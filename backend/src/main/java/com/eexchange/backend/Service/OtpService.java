package com.eexchange.backend.Service;

import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.utils.OtpHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.ttl-seconds:600}")
    private long otpTtlSeconds;

    @Value("${app.otp.cooldown-seconds:60}")
    private long cooldownSeconds;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.hourly-max:5}")
    private int hourlyMax;

    public void generateAndSendOtp(String email, String purpose) {
        String normalizedEmail = email.toLowerCase().trim();
        String normalizedPurpose = purpose.toLowerCase().trim();

        // 1. Check hourly rate limit per purpose
        String hourlyKey = "otp:hourly:" + normalizedPurpose + ":" + normalizedEmail;
        String hourlyCountStr = redisTemplate.opsForValue().get(hourlyKey);
        int hourlyCount = hourlyCountStr != null ? Integer.parseInt(hourlyCountStr) : 0;
        if (hourlyCount >= hourlyMax) {
            throw new AppException("Hourly OTP limit reached (max " + hourlyMax + " requests/hour). Try again later.", HttpStatus.TOO_MANY_REQUESTS);
        }

        // 2. Check 60-second cooldown per purpose
        String cooldownKey = "otp:cooldown:" + normalizedPurpose + ":" + normalizedEmail;
        Long remainingCooldown = redisTemplate.getExpire(cooldownKey);
        if (remainingCooldown != null && remainingCooldown > 0) {
            throw new AppException("Please wait " + remainingCooldown + " seconds before requesting another OTP.", HttpStatus.TOO_MANY_REQUESTS);
        }

        // 3. Generate 6-digit OTP
        int rawOtp = 100000 + secureRandom.nextInt(900000);
        String otpString = String.valueOf(rawOtp);

        // 4. Store hashed OTP in Redis with 10-minute TTL: otp:{purpose}:{email}
        String otpKey = "otp:" + normalizedPurpose + ":" + normalizedEmail;
        String hashedOtp = OtpHashUtil.hash(otpString);
        redisTemplate.opsForValue().set(otpKey, hashedOtp, Duration.ofSeconds(otpTtlSeconds));

        // 5. Set 60-second cooldown
        redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(cooldownSeconds));

        // 6. Increment hourly counter (expires in 1 hour if new)
        redisTemplate.opsForValue().increment(hourlyKey);
        if (hourlyCount == 0) {
            redisTemplate.expire(hourlyKey, Duration.ofHours(1));
        }

        // 7. Reset attempts counter
        redisTemplate.delete("otp:attempts:" + normalizedPurpose + ":" + normalizedEmail);

        // 8. Send email
        emailService.sendOtpEmail(normalizedEmail, otpString, normalizedPurpose,() -> redisTemplate.delete(cooldownKey));
    }

    public void verifyOtp(String email, String rawOtp, String purpose) {
        String normalizedEmail = email.toLowerCase().trim();
        String normalizedPurpose = purpose.toLowerCase().trim();

        String otpKey = "otp:" + normalizedPurpose + ":" + normalizedEmail;
        String attemptsKey = "otp:attempts:" + normalizedPurpose + ":" + normalizedEmail;

        String storedHash = redisTemplate.opsForValue().get(otpKey);
        if (storedHash == null) {
            throw new AppException("OTP has expired or was not requested.", HttpStatus.BAD_REQUEST);
        }

        // Increment attempts
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts > maxAttempts) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptsKey);
            throw new AppException("Too many invalid attempts. Your OTP has been invalidated.", HttpStatus.BAD_REQUEST);
        }

        if (!OtpHashUtil.matches(rawOtp, storedHash)) {
            int remaining = (int) (maxAttempts - (attempts != null ? attempts : 1));
            throw new AppException("Invalid OTP. Attempts remaining: " + Math.max(0, remaining), HttpStatus.BAD_REQUEST);
        }

        // On success: delete OTP keys
        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptsKey);
    }
}
