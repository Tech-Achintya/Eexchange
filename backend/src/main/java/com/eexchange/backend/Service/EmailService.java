package com.eexchange.backend.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    private final RestTemplate restTemplate;
    private final Environment environment;
    private final String brevoApiKey;
    private final String senderEmail;
    private final String senderName;
    private final long otpTtlSeconds;
    private final long passwordResetTtlSeconds;

    public EmailService(RestTemplateBuilder restTemplateBuilder,
                        Environment environment,
                        @Value("${BREVO_API_KEY}") String brevoApiKey,
                        @Value("${BREVO_SENDER_EMAIL}") String senderEmail,
                        @Value("${BREVO_SENDER_NAME:EExchange}") String senderName,
                        @Value("${app.otp.ttl-seconds:600}") long otpTtlSeconds,
                        @Value("${app.password-reset.ttl-seconds:900}") long passwordResetTtlSeconds) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restTemplate = new RestTemplate(requestFactory);
        this.environment = environment;
        this.brevoApiKey = brevoApiKey;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
        this.otpTtlSeconds = otpTtlSeconds;
        this.passwordResetTtlSeconds = passwordResetTtlSeconds;
    }



    @Async("emailTaskExecutor")
    public void sendOtpEmail(String toEmail, String otpCode, String purpose, Runnable onFailure) {
        if (isDevProfile()) {
            log.info("[dev] OTP for {} ({}) is: {}", toEmail, purpose, otpCode);
        }

        String title = "reset".equalsIgnoreCase(purpose)
                ? "Password Reset Verification"
                : "Account Signup Verification";

        String html = "<div style='font-family: Arial, sans-serif; padding: 20px; color: #333;'>"
                + "<h2 style='color: #4F46E5;'>Eexchange " + title + "</h2>"
                + "<p>Use the following 6-digit OTP to complete your request:</p>"
                + "<h1 style='font-size: 32px; letter-spacing: 5px; color: #111827;'>" + otpCode + "</h1>"
                + "<p style='color: #6B7280;'>This code is valid for <strong>"
                + ttlMinutes(otpTtlSeconds)
                + " minutes</strong>. Do not share it with anyone.</p>"
                + "</div>";

        boolean sent = sendEmail(toEmail, "Your Eexchange " + title, html);
        if (!sent && onFailure != null) {
            onFailure.run();
        }
    }

    @Async("emailTaskExecutor")
    public void sendPasswordResetLinkEmail(String toEmail, String resetLink, Runnable onFailure) {
        if (isDevProfile()) {
            log.info("[dev] Password reset link for {}: {}", toEmail, resetLink);
        }

        String html = "<div style='font-family: Arial, sans-serif; padding: 24px; color: #1f2937; max-width: 600px; margin: auto; border: 1px solid #e5e7eb; border-radius: 8px;'>"
                + "<h2 style='color: #4F46E5; margin-bottom: 8px;'>Password Reset Request</h2>"
                + "<p>We received a request to reset your Eexchange account password.</p>"
                + "<p>Click the button below to set a new password:</p>"
                + "<div style='margin: 24px 0;'>"
                + "<a href='" + resetLink + "' style='background-color: #4F46E5; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;'>Reset Password</a>"
                + "</div>"
                + "<p style='color: #ef4444; font-size: 14px;'><strong>Important:</strong> This link is valid for <strong>"
                + ttlMinutes(passwordResetTtlSeconds)
                + " minutes</strong> and can only be used once.</p>"
                + "<p style='font-size: 12px; color: #6b7280; word-break: break-all;'>If the button doesn't work, copy and paste this link into your browser:<br/>"
                + resetLink + "</p>"
                + "</div>";

        boolean sent = sendEmail(toEmail, "Reset Your Eexchange Password", html);
        if (!sent && onFailure != null) {
            onFailure.run();
        }
    }

    private boolean sendEmail(String toEmail, String subject, String htmlContent) {
        String cleanApiKey = brevoApiKey == null
                ? ""
                : brevoApiKey.trim().replace("\"", "").replace("'", "");

        if (cleanApiKey.isBlank()) {
            log.error("BREVO_API_KEY is empty; email to {} was not sent", maskEmail(toEmail));
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", cleanApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("sender", Map.of("name", senderName, "email", senderEmail));
        body.put("to", List.of(Map.of("email", toEmail)));
        body.put("subject", subject);
        body.put("htmlContent", htmlContent);

        try {
            restTemplate.postForEntity(
                    "https://api.brevo.com/v3/smtp/email",
                    new HttpEntity<>(body, headers),
                    String.class
            );
            log.info("Brevo email dispatched to {}", maskEmail(toEmail));
            return true;
        } catch (HttpStatusCodeException e) {
            log.error("Brevo rejected email to {}. HTTP status: {}",
                    maskEmail(toEmail), e.getStatusCode());
            return false;
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}",
                    maskEmail(toEmail), e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean isDevProfile() {
        return environment.matchesProfiles("dev","local");
    }

    private long ttlMinutes(long seconds) {
        return Math.max(1, seconds / 60);
    }

    private String maskEmail(String email) {
        if (email == null) {
            return "unknown";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}