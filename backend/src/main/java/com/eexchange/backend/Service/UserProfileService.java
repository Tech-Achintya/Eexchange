package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Request.ChangePasswordRequest;
import com.eexchange.backend.DTO.Request.DeleteAccountRequest;
import com.eexchange.backend.DTO.Response.TokenResponse;
import com.eexchange.backend.DTO.Request.UpdateProfileRequest;
import com.eexchange.backend.DTO.Response.UserProfileResponse;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.*;
import com.eexchange.backend.Utils.JWTutil;
import com.eexchange.backend.Utils.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.eexchange.backend.Service.Cache.CollegeFeedVersionService;


@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final String KEY_USER_STATUS = "user:status:";
    private static final String KEY_RESET_ACTIVE = "pwd_reset:active:";
    private static final String KEY_RESET_TOKEN = "pwd_reset:token:";
    private static final String KEY_RESET_COOLDOWN = "pwd_reset:cooldown:";
    private static final String KEY_RESET_HOURLY = "pwd_reset:hourly:";

    private static final int NAME_MIN_LENGTH = 2;
    private static final int NAME_MAX_LENGTH = 100;

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final SavedPostRepository savedPostRepository;
    private final InterestMessageRepository interestMessageRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTutil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final PostImageRepository postImageRepository;
    private final StorageService storageService;
    private final CollegeFeedVersionService collegeFeedVersionService;
    private final TokenRevocationService tokenRevocationService;
    private final LoginLockoutService loginLockoutService;
    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return toProfile(findUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);

        String trimmedName = request.getName() == null ? "" : request.getName().trim();
        if (trimmedName.length() < NAME_MIN_LENGTH || trimmedName.length() > NAME_MAX_LENGTH) {
            throw new AppException(
                    "Name must be between " + NAME_MIN_LENGTH + " and " + NAME_MAX_LENGTH + " characters",
                    HttpStatus.BAD_REQUEST);
        }

        user.setName(trimmedName);
        User saved = userRepository.save(user);
        if (saved.getCollege() != null) {
            collegeFeedVersionService.incrementVersion(saved.getCollege().getId());
        }
        return toProfile(saved);
    }

    @Transactional
    public TokenResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new AppException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }

        validatePasswordBytes(request.getNewPassword());

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new AppException("New password must be different from the current password",
                    HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // IMPORTANT ORDER: cutoff first, then token generation.
        // The cutoff rejects every older token; the freshly generated token
        // below is issued after the cutoff and therefore stays valid.
        tokenRevocationService.revokeAllTokens(userId);

        // A password change must also clear the failed-login lockout counter.
        loginLockoutService.clearFailures(LoginLockoutService.SCOPE_USER, user.getEmail());

        // Generated after the cutoff write, so its iat is >= cutoff.
        String freshToken = jwtUtil.generateToken(
                user.getEmail(),
                user.getId(),
                user.getCollege().getId());

        return new TokenResponse(freshToken);
    }

    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException("Password is incorrect", HttpStatus.BAD_REQUEST);
        }

        String formerEmail = user.getEmail();

        // 1. Delete messages this user sent on other people's posts.
        interestMessageRepository.deleteAllBySenderId(userId);

        // 2. Delete this user's saved posts.
        savedPostRepository.deleteAllByUserId(userId);

        // 3. Collect image keys BEFORE the posts (and their post_images) are
        //    deleted, so we can remove the actual objects afterwards.
        List<String> imageKeys = postImageRepository.findUrlsByUserId(userId);
        postRepository.deleteByUserId(userId);

        // 4. Anonymize instead of deleting the row, so reports/audit history
        //    keeps a valid foreign key.
        //    The email MUST stay lowercase because chk_users_email_normalized
        //    requires email = lower(trim(email)).
        user.setEmail("deleted_" + userId + "@deleted.invalid");
        user.setName("Deleted user");
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        // 5. Remove all user-scoped Redis state.
        clearRedisKeysForUser(userId, formerEmail);
        // 6. Delete the objects only if the account deletion commits.
        //    A storage failure is logged and must never roll back the account.
        deleteStorageAfterCommit(imageKeys);
    }

    private void validatePasswordBytes(String password) {
        PasswordPolicy.validateUserPassword(password);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.UNAUTHORIZED));
    }

    private UserProfileResponse toProfile(User user) {
        College college = user.getCollege();

        UserProfileResponse.CollegeSummary collegeSummary = college == null
                ? null
                : new UserProfileResponse.CollegeSummary(college.getId(), college.getName());

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                collegeSummary,
                user.getIsCollegeVerified(),
                user.getStatus(),
                user.getCreatedAt());
    }

    private void clearRedisKeysForUser(Long userId, String formerEmail) {
        // If a password-reset token is still active, delete its token key too.
        String activeResetToken = redisTemplate.opsForValue().get(KEY_RESET_ACTIVE + userId);
        if (activeResetToken != null) {
            redisTemplate.delete(KEY_RESET_TOKEN + activeResetToken);
        }

        for (String key : List.of(
                KEY_USER_STATUS + userId,
                TokenRevocationService.KEY_TOKENS_VALID_AFTER + userId,
                KEY_RESET_ACTIVE + userId,
                KEY_RESET_COOLDOWN + formerEmail,
                KEY_RESET_HOURLY + formerEmail)) {
            redisTemplate.delete(key);
        }
        loginLockoutService.clearFailures(LoginLockoutService.SCOPE_USER, formerEmail);
    }
    private void deleteStorageAfterCommit(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteStorageQuietly(keys);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteStorageQuietly(keys);
                    }
                });
    }

    private void deleteStorageQuietly(List<String> keys) {
        for (String key : keys) {
            try {
                storageService.delete(key);
            } catch (RuntimeException ex) {
                log.warn("Account deleted but storage object {} was not removed", key, ex);
            }
        }
    }
}