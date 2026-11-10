package com.eexchange.backend.Service;

import com.eexchange.backend.Service.Cache.CollegeFeedVersionService;

import com.eexchange.backend.DTO.Request.ChangePasswordRequest;
import com.eexchange.backend.DTO.Request.DeleteAccountRequest;
import com.eexchange.backend.DTO.Response.TokenResponse;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.*;
import com.eexchange.backend.Utils.JWTutil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
    private PostImageRepository postImageRepository;
    private StorageService storageService;
    private TokenRevocationService tokenRevocationService;
    private LoginLockoutService loginLockoutService;


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
        postImageRepository = mock(PostImageRepository.class);
        storageService = mock(StorageService.class);
        tokenRevocationService = mock(TokenRevocationService.class);
        loginLockoutService = mock(LoginLockoutService.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtUtil.getExpiryMs()).thenReturn(3_600_000L);

        service = new UserProfileService(
                userRepository,
                postRepository,
                savedPostRepository,
                interestMessageRepository,
                passwordEncoder,
                jwtUtil,
                redisTemplate,
                postImageRepository,
                storageService,
                mock(CollegeFeedVersionService.class),
                tokenRevocationService,
                loginLockoutService);
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
        verify(loginLockoutService)
                .clearFailures(LoginLockoutService.SCOPE_USER, "user@college.edu");

        InOrder order = inOrder(tokenRevocationService, jwtUtil);
        order.verify(tokenRevocationService).revokeAllTokens(7L);
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
        verify(loginLockoutService)
                .clearFailures(LoginLockoutService.SCOPE_USER, "user@college.edu");
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
    @Test
    void deleteAccountDeletesAllUserImagesAfterCommit() {
        User user = existingUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(postImageRepository.findUrlsByUserId(7L))
                .thenReturn(List.of("posts/7/a.jpg", "posts/7/b.png"));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteAccount(7L, deleteAccount("oldpass123"));

            // Keys were read before the posts were deleted.
            verify(postImageRepository).findUrlsByUserId(7L);
            verify(postRepository).deleteByUserId(7L);

            // Nothing removed yet.
            verify(storageService, never()).delete(anyString());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(storageService).delete("posts/7/a.jpg");
            verify(storageService).delete("posts/7/b.png");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteAccountStorageFailureDoesNotUndoDeletion() {
        User user = existingUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(postImageRepository.findUrlsByUserId(7L))
                .thenReturn(List.of("posts/7/a.jpg"));

        doThrow(new AppException("S3 down", HttpStatus.BAD_GATEWAY))
                .when(storageService).delete("posts/7/a.jpg");

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() ->
                    service.deleteAccount(7L, deleteAccount("oldpass123")));

            assertDoesNotThrow(() -> TransactionSynchronizationManager
                    .getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("deleted_7@deleted.invalid");
    }
}