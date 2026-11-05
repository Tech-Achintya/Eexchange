package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Request.AdminReportActionRequest;
import com.eexchange.backend.DTO.Response.AdminReportResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.ReportStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.Report;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.AdminRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String REPORTS_HIDDEN_REASON = "REPORTS";

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final AdminRepository adminRepository;
    private final AuditService auditService;
    private final PostMediaCleanup mediaCleanup;

    @Transactional(readOnly = true)
    public PageResponse<AdminReportResponse> list(ReportStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));
        Page<Report> reports = reportRepository
                .findByStatusOrderByCreatedAtAsc(status, pageable);

        if (reports.getContent().isEmpty()) {
            return pageResponse(reports, List.of());
        }

        List<Long> postIds = reports.getContent().stream()
                .map(report -> report.getPost().getId())
                .distinct()
                .toList();

        Map<Long, Long> openCounts = reportRepository
                .countByPostIdsAndStatus(postIds, ReportStatus.OPEN)
                .stream()
                .collect(Collectors.toMap(
                        ReportRepository.PostOpenCount::getPostId,
                        ReportRepository.PostOpenCount::getTotal));

        List<AdminReportResponse> content = reports.getContent().stream()
                .map(report -> toResponse(
                        report,
                        openCounts.getOrDefault(report.getPost().getId(), 0L)))
                .toList();

        return pageResponse(reports, content);
    }

    @Transactional
    public void act(Long adminId, Long reportId, AdminReportActionRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new AppException("Report not found", HttpStatus.NOT_FOUND));

        if (report.getStatus() != ReportStatus.OPEN) {
            throw new AppException("Report is already resolved", HttpStatus.BAD_REQUEST);
        }

        Admin admin = adminRepository.getReferenceById(adminId);
        Instant now = Instant.now();
        String note = trimToNull(request.getNote());
        Long postId = report.getPost().getId();

        if (request.getAction() == AdminReportActionRequest.Action.DISMISS) {
            dismiss(report, admin, now, note, adminId, postId);
            return;
        }

        if (Boolean.TRUE.equals(request.getDeletePost())) {
            resolveAndDeletePost(report, admin, now, note, adminId, postId);
            return;
        }

        resolveOne(report, admin, now, note, adminId, postId);
    }

    // ------------------------------------------------------------ branches

    private void dismiss(Report report, Admin admin, Instant now, String note,
                         Long adminId, Long postId) {

        report.setStatus(ReportStatus.DISMISSED);
        report.setResolvedBy(admin);
        report.setResolvedAt(now);
        report.setResolutionNote(note);
        reportRepository.saveAndFlush(report);

        // Flush above means this count sees the DISMISSED row.
        long openRemaining = reportRepository.countByPostIdAndStatus(postId, ReportStatus.OPEN);

        if (openRemaining == 0) {
            postRepository.findById(postId).ifPresent(post -> {
                if (post.getStatus() == PostStatus.HIDDEN
                        && REPORTS_HIDDEN_REASON.equals(post.getHiddenReason())) {
                    post.setStatus(PostStatus.APPROVED);
                    post.setHiddenReason(null);
                    postRepository.save(post);
                }
            });
        }

        auditService.log(adminId, AuditAction.DISMISS_REPORT, AuditTargetType.REPORT,
                report.getId(), note, Map.of("postId", postId));
    }

    private void resolveOne(Report report, Admin admin, Instant now, String note,
                            Long adminId, Long postId) {

        report.setStatus(ReportStatus.RESOLVED);
        report.setResolvedBy(admin);
        report.setResolvedAt(now);
        report.setResolutionNote(note);
        reportRepository.saveAndFlush(report);

        auditService.log(adminId, AuditAction.RESOLVE_REPORT, AuditTargetType.REPORT,
                report.getId(), note,
                Map.of("postId", postId, "deletePost", false));
    }

    private void resolveAndDeletePost(Report report, Admin admin, Instant now, String note,
                                      Long adminId, Long postId) {

        Post post = postRepository.findAdminDetailById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        if (post.getStatus() == PostStatus.DELETED) {
            throw new AppException("Post is already deleted", HttpStatus.BAD_REQUEST);
        }

        List<String> keys = mediaCleanup.keysOf(post);

        post.setStatus(PostStatus.DELETED);
        postRepository.save(post);

        int resolved = reportRepository.resolveAllOpenForPost(
                postId, ReportStatus.RESOLVED, ReportStatus.OPEN, admin, now, note);

        mediaCleanup.deleteAfterCommit(keys);

        auditService.log(adminId, AuditAction.RESOLVE_REPORT, AuditTargetType.REPORT,
                report.getId(), note,
                Map.of("postId", postId, "deletePost", true));
        auditService.log(adminId, AuditAction.DELETE_POST, AuditTargetType.POST,
                postId, note,
                Map.of("reportId", report.getId(), "resolvedReports", resolved));
    }

    // ------------------------------------------------------------- helpers

    private AdminReportResponse toResponse(Report report, long openReportCount) {
        return new AdminReportResponse(
                report.getId(),
                report.getReason(),
                report.getComment(),
                report.getReporter() == null ? null : report.getReporter().getId(),
                report.getReporter() == null ? null : report.getReporter().getName(),
                report.getPost().getId(),
                report.getPost().getTitle(),
                report.getPost().getStatus(),
                openReportCount,
                report.getCreatedAt());
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}