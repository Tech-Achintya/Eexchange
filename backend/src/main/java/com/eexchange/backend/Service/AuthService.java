package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.*;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.CollegeRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Utils.JWTutil;
import com.fasterxml.jackson.core.JsonProcessingException;
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
    private final PasswordEncoder passwordEncoder;
    private final JWTutil jwtUtil;
    private final EmailService emailService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.login.max-failures:5}")
    private int maxLoginFailures;

    @Value("${app.login.lockout-seconds:900}")
    private long loginLockoutSeconds;

    @Value("${app.password-reset.ttl-seconds:900}")
    private long passwordResetTtlSeconds;

    @Value("${app.password-reset.cooldown-seconds:60}")
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
    private static final String KEY_LOGIN_FAIL = "login:fail:";

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

        // 2. Refuse to overwrite an in-flight pending signup session
        String pendingKey = KEY_PENDING_SIGNUP + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(pendingKey))) {
            throw new AppException("A verification code was already sent for this email. Please verify the OTP or use resend.", HttpStatus.CONFLICT);
        }

        // 3. Resolve college
        CollegeDomainService.CollegeMatchResult matchResult = collegeDomainService.resolveCollege(email);
        College college = matchResult.getCollege();

        // 4. Run the OTP step FIRST (enforces cooldown, hourly cap, and sends OTP)
        otpService.generateAndSendOtp(email, "signup");

        // 5. Only once OTP step succeeds, write pending signup data to Redis (TTL 10 min)
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        PendingSignupData pendingData = PendingSignupData.builder()
                .email(email)
                .name(request.getName().trim())
                .collegeId(college.getId())
                .isCollegeVerified(matchResult.isCollegeVerified())
                .passwordHash(hashedPassword)
                .build();

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

        // 2. Retrieve pending signup data
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

        // 3. Resolve College entity
        College college = collegeRepository.findById(pendingData.getCollegeId())
                .orElseThrow(() -> new AppException("Associated college not found", HttpStatus.INTERNAL_SERVER_ERROR));

        // 4. Persist User in PostgreSQL
        User user = User.builder()
                .email(pendingData.getEmail())
                .name(pendingData.getName())
                .passwordHash(pendingData.getPasswordHash())
                .college(college)
                .isCollegeVerified(pendingData.isCollegeVerified())
                .emailVerifiedAt(Instant.now())
                .status(UserStatus.ACTIVE)
                .lastLoginAt(Instant.now())
                .build();

        user = userRepository.save(user);

        // 5. Cleanup pending signup key
        redisTemplate.delete(pendingKey);

        // 6. Cache user status in Redis
        redisTemplate.opsForValue().set("user:status:" + user.getId(), user.getStatus().name());

        // 7. Generate JWT
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
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // 1. Check failure lockout
        String failKey = KEY_LOGIN_FAIL + email;
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;

        if (failCount >= maxLoginFailures) {
            long minutes = Math.max(1, loginLockoutSeconds / 60);
            throw new AppException(
                    "Account temporarily locked due to repeated failed logins. Please try again in "
                            + minutes + " minutes.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        // 2. Always run BCrypt, even for unknown emails
        User user = userRepository.findByEmail(email).orElse(null);
        String storedHash = (user != null && user.getPasswordHash() != null)
                ? user.getPasswordHash()
                : dummyPasswordHash;

        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), storedHash);

        // 3. Do not reveal block status unless the password is correct
        if (user == null || !passwordMatches) {
            recordFailedLogin(email, failKey, failCount);
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

        // 4. Successful login
        redisTemplate.delete(failKey);

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        redisTemplate.opsForValue().set(
                "user:status:" + user.getId(),
                user.getStatus().name()
        );

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

        // 1. Cooldown
        String cooldownKey = KEY_RESET_COOLDOWN + email;
        Long cooldown = redisTemplate.getExpire(cooldownKey);
        if (cooldown != null && cooldown > 0) {
            throw new AppException(
                    "Please wait " + cooldown + " seconds before requesting another reset link.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        // 2. Hourly cap
        String hourlyKey = KEY_RESET_HOURLY + email;
        String hourlyStr = redisTemplate.opsForValue().get(hourlyKey);
        int hourlyCount = hourlyStr != null ? Integer.parseInt(hourlyStr) : 0;

        if (hourlyCount >= passwordResetHourlyMax) {
            throw new AppException(
                    "Hourly password reset limit reached. Try again later.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        redisTemplate.opsForValue().set(
                cooldownKey,
                "1",
                Duration.ofSeconds(passwordResetCooldownSeconds)
        );
        redisTemplate.opsForValue().increment(hourlyKey);
        if (hourlyCount == 0) {
            redisTemplate.expire(hourlyKey, Duration.ofHours(1));
        }

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
        redisTemplate.delete(redisKey);
        redisTemplate.delete(KEY_RESET_ACTIVE + user.getId());
        redisTemplate.delete(KEY_LOGIN_FAIL + email);
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

    private void recordFailedLogin(String email, String failKey, int currentFails) {
        redisTemplate.opsForValue().increment(failKey);
        if (currentFails == 0) {
            redisTemplate.expire(failKey, Duration.ofSeconds(loginLockoutSeconds));
        }
    }
}
