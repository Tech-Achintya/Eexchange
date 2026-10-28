package com.eexchange.backend.Utils;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DisposableEmailChecker {

    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
            "mailinator.com", "tempmail.com", "10minutemail.com",
            "guerrillamail.com", "throwawaymail.com", "yopmail.com",
            "trashmail.com", "sharklasers.com", "getairmail.com",
            "dispostable.com", "fakeinbox.com", "temp-mail.org"
    );

    public boolean isDisposable(String domain) {
        if (domain == null) return false;
        return DISPOSABLE_DOMAINS.contains(domain.toLowerCase().trim());
    }
}
