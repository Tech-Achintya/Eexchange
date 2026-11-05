package com.eexchange.backend.Service.Moderation;

import java.util.Optional;
import java.util.Set;

public final class ModerationVerdictParser {

    public static final Set<String> CODES = Set.of(
            "PROHIBITED_ITEM", "ADULT", "SCAM", "SPAM",
            "MISLEADING", "PERSONAL_INFO", "OTHER");

    private static final String REJECT_PREFIX = "REJECT:";

    private ModerationVerdictParser() {
    }

    public static Optional<Verdict> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }

        // Tolerate whitespace, surrounding quotes and one trailing period.
        String value = stripTrailingPeriods(raw.trim());
        value = stripQuotes(value);
        value = stripTrailingPeriods(value);

        if (value.equals("APPROVE")) {
            return Optional.of(new Verdict(true, null));
        }

        if (value.startsWith(REJECT_PREFIX)) {
            String code = value.substring(REJECT_PREFIX.length()).trim();
            if (CODES.contains(code)) {
                return Optional.of(new Verdict(false, code));
            }
        }

        // Anything else, including extra prose, is malformed.
        return Optional.empty();
    }

    private static String stripTrailingPeriods(String value) {
        String result = value;
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private static String stripQuotes(String value) {
        String result = value;
        boolean changed = true;

        while (changed && result.length() >= 2) {
            changed = false;
            char first = result.charAt(0);
            char last = result.charAt(result.length() - 1);

            if ((first == '"' && last == '"')
                    || (first == '\'' && last == '\'')
                    || (first == '`' && last == '`')) {
                result = result.substring(1, result.length() - 1).trim();
                changed = true;
            }
        }
        return result;
    }

    public record Verdict(boolean approved, String code) {
    }
}