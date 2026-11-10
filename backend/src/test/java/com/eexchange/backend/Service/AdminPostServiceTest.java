package com.eexchange.backend.Service;

import com.eexchange.backend.Service.Cache.CollegeFeedVersionService;

import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.AdminRole;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.ReportStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.AdminRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPostServiceTest {

    private PostRepository postRepository;
    private ReportRepository reportRepository;
    private AuditService auditService;
    private StorageService storageService;
    private AdminPostService service;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        reportRepository = mock(ReportRepository.class);
        AdminRepository adminRepository = mock(AdminRepository.class);
        auditService = mock(AuditService.class);
        storageService = mock(StorageService.class);

        when(adminRepository.getReferenceById(1L))
                .thenReturn(Admin.builder().id(1L).role(AdminRole.MODERATOR).build());

        service = new AdminPostService(postRepository, reportRepository,
                adminRepository, auditService, new PostMediaCleanup(storageService),
                storageService, mock(CollegeFeedVersionService.class));

        when(storageService.publicUrl(any()))
                .thenAnswer(i -> "https://cdn.test/" + i.getArgument(0));
    }

    @Test
    void deleteRequiresReason() {
        AppException ex = assertThrows(AppException.class,
                () -> service.delete(1L, 1L, "   "));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(postRepository, never()).findAdminDetailById(any());
    }

    @Test
    void doubleDeleteIs400() {
        Post post = post(PostStatus.DELETED);
        when(postRepository.findAdminDetailById(1L)).thenReturn(Optional.of(post));

        AppException ex = assertThrows(AppException.class,
                () -> service.delete(1L, 1L, "duplicate"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteResolvesOpenReportsAndAudits() {
        Post post = post(PostStatus.APPROVED);
        when(postRepository.findAdminDetailById(1L)).thenReturn(Optional.of(post));
        when(reportRepository.resolveAllOpenForPost(
                eq(1L), eq(ReportStatus.RESOLVED), eq(ReportStatus.OPEN),
                any(), any(), any())).thenReturn(3);

        service.delete(1L, 1L, "policy violation");

        assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
        verify(reportRepository).resolveAllOpenForPost(
                eq(1L), eq(ReportStatus.RESOLVED), eq(ReportStatus.OPEN),
                any(), any(), any());
        verify(auditService).log(eq(1L), eq(AuditAction.DELETE_POST),
                eq(AuditTargetType.POST), eq(1L), eq("policy violation"), any());
    }

    @Test
    void listForwardsFiltersAndNormalizesPaging() {
        when(postRepository.findForAdmin(
                eq(PostStatus.HIDDEN), eq(10L), eq("%lamp%"), any(Pageable.class)))
                .thenReturn(page());

        service.list(PostStatus.HIDDEN, "Lamp", 10L, -1, 999);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findForAdmin(
                eq(PostStatus.HIDDEN), eq(10L), eq("%lamp%"), pageable.capture());

        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        verify(reportRepository, never()).countByPostIdsAndStatus(any(), any());
    }

    @Test
    void listEscapesWildcards() {
        when(postRepository.findForAdmin(isNull(), isNull(), eq("%50!%!_off%"),
                any(Pageable.class))).thenReturn(page());

        service.list(null, "50%_off", null, 0, 20);

        verify(postRepository).findForAdmin(
                isNull(), isNull(), eq("%50!%!_off%"), any(Pageable.class));
    }

    // ---------------------------------------------------------- fixtures

    private Post post(PostStatus status) {
        return Post.builder()
                .id(1L)
                .title("Lamp")
                .college(College.builder().id(10L).name("College A").build())
                .user(User.builder().id(9L).name("Owner").build())
                .status(status)
                .build();
    }

    private Page<Post> page(Post... posts) {
        return new PageImpl<>(List.of(posts));
    }
}