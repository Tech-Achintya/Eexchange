package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.*;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.CollegeRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.utils.JWTutil;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.login.max-failures:5}")
    private int maxLoginFailures;

    @Value("${app.login.lockout-seconds:900}")
    private long loginLockoutSeconds;

    private static final String KEY_PENDING_SIGNUP = "pending:signup:";
    private static final String KEY_LOGIN_FAIL = "login:fail:";

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
                .emailVerifiedAt(pendingData.isCollegeVerified() ? Instant.now() : null)
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

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // 1. Check login failure lockout
        String failKey = KEY_LOGIN_FAIL + email;
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;
        if (failCount >= maxLoginFailures) {
            throw new AppException("Account temporarily locked due to repeated failed logins. Please try again in 15 minutes.", HttpStatus.TOO_MANY_REQUESTS);
        }

        // 2. Lookup user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    recordFailedLogin(email, failKey, failCount);
                    return new AppException("Invalid email or password", HttpStatus.UNAUTHORIZED);
                });

        // 3. Check account status
        if (user.getStatus() == UserStatus.PERM_BLOCKED) {
            throw new AppException("This account has been permanently suspended.", HttpStatus.FORBIDDEN);
        }
        if (user.getStatus() == UserStatus.TEMP_BLOCKED) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(Instant.now())) {
                throw new AppException("Account temporarily suspended until: " + user.getBlockedUntil(), HttpStatus.FORBIDDEN);
            }
        }

        // 4. Validate password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordFailedLogin(email, failKey, failCount);
            throw new AppException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        // 5. Successful login: clear lockout counter
        redisTemplate.delete(failKey);

        // 6. Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // 7. Update Redis status cache
        redisTemplate.opsForValue().set("user:status:" + user.getId(), user.getStatus().name());

        // 8. Generate JWT
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getCollege().getId());

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

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException("No account registered with this email", HttpStatus.NOT_FOUND));

        if (user.getStatus() == UserStatus.PERM_BLOCKED) {
            throw new AppException("This account has been permanently suspended.", HttpStatus.FORBIDDEN);
        }

        otpService.generateAndSendOtp(email, "reset");
    }

    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        String rawOtp = request.getOtp().trim();

        // 1. Verify OTP with purpose "reset"
        otpService.verifyOtp(email, rawOtp, "reset");

        // 2. Fetch user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        // 3. Hash and update password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 4. Clear any existing login lockout
        redisTemplate.delete(KEY_LOGIN_FAIL + email);
    }

    public void resendOtp(ResendOtpRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        String purpose = request.getPurpose().toLowerCase().trim();

        if ("signup".equals(purpose)) {
            String pendingKey = KEY_PENDING_SIGNUP + email;
            if (Boolean.FALSE.equals(redisTemplate.hasKey(pendingKey))) {
                throw new AppException("Signup session expired or not found. Please initiate signup again.", HttpStatus.BAD_REQUEST);
            }
        } else if ("reset".equals(purpose)) {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new AppException("No account registered with this email", HttpStatus.NOT_FOUND));
            if (user.getStatus() == UserStatus.PERM_BLOCKED) {
                throw new AppException("This account has been permanently suspended.", HttpStatus.FORBIDDEN);
            }
        }

        otpService.generateAndSendOtp(email, purpose);
    }

    private void recordFailedLogin(String email, String failKey, int currentFails) {
        redisTemplate.opsForValue().increment(failKey);
        if (currentFails == 0) {
            redisTemplate.expire(failKey, Duration.ofSeconds(loginLockoutSeconds));
        }
    }
}
