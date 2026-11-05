package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Request.ReportCreateRequest;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.ReportStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.Report;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.ReportRepository;
import com.eexchange.backend.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int MAX_COMMENT_LENGTH = 500;
    private static final String REPORTS_HIDDEN_REASON = "REPORTS";

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;

    @Value("${app.reports.auto-hide-threshold:3}")
    private int autoHideThreshold;

    @Transactional
    public void reportPost(Long userId, Long postId, ReportCreateRequest request) {
        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(
                        "Authentication required", HttpStatus.UNAUTHORIZED));

        Post post = postRepository.findDetailById(postId)
                .orElseThrow(this::notFound);

        // Only visible posts can be reported. Never 403: do not confirm existence.
        boolean visible = post.getCollege().getId().equals(user.getCollege().getId())
                && post.getStatus() == PostStatus.APPROVED;
        if (!visible) {
            throw notFound();
        }

        if (post.getUser().getId().equals(userId)) {
            throw new AppException("You cannot report your own post", HttpStatus.BAD_REQUEST);
        }

        String comment = trimToNull(request.getComment());
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new AppException("Comment cannot exceed 500 characters",
                    HttpStatus.BAD_REQUEST);
        }

        if (reportRepository.existsByPostIdAndReporterId(postId, userId)) {
            throw new AppException("You have already reported this post",
                    HttpStatus.CONFLICT);
        }

        Report report = Report.builder()
                .post(post)
                .reporter(user)
                .reason(request.getReason())
                .comment(comment)
                .status(ReportStatus.OPEN)
                .build();

        try {
            // Flush so the unique index reports a racing duplicate here, not later.
            reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException("You have already reported this post",
                    HttpStatus.CONFLICT);
        }

        long openReports = reportRepository.countByPostIdAndStatus(postId, ReportStatus.OPEN);

        if (openReports >= autoHideThreshold) {
            // ONE statement, guarded by status = APPROVED, so concurrent reports race safely.
            postRepository.hideIfApproved(
                    postId, PostStatus.HIDDEN, REPORTS_HIDDEN_REASON, PostStatus.APPROVED);
        }
    }

    private AppException notFound() {
        return new AppException("Post not found", HttpStatus.NOT_FOUND);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}