package com.eexchange.backend.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendOtpEmail(String toEmail, String otpCode) {
        // Log to console for debugging
        System.out.println("LOGGING OTP for " + toEmail + " -> " + otpCode);

        if (brevoApiKey == null || brevoApiKey.isEmpty()) {
            System.out.println("WARNING: BREVO_API_KEY is not set. Check your Environment Variables.");
            return;
        }

        String url = "https://api.brevo.com/v3/smtp/email";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey);

        // Brevo JSON structure
        Map<String, Object> body = new HashMap<>();
        
        Map<String, String> sender = new HashMap<>();
        sender.put("name", "EExchange");
        sender.put("email", "achintya11022006@gmail.com"); // Your registered Brevo email
        body.put("sender", sender);

        List<Map<String, String>> to = new ArrayList<>();
        Map<String, String> recipient = new HashMap<>();
        recipient.put("email", toEmail);
        to.add(recipient);
        body.put("to", to);

        body.put("subject", "Your OTP for EExchange Registration");
        body.put("htmlContent", "<html><body><strong>Your OTP for registration is: " + otpCode + "</strong><p>This OTP is valid for 5 minutes.</p></body></html>");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(url, request, String.class);
            System.out.println("OTP Email sent successfully via Brevo to " + toEmail);
        } catch (Exception e) {
            System.err.println("Error sending email via Brevo: " + e.getMessage());
            // We don't throw an error so the user can still use the console log workaround
        }
    }
}
