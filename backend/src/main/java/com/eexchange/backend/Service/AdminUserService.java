package com.eexchange.backend.Service;

import com.eexchange.backend.Audit.AuditEvent;
import com.eexchange.backend.Audit.AuditEventRepository;
import com.eexchange.backend.DTO.Request.AdminUserBlockRequest;
import com.eexchange.backend.DTO.Request.AdminUserUnblockRequest;
import com.eexchange.backend.DTO.Response.AdminUserDetailResponse;
import com.eexchange.backend.DTO.Response.AdminUserRowResponse;
import com.eexchange.backend.DTO.Response.AuditLogResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Enums.AdminRole;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.ReportStatus;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.PostImageRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.ReportRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Service.Cache.CollegeFeedVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int REASON_MIN = 5;
    private static final int REASON_MAX = 500;
    private static final String USER_BLOCKED_REASON = "USER_BLOCKED";

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final ReportRepository reportRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditService auditService;
    private final PostMediaCleanup mediaCleanup;
    private final UserStatusCache userStatusCache;
    private final EmailService emailService;
    private final CollegeFeedVersionService collegeFeedVersionService;

    @Value("${app.admin.max-temp-block-days:90}")
    private int maxTempBlockDays;

    // ------------------------------------------------------------- list/detail

    @Transactional(readOnly = true)
    public PageResponse<AdminUserRowResponse> list(String q,
                                                   UserStatus status,
                                                   Long collegeId,
                                                   int page,
                                                   int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<User> users = userRepository.findForAdmin(
                likePattern(q), status, collegeId, pageable);

        if (users.getContent().isEmpty()) {
            return pageResponse(users, List.of());
        }

        List<Long> userIds = users.getContent().stream().map(User::getId).toList();

        Map<Long, Long> postCounts = postRepository
                .countByUserIdsExcludingStatus(userIds, PostStatus.DELETED)
                .stream()
                .collect(Collectors.toMap(
                        PostRepository.UserPostTotal::getUserId,
                        PostRepository.UserPostTotal::getTotal));

        List<AdminUserRowResponse> content = users.getContent().stream()
                .map(user -> toRow(user, postCounts.getOrDefault(user.getId(), 0L)))
                .toList();

        return pageResponse(users, content);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse detail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        if (user.getDeletedAt() != null) {
            throw new AppException("User not found", HttpStatus.NOT_FOUND);
        }

        Map<PostStatus, Long> counts = new EnumMap<>(PostStatus.class);
        for (PostStatus status : PostStatus.values()) {
            counts.put(status, 0L);
        }
        for (PostRepository.UserPostStatusCount row
                : postRepository.countByUserIdGroupByStatus(userId)) {
            counts.put(row.getStatus(), row.getTotal());
        }

        long postCount = counts.entrySet().stream()
                .filter(entry -> entry.getKey() != PostStatus.DELETED)
                .mapToLong(Map.Entry::getValue)
                .sum();

        long openReports = reportRepository.countByPostOwnerIdAndStatus(
                userId, ReportStatus.OPEN);

        List<AuditLogResponse> recentAudit = auditEventRepository
                .findRecentByTarget(AuditTargetType.USER, userId, 10)
                .stream()
                .map(this::toAudit)
                .toList();

        return new AdminUserDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                college(user),
                user.getStatus(),
                user.getBlockedUntil(),
                user.getBlockReason(),
                user.getBlockedAt(),
                user.getIsCollegeVerified(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                postCount,
                counts,
                openReports,
                recentAudit);
    }

    // ------------------------------------------------------------------ block

    @Transactional
    public void block(Long adminId,
                      AdminRole adminRole,
                      Long userId,
                      AdminUserBlockRequest request) {

        if (request.getType() == null) {
            throw new AppException("Block type is required", HttpStatus.BAD_REQUEST);
        }

        String reason = requireReason(request.getReason());
        Instant now = Instant.now();
        Instant until = null;

        if (request.getType() == AdminUserBlockRequest.Type.TEMP) {
            until = validateUntil(request.getUntil(), now);
        } else if (adminRole != AdminRole.SUPER_ADMIN) {
            throw new AppException("Only a super admin can permanently block a user",
                    HttpStatus.FORBIDDEN);
        }

        User user = findBlockableUser(userId);
        UserStatus previousStatus = user.getStatus();

        if (previousStatus == UserStatus.PERM_BLOCKED) {
            throw new AppException("User is already permanently blocked", HttpStatus.CONFLICT);
        }

        UserStatus nextStatus = request.getType() == AdminUserBlockRequest.Type.TEMP
                ? UserStatus.TEMP_BLOCKED
                : UserStatus.PERM_BLOCKED;

        String email = user.getEmail();

        user.setStatus(nextStatus);
        user.setBlockedAt(now);
        user.setBlockedUntil(until);
        user.setBlockReason(reason);
        userRepository.save(user);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousStatus", previousStatus.name());
        metadata.put("until", until == null ? null : until.toString());

        if (nextStatus == UserStatus.TEMP_BLOCKED) {
            int postsHidden = postRepository.hideApprovedByUserId(
                    userId, PostStatus.HIDDEN, USER_BLOCKED_REASON, PostStatus.APPROVED);
            metadata.put("postsHidden", postsHidden);

            auditService.log(adminId, AuditAction.BLOCK_TEMP, AuditTargetType.USER,
                    userId, reason, metadata);
        } else {
            // Keys MUST be read before the posts disappear.
            List<String> imageKeys = postImageRepository.findUrlsByUserId(userId);
            int postsDeleted = postRepository.deleteByUserId(userId);
            mediaCleanup.deleteAfterCommit(imageKeys);

            metadata.put("postsDeleted", postsDeleted);

            auditService.log(adminId, AuditAction.BLOCK_PERM, AuditTargetType.USER,
                    userId, reason, metadata);
        }

        // Both block types affect what the college feed can show.
        collegeFeedVersionService.incrementVersion(user.getCollege().getId());

        // A rollback must never flip Redis or send mail.
        // A rollback must never flip Redis or send mail.
        final Instant blockUntil = until;
        afterCommit(() -> {
            updateStatusCacheQuietly(userId, nextStatus);
            emailService.sendAccountBlockedEmail(email, reason, blockUntil);
        });
    }

    // ---------------------------------------------------------------- unblock

    @Transactional
    public void unblock(Long adminId,
                        AdminRole adminRole,
                        Long userId,
                        AdminUserUnblockRequest  request) {

        String reason = requireUnblockReason(request.getReason());
        User user = findBlockableUser(userId);
        UserStatus previousStatus = user.getStatus();

        if (previousStatus == UserStatus.ACTIVE) {
            throw new AppException("User is not blocked", HttpStatus.CONFLICT);
        }

        if (previousStatus == UserStatus.PERM_BLOCKED
                && adminRole != AdminRole.SUPER_ADMIN) {
            throw new AppException("Only a super admin can unblock a permanently blocked user",
                    HttpStatus.FORBIDDEN);
        }

        String email = user.getEmail();

        user.setStatus(UserStatus.ACTIVE);
        user.setBlockedUntil(null);
        user.setBlockReason(null);
        user.setBlockedAt(null);
        userRepository.save(user);

        int postsRestored = postRepository.restoreUserBlockedByUserId(
                userId, PostStatus.APPROVED, PostStatus.HIDDEN, USER_BLOCKED_REASON);

        collegeFeedVersionService.incrementVersion(user.getCollege().getId());

        auditService.log(adminId, AuditAction.UNBLOCK, AuditTargetType.USER, userId, reason,
                Map.of("previousStatus", previousStatus.name(),
                        "postsRestored", postsRestored));

        afterCommit(() -> {
            updateStatusCacheQuietly(userId, UserStatus.ACTIVE);
            emailService.sendAccountUnblockedEmail(email);
        });
    }

    // ------------------------------------------------- scheduled-job support

    // Called by TempBlockExpiryJob. Returns false when an admin already changed
    // the row, so the caller skips it.
    @Transactional
    public boolean unblockExpired(Long userId, Instant now) {
        int changed = userRepository.unblockIfExpired(
                userId, UserStatus.ACTIVE, UserStatus.TEMP_BLOCKED, now);

        if (changed == 0) {
            return false;
        }

        int postsRestored = postRepository.restoreUserBlockedByUserId(
                userId, PostStatus.APPROVED, PostStatus.HIDDEN, USER_BLOCKED_REASON);

        // The block was actually lifted by the system, not by an admin.
        auditService.logSystem(AuditAction.AUTO_UNBLOCK, AuditTargetType.USER, userId,
                "Temporary block expired", Map.of("postsRestored", postsRestored));

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            collegeFeedVersionService.incrementVersion(user.getCollege().getId());
            afterCommit(() -> {
                updateStatusCacheQuietly(userId, UserStatus.ACTIVE);
                emailService.sendAccountUnblockedEmail(user.getEmail());
            });
        }

        log.info("TEMP block expired for user {}; {} post(s) restored",
                userId, postsRestored);
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private User findBlockableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        if (user.getDeletedAt() != null) {
            throw new AppException("User account is deleted", HttpStatus.BAD_REQUEST);
        }
        return user;
    }

    private String requireReason(String raw) {
        String reason = raw == null ? "" : raw.trim();
        if (reason.length() < REASON_MIN || reason.length() > REASON_MAX) {
            throw new AppException("Reason must be between 5 and 500 characters",
                    HttpStatus.BAD_REQUEST);
        }
        return reason;
    }

    private String requireUnblockReason(String raw) {
        String reason = raw == null ? "" : raw.trim();
        if (reason.isEmpty() || reason.length() > REASON_MAX) {
            throw new AppException("Reason is required (max 500 characters)",
                    HttpStatus.BAD_REQUEST);
        }
        return reason;
    }

    private Instant validateUntil(Instant until, Instant now) {
        if (until == null) {
            throw new AppException("until is required for a temporary block",
                    HttpStatus.BAD_REQUEST);
        }
        if (!until.isAfter(now)) {
            throw new AppException("until must be in the future", HttpStatus.BAD_REQUEST);
        }
        Instant max = now.plus(maxTempBlockDays, ChronoUnit.DAYS);
        if (until.isAfter(max)) {
            throw new AppException(
                    "Temporary block cannot exceed " + maxTempBlockDays + " days",
                    HttpStatus.BAD_REQUEST);
        }
        return until;
    }

    private void updateStatusCacheQuietly(Long userId, UserStatus status) {
        try {
            userStatusCache.put(userId, status);
        } catch (RuntimeException ex) {
            log.warn("Could not update user:status cache for user {} to {}",
                    userId, status, ex);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }

    private AdminUserRowResponse toRow(User user, long postCount) {
        return new AdminUserRowResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                college(user),
                user.getStatus(),
                user.getBlockedUntil(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                postCount);
    }

    private AdminUserRowResponse.CollegeSummary college(User user) {
        if (user.getCollege() == null) {
            return null;
        }
        return new AdminUserRowResponse.CollegeSummary(
                user.getCollege().getId(), user.getCollege().getName());
    }

    private AuditLogResponse toAudit(AuditEvent audit) {
        return new AuditLogResponse(
                audit.getId(),
                audit.getActorType(),
                audit.getActorId(),
                audit.getActorName(),
                audit.getAction(),
                audit.getTargetType(),
                audit.getTargetId(),
                audit.getReason(),
                audit.getMetadata(),
                audit.getCreatedAt());
    }

    private String likePattern(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String escaped = raw.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private <T> PageResponse<T> pageResponse(Page<?> page, List<T> content) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}