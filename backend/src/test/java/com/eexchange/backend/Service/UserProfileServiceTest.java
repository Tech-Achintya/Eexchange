package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.ChangePasswordRequest;
import com.eexchange.backend.DTO.DeleteAccountRequest;
import com.eexchange.backend.DTO.TokenResponse;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.InterestMessageRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.SavedPostRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Utils.JWTutil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileServiceTest {

    private UserRepository userRepository;
    private PostRepository postRepository;
    private SavedPostRepository savedPostRepository;
    private InterestMessageRepository interestMessageRepository;
    private PasswordEncoder passwordEncoder;
    private JWTutil jwtUtil;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private UserProfileService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);
        postRepository = mock(PostRepository.class);
        savedPostRepository = mock(SavedPostRepository.class);
        interestMessageRepository = mock(InterestMessageRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        jwtUtil = mock(JWTutil.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtUtil.getExpiryMs()).thenReturn(3_600_000L);

        service = new UserProfileService(
                userRepository,
                postRepository,
                savedPostRepository,
                interestMessageRepository,
                passwordEncoder,
                jwtUtil,
                redisTemplate);
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        User user = existingUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        AppException ex = assertThrows(AppException.class,
                () -> service.changePassword(7L, changePassword("wrong-pass", "newpass123")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
        verify(jwtUtil, never()).generateToken(anyString(), anyLong(), anyLong());
    }

    @Test
    void changePasswordRejectsSamePassword() {
        User user = existingUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        AppException ex = assertThrows(AppException.class,
                () -> service.changePassword(7L, changePassword("oldpass123", "oldpass123")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordRejectsWeakPassword() {
        User user = existingUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        AppException ex = assertThrows(AppException.class,
                () -> service.changePassword(7L, changePassword("oldpass123", "short")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordWritesCutoffBeforeGeneratingFreshToken() {
        User user = existingUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateToken("user@college.edu", 7L, 3L))
                .thenReturn("fresh-token");

        TokenResponse response = service.changePassword(
                7L, changePassword("oldpass123", "newpass123"));

        assertThat(response.token()).isEqualTo("fresh-token");
        verify(redisTemplate).delete("login:fail:user@college.edu");

        InOrder order = inOrder(valueOperations, jwtUtil);
        order.verify(valueOperations).set(
                eq("user:tokens-valid-after:7"),
                anyString(),
                eq(Duration.ofMillis(3_600_000L)));
        order.verify(jwtUtil).generateToken("user@college.edu", 7L, 3L);
    }

    @Test
    void deleteAccountRejectsWrongPassword() {
        User user = existingUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        AppException ex = assertThrows(AppException.class,
                () -> service.deleteAccount(7L, deleteAccount("wrong-pass")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(postRepository, never()).deleteByUserId(anyLong());
        verify(savedPostRepository, never()).deleteAllByUserId(anyLong());
        verify(interestMessageRepository, never()).deleteAllBySenderId(anyLong());
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteAccountAnonymizesUserAndDeletesOwnedRows() {
        User user = existingUser();
        String oldHash = user.getPasswordHash();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteAccount(7L, deleteAccount("oldpass123"));

        assertThat(user.getEmail()).isEqualTo("deleted_7@deleted.invalid");
        assertThat(user.getEmail()).isEqualTo(user.getEmail().toLowerCase());
        assertThat(user.getName()).isEqualTo("Deleted user");
        assertThat(user.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(user.getDeletedAt()).isNotNull();

        verify(interestMessageRepository).deleteAllBySenderId(7L);
        verify(savedPostRepository).deleteAllByUserId(7L);
        verify(postRepository).deleteByUserId(7L);

        verify(redisTemplate).delete("user:status:7");
        verify(redisTemplate).delete("user:tokens-valid-after:7");
        verify(redisTemplate).delete("login:fail:user@college.edu");
    }

    private User existingUser() {
        College college = College.builder()
                .id(3L)
                .name("Test College")
                .build();

        return User.builder()
                .id(7L)
                .email("user@college.edu")
                .name("Old Name")
                .passwordHash(passwordEncoder.encode("oldpass123"))
                .college(college)
                .isCollegeVerified(true)
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private ChangePasswordRequest changePassword(String oldPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        return request;
    }

    private DeleteAccountRequest deleteAccount(String password) {
        DeleteAccountRequest request = new DeleteAccountRequest();
        request.setPassword(password);
        return request;
    }
}