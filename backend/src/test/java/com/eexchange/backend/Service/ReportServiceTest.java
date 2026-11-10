package com.eexchange.backend.Service;

import com.eexchange.backend.Service.Cache.CollegeFeedVersionService;

import com.eexchange.backend.DTO.Request.ReportCreateRequest;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Enums.ReportReason;
import com.eexchange.backend.Entity.Enums.ReportStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.Report;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.ReportRepository;
import com.eexchange.backend.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private UserRepository userRepository;
    private PostRepository postRepository;
    private ReportRepository reportRepository;
    private ReportService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        postRepository = mock(PostRepository.class);
        reportRepository = mock(ReportRepository.class);

        service = new ReportService(userRepository, postRepository, reportRepository,
                mock(CollegeFeedVersionService.class));
        ReflectionTestUtils.setField(service, "autoHideThreshold", 3);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, 10L)));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void ownPostCannotBeReported() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 7L, 10L, PostStatus.APPROVED)));

        AppException ex = assertThrows(AppException.class,
                () -> service.reportPost(7L, 1L, request("nice")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateReportReturns409() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 99L, 10L, PostStatus.APPROVED)));
        when(reportRepository.existsByPostIdAndReporterId(1L, 7L)).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> service.reportPost(7L, 1L, request("again")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void uniqueConstraintRaceReturns409() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 99L, 10L, PostStatus.APPROVED)));
        when(reportRepository.existsByPostIdAndReporterId(1L, 7L)).thenReturn(false);
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        AppException ex = assertThrows(AppException.class,
                () -> service.reportPost(7L, 1L, request("race")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void postFromAnotherCollegeOrNotApprovedIs404() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 99L, 20L, PostStatus.APPROVED)));
        assertThat(assertThrows(AppException.class,
                () -> service.reportPost(7L, 1L, request("x"))).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        when(postRepository.findDetailById(2L))
                .thenReturn(Optional.of(post(2L, 99L, 10L, PostStatus.HIDDEN)));
        assertThat(assertThrows(AppException.class,
                () -> service.reportPost(7L, 2L, request("x"))).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        when(postRepository.findDetailById(3L))
                .thenReturn(Optional.of(post(3L, 99L, 10L, PostStatus.MANUAL_REVIEW)));
        assertThat(assertThrows(AppException.class,
                () -> service.reportPost(7L, 3L, request("x"))).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void twoReportsDoNotHide() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 99L, 10L, PostStatus.APPROVED)));
        when(reportRepository.countByPostIdAndStatus(1L, ReportStatus.OPEN))
                .thenReturn(2L);

        service.reportPost(7L, 1L, request("second"));

        verify(postRepository, never()).hideIfApproved(any(), any(), any(), any());
    }

    @Test
    void thirdReportHidesWithOneAtomicUpdate() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 99L, 10L, PostStatus.APPROVED)));
        when(reportRepository.countByPostIdAndStatus(1L, ReportStatus.OPEN))
                .thenReturn(3L);

        service.reportPost(7L, 1L, request("third"));

        verify(postRepository).hideIfApproved(
                1L, PostStatus.HIDDEN, "REPORTS", PostStatus.APPROVED);
    }

    @Test
    void commentLongerThan500IsRejected() {
        when(postRepository.findDetailById(1L))
                .thenReturn(Optional.of(post(1L, 99L, 10L, PostStatus.APPROVED)));

        AppException ex = assertThrows(AppException.class,
                () -> service.reportPost(7L, 1L, request("x".repeat(501))));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    // ---------------------------------------------------------- fixtures

    private ReportCreateRequest request(String comment) {
        ReportCreateRequest request = new ReportCreateRequest();
        request.setReason(ReportReason.SCAM);
        request.setComment(comment);
        return request;
    }

    private User user(Long id, Long collegeId) {
        return User.builder()
                .id(id)
                .name("User " + id)
                .college(College.builder().id(collegeId).name("College " + collegeId).build())
                .build();
    }

    private Post post(Long id, Long ownerId, Long collegeId, PostStatus status) {
        return Post.builder()
                .id(id)
                .title("Post " + id)
                .college(College.builder().id(collegeId).name("College " + collegeId).build())
                .user(User.builder().id(ownerId).name("Owner").build())
                .status(status)
                .build();
    }
}