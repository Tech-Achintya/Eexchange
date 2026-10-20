package com.eexchange.backend.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    @Value("${BREVO_API_KEY}")
    private String brevoApiKey;

    @Value("${BREVO_SENDER_EMAIL:achintya11022006@gmail.com}")
    private String senderEmail;

    @Value("${BREVO_SENDER_NAME:EExchange}")
    private String senderName;

    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    public void sendOtpEmail(String toEmail, String otpCode, String purpose) {
        // Log OTP in console for dev mode
        log.info("🔑 [DEV MODE] OTP for {} ({}) is: {}", toEmail, purpose, otpCode);

        // Sanitize API key (strip stray quotes/spaces)
        String cleanApiKey = (brevoApiKey != null) ? brevoApiKey.trim().replace("\"", "").replace("'", "") : null;

        if (cleanApiKey == null || cleanApiKey.isBlank()) {
            log.warn("⚠️ BREVO_API_KEY is not set or empty. OTP logged to console only.");
            return;
        }

        String url = "https://api.brevo.com/v3/smtp/email";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", cleanApiKey);

        Map<String, Object> body = new HashMap<>();

        Map<String, String> sender = new HashMap<>();
        sender.put("name", senderName);
        sender.put("email", senderEmail);
        body.put("sender", sender);

        List<Map<String, String>> to = new ArrayList<>();
        Map<String, String> recipient = new HashMap<>();
        recipient.put("email", toEmail);
        to.add(recipient);
        body.put("to", to);

        String title = "reset".equalsIgnoreCase(purpose) ? "Password Reset Verification" : "Account Signup Verification";
        body.put("subject", "Your Eexchange " + title + ": " + otpCode);
        body.put("htmlContent", "<div style='font-family: Arial, sans-serif; padding: 20px; color: #333;'>"
                + "<h2 style='color: #4F46E5;'>Eexchange " + title + "</h2>"
                + "<p>Use the following 6-digit OTP to complete your request:</p>"
                + "<h1 style='font-size: 32px; letter-spacing: 5px; color: #111827;'>" + otpCode + "</h1>"
                + "<p style='color: #6B7280;'>This code is valid for <strong>10 minutes</strong>. Do not share it with anyone.</p>"
                + "</div>");

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, request, String.class);
            log.info("✅ Brevo email dispatched to {}", toEmail);
        } catch (HttpStatusCodeException e) {
            // 🔍 THIS WILL SHOW BREVO'S EXACT JSON ERROR REASON
            log.error("❌ Failed to send email via Brevo. HTTP Status: {} - Response Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("❌ Failed to send email via Brevo: {}", e.getMessage(), e);
        }
    }
    @Async
    public void sendPasswordResetLinkEmail(String toEmail, String resetLink) {
        log.info("🔗 [DEV MODE] Password Reset Link for {}: {}", toEmail, resetLink);

        String cleanApiKey = (brevoApiKey != null) ? brevoApiKey.trim().replace("\"", "").replace("'", "") : null;
        if (cleanApiKey == null || cleanApiKey.isBlank()) {
            log.warn("⚠️ Brevo API key is not set. Reset link logged to console only.");
            return;
        }

        String url = "https://api.brevo.com/v3/smtp/email";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("api-key", cleanApiKey);

        Map<String, Object> body = new HashMap<>();

        Map<String, String> sender = new HashMap<>();
        sender.put("name", senderName);
        sender.put("email", senderEmail);
        body.put("sender", sender);

        List<Map<String, String>> to = new ArrayList<>();
        Map<String, String> recipient = new HashMap<>();
        recipient.put("email", toEmail);
        to.add(recipient);
        body.put("to", to);

        body.put("subject", "Reset Your Eexchange Password");
        body.put("htmlContent", "<div style='font-family: Arial, sans-serif; padding: 24px; color: #1f2937; max-width: 600px; margin: auto; border: 1px solid #e5e7eb; border-radius: 8px;'>"
                + "<h2 style='color: #4F46E5; margin-bottom: 8px;'>Password Reset Request</h2>"
                + "<p>We received a request to reset your Eexchange account password.</p>"
                + "<p>Click the button below to set a new password:</p>"
                + "<div style='margin: 24px 0;'>"
                + "<a href='" + resetLink + "' style='background-color: #4F46E5; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;'>Reset Password</a>"
                + "</div>"
                + "<p style='color: #ef4444; font-size: 14px;'><strong>⚠️ Important:</strong> This link is valid for <strong>2 minutes only</strong> and can only be used once.</p>"
                + "<p style='font-size: 12px; color: #6b7280; word-break: break-all;'>If the button doesn't work, copy and paste this link into your browser:<br/>" + resetLink + "</p>"
                + "</div>");

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, request, String.class);
            log.info("✅ Password reset email dispatched to {}", toEmail);
        } catch (HttpStatusCodeException e) {
            log.error("❌ Failed to send reset email. Status: {} - Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("❌ Failed to send reset email: {}", e.getMessage(), e);
        }
    }

}
