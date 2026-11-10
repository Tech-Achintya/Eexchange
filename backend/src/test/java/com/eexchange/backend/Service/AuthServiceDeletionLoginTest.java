package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Request.LoginRequest;
import com.eexchange.backend.DTO.Request.ResetPasswordRequest;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.CollegeRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Utils.JWTutil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceDeletionLoginTest {

    private static final String IP = "203.0.113.7";

    private UserRepository userRepository;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PasswordEncoder passwordEncoder;
    private JWTutil jwtUtil;
    private TokenRevocationService tokenRevocationService;
    private LoginLockoutService loginLockoutService;
    private AuthService authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        passwordEncoder = new BCryptPasswordEncoder(4);
        jwtUtil = mock(JWTutil.class);
        tokenRevocationService = mock(TokenRevocationService.class);
        loginLockoutService = mock(LoginLockoutService.class);

        authService = new AuthService(
                mock(OtpService.class),
                mock(CollegeDomainService.class),
                userRepository,
                mock(CollegeRepository.class),
                redisTemplate,
                mock(UserStatusCache.class),
                passwordEncoder,
                jwtUtil,
                mock(EmailService.class),
                tokenRevocationService,
                loginLockoutService);

        authService.initDummyPasswordHash();
    }

    @Test
    void loginWithTheFormerEmailFailsAfterAccountAnonymization() {
        when(userRepository.findByEmail("user@college.edu"))
                .thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setEmail("user@college.edu");
        request.setPassword("oldpass123");

        AppException ex = assertThrows(AppException.class,
                () -> authService.login(request, IP));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // A failed login records a per-IP failure, never a global account lock.
        verify(loginLockoutService).recordFailure(
                LoginLockoutService.SCOPE_USER, "user@college.edu", IP);
        verify(tokenRevocationService, never()).revokeAllTokens(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void correctPasswordWinsEvenWhenAnotherIpHasFailedRepeatedly() {
        User user = User.builder()
                .id(7L)
                .email("user@college.edu")
                .name("User")
                .passwordHash(passwordEncoder.encode("oldpass123"))
                .college(College.builder().id(3L).name("Test College").build())
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        when(userRepository.findByEmail("user@college.edu"))
                .thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("user@college.edu", 7L, 3L))
                .thenReturn("jwt-token");

        LoginRequest request = new LoginRequest();
        request.setEmail("user@college.edu");
        request.setPassword("oldpass123");

        var response = authService.login(request, "198.51.100.9");

        assertThat(response.getToken()).isEqualTo("jwt-token");
        // A correct password bypasses the lockout check entirely and clears
        // any failures recorded for this identifier.
        verify(loginLockoutService, never()).checkNotLocked(
                LoginLockoutService.SCOPE_USER, "user@college.edu", "198.51.100.9");
        verify(loginLockoutService)
                .clearFailures(LoginLockoutService.SCOPE_USER, "user@college.edu");
    }

    @Test
    void resetPasswordRotatesHashAndRevokesEveryExistingToken() {
        User user = User.builder()
                .id(7L)
                .email("user@college.edu")
                .name("User")
                .passwordHash(passwordEncoder.encode("oldpass123"))
                .college(College.builder().id(3L).name("Test College").build())
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        when(valueOperations.get("pwd_reset:token:reset-token"))
                .thenReturn("user@college.edu");
        when(userRepository.findByEmail("user@college.edu"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("reset-token");
        request.setNewPassword("newpass123");

        authService.resetPassword(request);

        assertThat(passwordEncoder.matches("newpass123", user.getPasswordHash())).isTrue();
        verify(tokenRevocationService).revokeAllTokens(7L);
        verify(loginLockoutService)
                .clearFailures(LoginLockoutService.SCOPE_USER, "user@college.edu");
        verify(redisTemplate).delete("pwd_reset:token:reset-token");
        verify(redisTemplate).delete("pwd_reset:active:7");
    }
}
