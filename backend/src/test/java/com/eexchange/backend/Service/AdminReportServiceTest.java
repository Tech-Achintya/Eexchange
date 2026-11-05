package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Request.AdminReportActionRequest;
import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.AdminRole;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.ReportReason;
import com.eexchange.backend.Entity.Enums.ReportStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import com.eexchange.backend.Entity.Report;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Repository.AdminRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminReportServiceTest {

    private ReportRepository reportRepository;
    private PostRepository postRepository;
    private AuditService auditService;
    private StorageService storageService;
    private AdminReportService service;

    @BeforeEach
    void setUp() {
        reportRepository = mock(ReportRepository.class);
        postRepository = mock(PostRepository.class);
        AdminRepository adminRepository = mock(AdminRepository.class);
        auditService = mock(AuditService.class);
        storageService = mock(StorageService.class);

        when(adminRepository.getReferenceById(1L))
                .thenReturn(Admin.builder().id(1L).role(AdminRole.MODERATOR).build());

        service = new AdminReportService(reportRepository, postRepository,
                adminRepository, auditService, new PostMediaCleanup(storageService));
    }

    @Test
    void dismissRestoresHiddenPostOnlyWhenNoOpenReportsRemain() {
        Report report = openReport();
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        // One open report remains -> no restore.
        when(reportRepository.countByPostIdAndStatus(1L, ReportStatus.OPEN)).thenReturn(1L);
        Post post = hiddenReportPost();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        service.act(1L, 10L, action(AdminReportActionRequest.Action.DISMISS, false));

        assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN);

        // No open reports remain -> restore.
        Report second = openReport();
        when(reportRepository.findById(10L)).thenReturn(Optional.of(second));
        when(reportRepository.countByPostIdAndStatus(1L, ReportStatus.OPEN)).thenReturn(0L);

        service.act(1L, 10L, action(AdminReportActionRequest.Action.DISMISS, false));

        assertThat(post.getStatus()).isEqualTo(PostStatus.APPROVED);
        assertThat(post.getHiddenReason()).isNull();
    }

    @Test
    void dismissDoesNotRestoreAPostHiddenForAnotherReason() {
        Report report = openReport();
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(reportRepository.countByPostIdAndStatus(1L, ReportStatus.OPEN)).thenReturn(0L);

        Post post = hiddenReportPost();
        post.setHiddenReason("ADMIN");
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        service.act(1L, 10L, action(AdminReportActionRequest.Action.DISMISS, false));

        assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN);
        assertThat(post.getHiddenReason()).isEqualTo("ADMIN");
    }

    @Test
    void resolveWithoutDeleteOnlyResolvesThisReport() {
        Report report = openReport();
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        service.act(1L, 10L, action(AdminReportActionRequest.Action.RESOLVE, false));

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        verify(reportRepository, never()).resolveAllOpenForPost(
                any(), any(), any(), any(), any(), any());
        verify(auditService).log(eq(1L), eq(AuditAction.RESOLVE_REPORT),
                eq(AuditTargetType.REPORT), eq(10L), any(), any());
    }

    @Test
    void resolveWithDeleteSoftDeletesResolvesAllAndDeletesFilesAfterCommit() {
        Report report = openReport();
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(postRepository.findAdminDetailById(1L))
                .thenReturn(Optional.of(hiddenReportPost()));
        when(reportRepository.resolveAllOpenForPost(
                eq(1L), eq(ReportStatus.RESOLVED), eq(ReportStatus.OPEN),
                any(), any(), any())).thenReturn(2);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.act(1L, 10L, action(AdminReportActionRequest.Action.RESOLVE, true));

            verify(reportRepository).resolveAllOpenForPost(
                    eq(1L), eq(ReportStatus.RESOLVED), eq(ReportStatus.OPEN),
                    any(), any(), any());

            verify(storageService, never()).delete(any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(storageService).delete("posts/9/a.jpg");
            verify(storageService).delete("posts/9/b.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void everyBranchSetsResolvedByAndResolvedAt() {
        Report dismiss = openReport();
        when(reportRepository.findById(10L)).thenReturn(Optional.of(dismiss));
        service.act(1L, 10L, action(AdminReportActionRequest.Action.DISMISS, false));
        assertThat(dismiss.getResolvedBy()).isNotNull();
        assertThat(dismiss.getResolvedAt()).isNotNull();

        Report resolve = openReport();
        when(reportRepository.findById(11L)).thenReturn(Optional.of(resolve));
        service.act(1L, 11L, action(AdminReportActionRequest.Action.RESOLVE, false));
        assertThat(resolve.getResolvedBy()).isNotNull();
        assertThat(resolve.getResolvedAt()).isNotNull();
    }

    @Test
    void auditRowsUseTheRightActionTargetAndAdmin() {
        Report report = openReport();
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        service.act(1L, 10L, action(AdminReportActionRequest.Action.DISMISS, false));

        verify(auditService).log(eq(1L), eq(AuditAction.DISMISS_REPORT),
                eq(AuditTargetType.REPORT), eq(10L), isNull(), any());
        verify(auditService, times(1)).log(any(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------- fixtures

    private AdminReportActionRequest action(AdminReportActionRequest.Action action,
                                            boolean deletePost) {
        AdminReportActionRequest request = new AdminReportActionRequest();
        request.setAction(action);
        request.setDeletePost(deletePost);
        return request;
    }

    private Report openReport() {
        return Report.builder()
                .id(10L)
                .reason(ReportReason.SCAM)
                .status(ReportStatus.OPEN)
                .post(hiddenReportPost())
                .reporter(User.builder().id(7L).name("Reporter").build())
                .build();
    }

    private Post hiddenReportPost() {
        Post post = Post.builder()
                .id(1L)
                .title("Reported post")
                .user(User.builder().id(9L).name("Owner").build())
                .college(College.builder().id(10L).name("College A").build())
                .status(PostStatus.HIDDEN)
                .hiddenReason("REPORTS")
                .build();

        post.getImages().add(PostImage.builder()
                .id(100L).post(post).url("posts/9/a.jpg").position((short) 0).build());
        post.getImages().add(PostImage.builder()
                .id(101L).post(post).url("posts/9/b.jpg").position((short) 1).build());
        return post;
    }
}