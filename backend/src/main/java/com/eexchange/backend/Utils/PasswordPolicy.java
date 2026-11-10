package com.eexchange.backend.Utils;

import com.eexchange.backend.Exception.AppException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * One password policy shared by signup, password change and admin bootstrap.
 * <p>
 * Length is measured in characters (what users perceive), while the upper
 * bound is measured in bytes because BCrypt silently truncates after 72 bytes.
 */
public final class PasswordPolicy {

    public static final int USER_MIN_LENGTH = 8;
    public static final int ADMIN_MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private static final List<String> COMMON_PASSWORDS = List.of(
            "password", "password1", "password123", "12345678", "123456789",
            "qwerty123", "letmein", "changeme", "admin", "admin123",
            "welcome1", "iloveyou");

    private PasswordPolicy() {
    }

    public static void validateUserPassword(String password) {
        validateLength(password, USER_MIN_LENGTH);
    }

    /**
     * Admin passwords must be longer and must not simply reuse the account
     * username or email address. The check is case-insensitive and exact: a
     * strong password may legitimately contain the account name.
     */
    public static void validateAdminPassword(String password, String... accountIdentifiers) {
        validateLength(password, ADMIN_MIN_LENGTH);

        String normalised = password.toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(normalised)) {
            throw new AppException(
                    "Admin password is too common. Choose a unique password.",
                    HttpStatus.BAD_REQUEST);
        }
        for (String identifier : accountIdentifiers) {
            if (identifier != null && normalised.equals(identifier.trim().toLowerCase(Locale.ROOT))) {
                throw new AppException(
                        "Admin password must not be the username or email address.",
                        HttpStatus.BAD_REQUEST);
            }
        }
    }

    public static boolean isStrongAdminPassword(String password, String... accountIdentifiers) {
        try {
            validateAdminPassword(password, accountIdentifiers);
            return true;
        } catch (AppException ex) {
            return false;
        }
    }

    private static void validateLength(String password, int minLength) {
        if (password == null) {
            throw new AppException("Password is required", HttpStatus.BAD_REQUEST);
        }
        int characters = password.codePointCount(0, password.length());
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (characters < minLength || bytes > MAX_BYTES) {
            throw new AppException(
                    "Password must be between " + minLength
                            + " and " + MAX_BYTES + " bytes",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
