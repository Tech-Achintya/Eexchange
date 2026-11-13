package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Response.AdminPostCardResponse;
import com.eexchange.backend.DTO.Response.AdminPostDetailResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.ReportStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import com.eexchange.backend.Entity.Report;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.AdminRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.ReportRepository;
import com.eexchange.backend.Service.Cache.CollegeFeedVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPostService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final AdminRepository adminRepository;
    private final AuditService auditService;
    private final PostMediaCleanup mediaCleanup;
    private final StorageService storageService;
    private final CollegeFeedVersionService collegeFeedVersionService;

    @Transactional(readOnly = true)
    public PageResponse<AdminPostCardResponse> list(PostStatus status,
                                                    String q,
                                                    Long collegeId,
                                                    int page,
                                                    int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<Post> posts = postRepository.findForAdmin(
                status, collegeId, likePattern(q), pageable);

        if (posts.getContent().isEmpty()) {
            return pageResponse(posts, List.of());
        }

        List<Long> postIds = posts.getContent().stream().map(Post::getId).toList();

        Map<Long, Long> openCounts = reportRepository
                .countByPostIdsAndStatus(postIds, ReportStatus.OPEN)
                .stream()
                .collect(Collectors.toMap(
                        ReportRepository.PostOpenCount::getPostId,
                        ReportRepository.PostOpenCount::getTotal));

        List<AdminPostCardResponse> content = posts.getContent().stream()
                .map(post -> new AdminPostCardResponse(
                        post.getId(),
                        post.getTitle(),
                        post.getPrice(),
                        post.getStatus(),
                        post.getHiddenReason(),
                        post.getCollege().getId(),
                        post.getCollege().getName(),
                        post.getUser().getId(),
                        post.getUser().getName(),
                        openCounts.getOrDefault(post.getId(), 0L),
                        aiVerdict(post),
                        post.getCreatedAt()))
                .toList();

        return pageResponse(posts, content);
    }

    @Transactional(readOnly = true)
    public AdminPostDetailResponse detail(Long postId) {
        Post post = postRepository.findAdminDetailById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        List<Report> reports = reportRepository.findByPostIdOrderByCreatedAtAsc(postId);

        return new AdminPostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getDescription(),
                post.getPrice(),
                post.getCondition(),
                post.getCategory().getId(),
                post.getCategory().getName(),
                post.getStatus(),
                post.getRejectionReason(),
                post.getHiddenReason(),
                post.getModerationAttempts(),
                post.getEditCount(),
                post.getCollege().getId(),
                post.getCollege().getName(),
                post.getUser().getId(),
                post.getUser().getName(),
                post.getImages().stream()
                        .map(PostImage::getUrl)
                        .map(storageService::publicUrl)
                        .toList(),
                post.getAiReviewResult(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getExpiresAt(),
                reports.stream()
                        .map(report -> new AdminPostDetailResponse.ReportSummary(
                                report.getId(),
                                report.getReason(),
                                report.getComment(),
                                report.getReporter() == null ? null : report.getReporter().getId(),
                                report.getReporter() == null ? null : report.getReporter().getName(),
                                report.getStatus(),
                                report.getCreatedAt()))
                        .toList());
    }

    @Transactional
    public void delete(Long adminId, Long postId, String reason) {
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isEmpty()) {
            throw new AppException("Reason is required", HttpStatus.BAD_REQUEST);
        }

        Post post = postRepository.findAdminDetailById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        if (post.getStatus() == PostStatus.DELETED) {
            throw new AppException("Post is already deleted", HttpStatus.BAD_REQUEST);
        }

        Admin admin = adminRepository.getReferenceById(adminId);
        List<String> keys = mediaCleanup.keysOf(post);

        // Only APPROVED posts are in the college feed.
        boolean wasVisible = post.getStatus() == PostStatus.APPROVED;

        post.setStatus(PostStatus.DELETED);
        postRepository.save(post);
        if (wasVisible) {
            collegeFeedVersionService.incrementVersion(post.getCollege().getId());
        }

        int resolved = reportRepository.resolveAllOpenForPost(
                postId, ReportStatus.RESOLVED, ReportStatus.OPEN, admin, Instant.now(),
                "Post deleted by admin: " + trimmedReason);

        mediaCleanup.deleteAfterCommit(keys);

        auditService.log(adminId, AuditAction.DELETE_POST, AuditTargetType.POST,
                postId, trimmedReason, Map.of("resolvedReports", resolved));
    }

    // ------------------------------------------------------------- helpers

    private String aiVerdict(Post post) {
        Map<String, Object> review = post.getAiReviewResult();
        if (review == null) {
            return null;
        }
        Object verdict = review.get("verdict");
        return verdict == null ? null : verdict.toString();
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