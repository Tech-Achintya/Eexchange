package com.eexchange.backend.Service.Moderation;

import org.springframework.stereotype.Component;

import java.util.Map;

// The model's code becomes a fixed, friendly sentence. Raw model text is never shown.
@Component
public class RejectionMessages {

    private static final Map<String, String> MESSAGES = Map.of(
            "PROHIBITED_ITEM",
            "This listing appears to offer a prohibited item and cannot be published.",
            "ADULT",
            "This listing contains adult content and cannot be published.",
            "SCAM",
            "This listing looks like a scam or asks for unsafe payment, so it cannot be published.",
            "SPAM",
            "This listing looks like spam or unrelated advertising and cannot be published.",
            "MISLEADING",
            "The photos do not appear to match the listing details.",
            "PERSONAL_INFO",
            "This listing exposes personal contact or identity information.",
            "OTHER",
            "This listing does not meet our campus marketplace rules."
    );

    public String forCode(String code) {
        if (code == null) {
            return MESSAGES.get("OTHER");
        }
        return MESSAGES.getOrDefault(code, MESSAGES.get("OTHER"));
    }
}