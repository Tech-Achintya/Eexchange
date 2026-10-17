package com.eexchange.backend.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;

    @Value("${BREVO_SENDER_EMAIL:achintya11022006@gmail.com}")
    private String senderEmail;

    @Value("${BREVO_SENDER_NAME:EExchange}")
    private String senderName;

    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    public void sendOtpEmail(String toEmail, String otpCode, String purpose) {
        // Always log OTP in dev mode for easy local verification
        log.info("🔑 [DEV MODE] OTP for {} ({}) is: {}", toEmail, purpose, otpCode);

        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.warn("⚠️ BREVO_API_KEY is not set. OTP logged to console only.");
            return;
        }

        String url = "https://api.brevo.com/v3/smtp/email";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey);

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
        } catch (Exception e) {
            log.error("❌ Failed to send email via Brevo: {}", e.getMessage());
        }
    }
}
