package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Request.SignupRequest;
import com.eexchange.backend.DTO.Request.VerifyOtpRequest;
import com.eexchange.backend.DTO.Response.AuthResponse;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Repository.CollegeRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Utils.JWTutil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceSignupTest {

    private UserRepository userRepository;
    private CollegeRepository collegeRepository;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private OtpService otpService;
    private CollegeDomainService collegeDomainService;
    private PasswordEncoder passwordEncoder;
    private JWTutil jwtUtil;
    private AuthService authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);
        collegeRepository = mock(CollegeRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        otpService = mock(OtpService.class);
        collegeDomainService = mock(CollegeDomainService.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        jwtUtil = mock(JWTutil.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        authService = new AuthService(
                otpService,
                collegeDomainService,
                userRepository,
                collegeRepository,
                redisTemplate,
                mock(UserStatusCache.class),
                passwordEncoder,
                jwtUtil,
                mock(EmailService.class),
                mock(TokenRevocationService.class),
                mock(LoginLockoutService.class));
        authService.initDummyPasswordHash();
    }

    @Test
    void signupStoresNoPasswordAndOverwritesAnyExistingPendingSession() {
        College college = College.builder().id(3L).name("Test College").build();
        when(collegeDomainService.resolveCollege("victim@college.edu"))
                .thenReturn(new CollegeDomainService.CollegeMatchResult(college, true));

        SignupRequest request = new SignupRequest();
        request.setEmail("victim@college.edu");
        request.setName("Victim");

        authService.signup(request);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq("pending:signup:victim@college.edu"),
                json.capture(),
                eq(Duration.ofMinutes(10)));

        // A previous pending session must not block a restart, and the stored
        // payload must not contain any credential.
        verify(redisTemplate, never()).hasKey("pending:signup:victim@college.edu");
        assertThat(json.getValue()).contains("victim@college.edu");
        assertThat(json.getValue().toLowerCase()).doesNotContain("password");
    }

    @Test
    void verifyOtpCreatesTheAccountWithThePasswordSubmittedAtVerification() {
        College college = College.builder().id(3L).name("Test College").build();

        // A stale/attacker-written pending record: its name and password hash
        // must be ignored in favour of the verified request.
        when(valueOperations.get("pending:signup:victim@college.edu")).thenReturn("""
                {"email":"victim@college.edu","name":"Attacker",
                 "collegeId":3,"collegeVerified":true,
                 "passwordHash":"attacker-hash"}
                """);
        when(collegeRepository.findById(3L)).thenReturn(Optional.of(college));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User savedUser = invocation.getArgument(0);
                    savedUser.setId(7L);
                    return savedUser;
                });
        when(jwtUtil.generateToken(anyString(), anyLong(), anyLong()))
                .thenReturn("jwt-token");

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("victim@college.edu");
        request.setOtp("123456");
        request.setName("Victim");
        request.setPassword("VictimPass1");

        AuthResponse response = authService.verifyOtp(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();

        assertThat(user.getName()).isEqualTo("Victim");
        assertThat(user.getIsCollegeVerified()).isTrue();
        assertThat(passwordEncoder.matches("VictimPass1", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo("attacker-hash");
        assertThat(response.getToken()).isEqualTo("jwt-token");
    }
}
