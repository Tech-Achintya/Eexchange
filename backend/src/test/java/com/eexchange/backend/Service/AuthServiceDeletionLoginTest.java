package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.LoginRequest;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceDeletionLoginTest {

    private UserRepository userRepository;
    private ValueOperations<String, String> valueOperations;
    private AuthService authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

        authService = new AuthService(
                mock(OtpService.class),
                mock(CollegeDomainService.class),
                userRepository,
                mock(CollegeRepository.class),
                redisTemplate,
                passwordEncoder,
                mock(JWTutil.class),
                mock(EmailService.class));

        // AuthService fields are normally filled by @Value.
        ReflectionTestUtils.setField(authService, "maxLoginFailures", 5);
        ReflectionTestUtils.setField(authService, "loginLockoutSeconds", 900L);
        authService.initDummyPasswordHash();
    }

    @Test
    void loginWithTheFormerEmailFailsAfterAccountAnonymization() {
        when(userRepository.findByEmail("user@college.edu"))
                .thenReturn(Optional.empty());
        when(valueOperations.increment("login:fail:user@college.edu"))
                .thenReturn(1L);

        LoginRequest request = new LoginRequest();
        request.setEmail("user@college.edu");
        request.setPassword("oldpass123");

        AppException ex = assertThrows(AppException.class,
                () -> authService.login(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}