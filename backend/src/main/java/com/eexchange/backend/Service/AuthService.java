package com.eexchange.backend.Service;

import com.eexchange.backend.Configuration.RedisLuaScripts;
import com.eexchange.backend.DTO.*;
import com.eexchange.backend.DTO.Request.*;
import com.eexchange.backend.DTO.Response.AuthResponse;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.CollegeRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Utils.JWTutil;
import com.eexchange.backend.Utils.PasswordPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final OtpService otpService;
    private final CollegeDomainService collegeDomainService;
    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final StringRedisTemplate redisTemplate;
    private final UserStatusCache userStatusCache;
    private final PasswordEncoder passwordEncoder;
    private final JWTutil jwtUtil;
    private final EmailService emailService;
    private final TokenRevocationService tokenRevocationService;
    private final LoginLockoutService loginLockoutService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            // Pending records written by an older deployment may contain fields
            // that no longer exist (notably passwordHash). Ignore them instead
            // of failing verification until the 15-minute TTL expires.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${app.password-reset.ttl-seconds:900}")
    private long passwordResetTtlSeconds;

    @Value("${app.password-reset.cooldown-seconds:30}")
    private long passwordResetCooldownSeconds;

    @Value("${app.password-reset.hourly-max:5}")
    private int passwordResetHourlyMax;

    private String dummyPasswordHash;

    @PostConstruct
    void initDummyPasswordHash() {
        this.dummyPasswordHash = passwordEncoder.encode(
                "dummy-password-for-timing-" + UUID.randomUUID()
        );
    }

    @Value("${app.password-reset.base-url:http://localhost:5173/reset-password}")
    private String passwordResetBaseUrl;

    private static final String KEY_RESET_TOKEN = "pwd_reset:token:";
    private static final String KEY_RESET_ACTIVE = "pwd_reset:active:";
    private static final String KEY_RESET_COOLDOWN = "pwd_reset:cooldown:";
    private static final String KEY_RESET_HOURLY = "pwd_reset:hourly:";
    private static final String KEY_PENDING_SIGNUP = "pending:signup:";

    public boolean validateResetToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey("pwd_reset:token:" + token.trim()));
    }

    public CollegeDomainService.CollegeMatchResult signup(SignupRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // 1. Check if user already exists
        if (userRepository.existsByEmail(email)) {
            throw new AppException("An account with this email already exists", HttpStatus.CONFLICT);
        }

        // 2. Resolve college
        CollegeDomainService.CollegeMatchResult matchResult = collegeDomainService.resolveCollege(email);
        College college = matchResult.getCollege();

        // 3. Run the OTP step FIRST (enforces cooldown, hourly cap, and sends OTP).
        //    A previous pending session is deliberately overwritten: pending data
        //    contains no credential, so restarting signup can never let one
        //    requester choose the password for another. The OTP cooldown still
        //    rate-limits overwrites.
        otpService.generateAndSendOtp(email, "signup");

        // 4. Only once OTP step succeeds, write pending signup data to Redis (TTL 10 min)
        PendingSignupData pendingData = PendingSignupData.builder()
                .email(email)
                .collegeId(college.getId())
                .isCollegeVerified(matchResult.isCollegeVerified())
                .build();

        String pendingKey = KEY_PENDING_SIGNUP + email;
        try {
            String json = objectMapper.writeValueAsString(pendingData);
            redisTemplate.opsForValue().set(pendingKey, json, Duration.ofMinutes(10));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize pending signup data for {}: {}", email, e.getMessage());
            throw new AppException("Failed to process signup request", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return matchResult;
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        String rawOtp = request.getOtp().trim();

        // 1. Verify OTP from Redis (purpose: signup)
        otpService.verifyOtp(email, rawOtp, "signup");

        // 2. Retrieve pending signup data. Only the email, college and
        //    verification flag are stored there; the name and password are taken
        //    from this request, which is submitted by whoever successfully proved
        //    control of the mailbox.
        String pendingKey = KEY_PENDING_SIGNUP + email;
        String pendingJson = redisTemplate.opsForValue().get(pendingKey);
        if (pendingJson == null) {
            throw new AppException("Signup session has expired. Please initiate signup again.", HttpStatus.BAD_REQUEST);
        }

        PendingSignupData pendingData;
        try {
            pendingData = objectMapper.readValue(pendingJson, PendingSignupData.class);
        } catch (JsonProcessingException e) {
            throw new AppException("Invalid signup session data", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // 3. Validate the credential supplied at verification time.
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new AppException("Name must be between 2 and 100 characters", HttpStatus.BAD_REQUEST);
        }
        PasswordPolicy.validateUserPassword(request.getPassword());

        // 4. Resolve College entity
        College college = collegeRepository.findById(pendingData.getCollegeId())
                .orElseThrow(() -> new AppException("Associated college not found", HttpStatus.INTERNAL_SERVER_ERROR));

        // 5. Persist User in PostgreSQL
        User user = User.builder()
                .email(pendingData.getEmail())
                .name(name)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .college(college)
                .isCollegeVerified(pendingData.isCollegeVerified())
                .emailVerifiedAt(Instant.now())
                .status(UserStatus.ACTIVE)
                .lastLoginAt(Instant.now())
                .build();

        user = userRepository.save(user);

        // 6. Cleanup pending signup key
        redisTemplate.delete(pendingKey);

        // 7. Cache user status consistently with standard TTL (1 hour)
        userStatusCache.put(user.getId(), user.getStatus());

        // 8. Generate JWT
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), college.getId());

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .collegeId(college.getId())
                .collegeName(college.getName())
                .isCollegeVerified(user.getIsCollegeVerified())
                .isNewUser(true)
                .token(token)
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = request.getEmail().toLowerCase().trim();

        // 1. Always run BCrypt, even for unknown emails. The password is checked
        //    before the lockout so a legitimate owner is never locked out by
        //    someone else's failed attempts from another address.
        User user = userRepository.findByEmail(email).orElse(null);
        String storedHash = (user != null && user.getPasswordHash() != null)
                ? user.getPasswordHash()
                : dummyPasswordHash;

        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), storedHash);

        // 2. Do not reveal block status unless the password is correct
        if (user == null || !passwordMatches) {
            loginLockoutService.recordFailure(LoginLockoutService.SCOPE_USER, email, clientIp);
            // Raises 429 once this identifier+IP pair has exhausted its attempts.
            loginLockoutService.checkNotLocked(LoginLockoutService.SCOPE_USER, email, clientIp);
            throw new AppException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }
        if (user.getDeletedAt() != null) {
            throw new AppException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }
        if (user.getStatus() == UserStatus.PERM_BLOCKED) {
            throw new AppException("This account has been permanently suspended.", HttpStatus.FORBIDDEN);
        }

        if (user.getStatus() == UserStatus.TEMP_BLOCKED
                && user.getBlockedUntil() != null
                && user.getBlockedUntil().isAfter(Instant.now())) {
            throw new AppException(
                    "Account temporarily suspended until: " + user.getBlockedUntil(),
                    HttpStatus.FORBIDDEN
            );
        }

        // 3. Successful login
        loginLockoutService.clearFailures(LoginLockoutService.SCOPE_USER, email);

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Cache user status consistently with standard TTL (1 hour)
        userStatusCache.put(user.getId(), user.getStatus());

        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getId(),
                user.getCollege().getId()
        );

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .collegeId(user.getCollege().getId())
                .collegeName(user.getCollege().getName())
                .isCollegeVerified(user.getIsCollegeVerified())
                .isNewUser(false)
                .token(token)
                .build();
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // 1. Enforce the 30-second cooldown atomically via SET NX EX
        String cooldownKey = KEY_RESET_COOLDOWN + email;
        Boolean cooldownSet = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", Duration.ofSeconds(passwordResetCooldownSeconds));
        if (!Boolean.TRUE.equals(cooldownSet)) {
            Long cooldown = redisTemplate.getExpire(cooldownKey);
            long waitSeconds = (cooldown != null && cooldown > 0) ? cooldown : passwordResetCooldownSeconds;
            throw new AppException(
                    "Please wait " + waitSeconds + " seconds before requesting another reset link.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        // 2. Check hourly cap
        String hourlyKey = KEY_RESET_HOURLY + email;
        String hourlyStr = redisTemplate.opsForValue().get(hourlyKey);
        int hourlyCount = hourlyStr != null ? Integer.parseInt(hourlyStr) : 0;

        if (hourlyCount >= passwordResetHourlyMax) {
            throw new AppException(
                    "Hourly password reset limit reached. Try again later.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        // Increment hourly counter atomically with 1-hour TTL
        redisTemplate.execute(
                RedisLuaScripts.INCR_WITH_TTL,
                List.of(hourlyKey),
                String.valueOf(Duration.ofHours(1).toSeconds())
        );

        // 3. Generic behaviour for unknown / blocked accounts
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        // 4. Revoke the previous active token
        String activeKey = KEY_RESET_ACTIVE + user.getId();
        String previousToken = redisTemplate.opsForValue().get(activeKey);
        if (previousToken != null) {
            redisTemplate.delete(KEY_RESET_TOKEN + previousToken);
        }

        // 5. Issue exactly one active token
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String tokenKey = KEY_RESET_TOKEN + token;

        redisTemplate.opsForValue().set(
                tokenKey,
                email,
                Duration.ofSeconds(passwordResetTtlSeconds)
        );
        redisTemplate.opsForValue().set(
                activeKey,
                token,
                Duration.ofSeconds(passwordResetTtlSeconds)
        );

        String resetLink = passwordResetBaseUrl + "?token=" + token;

        emailService.sendPasswordResetLinkEmail(email, resetLink, () -> {
            redisTemplate.delete(tokenKey);
            redisTemplate.delete(activeKey);
            redisTemplate.delete(cooldownKey);
        });
    }

    public void resetPassword(ResetPasswordRequest request) {
        String token = request.getToken().trim();
        String redisKey = KEY_RESET_TOKEN + token;

        String email = redisTemplate.opsForValue().get(redisKey);
        if (email == null) {
            throw new AppException(
                    "This password reset link has expired or is invalid. Please request a new one.",
                    HttpStatus.BAD_REQUEST
            );
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            redisTemplate.delete(redisKey);
            throw new AppException(
                    "This password reset link has expired or is invalid. Please request a new one.",
                    HttpStatus.BAD_REQUEST
            );
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // A reset must invalidate every JWT issued before this moment. Without
        // this, a stolen token survives the "compromised account" recovery.
        tokenRevocationService.revokeAllTokens(user.getId());

        redisTemplate.delete(redisKey);
        redisTemplate.delete(KEY_RESET_ACTIVE + user.getId());
        loginLockoutService.clearFailures(LoginLockoutService.SCOPE_USER, email);
    }

    public void resendOtp(ResendOtpRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        String purpose = request.getPurpose() == null
                ? "signup"
                : request.getPurpose().toLowerCase().trim();

        if (!"signup".equals(purpose)) {
            throw new AppException(
                    "Only signup OTP resend is supported",
                    HttpStatus.BAD_REQUEST
            );
        }

        String pendingKey = KEY_PENDING_SIGNUP + email;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(pendingKey))) {
            throw new AppException(
                    "Signup session expired or not found. Please initiate signup again.",
                    HttpStatus.BAD_REQUEST
            );
        }

        otpService.generateAndSendOtp(email, "signup");
    }
}
