package com.eexchange.backend.Utils;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JWTutilTest {

    private static final String USER_SECRET =
            "test-user-secret-test-user-secret-test-user-secret";
    private static final String ADMIN_SECRET =
            "test-admin-secret-test-admin-secret-test-admin-secret";

    private JWTutil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JWTutil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", USER_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "adminSecretKey", ADMIN_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiryMs", 60_000L);
        ReflectionTestUtils.setField(jwtUtil, "adminExpiryMs", 60_000L);
    }

    @Test
    void userTokenIsRejectedAsAdminToken() {
        String userToken = jwtUtil.generateToken("user@college.edu", 1L, 10L);

        assertThatThrownBy(() -> jwtUtil.extractAdminClaims(userToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void adminTokenIsRejectedAsUserToken() {
        String adminToken = jwtUtil.generateAdminToken("admin", 99L, "ROLE_MODERATOR");

        assertThatThrownBy(() -> jwtUtil.extractAllClaims(adminToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(jwtUtil, "expiryMs", -1_000L);
        String expiredToken = jwtUtil.generateToken("user@college.edu", 1L, 10L);

        assertThatThrownBy(() -> jwtUtil.extractAllClaims(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtUtil.generateToken("user@college.edu", 1L, 10L);

        String[] parts = token.split("\\.");
        char firstSignatureChar = parts[2].charAt(0);
        char replacement = firstSignatureChar == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "."
                + replacement + parts[2].substring(1);

        assertThatThrownBy(() -> jwtUtil.extractAllClaims(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void userTokenRoundTripsUserClaims() {
        String token = jwtUtil.generateToken("user@college.edu", 7L, 3L);

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("user@college.edu");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(7L);
        assertThat(jwtUtil.extractCollegeId(token)).isEqualTo(3L);
    }

    @Test
    void adminTokenRoundTripsAdminIdAndRole() {
        String token = jwtUtil.generateAdminToken("admin", 42L, "ROLE_SUPER_ADMIN");

        assertThat(jwtUtil.extractAdminId(token)).isEqualTo(42L);
        assertThat(jwtUtil.extractRole(token, true)).isEqualTo("ROLE_SUPER_ADMIN");
    }
}
