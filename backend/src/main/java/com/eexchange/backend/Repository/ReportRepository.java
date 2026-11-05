package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Entity.Enums.ReportStatus;
import com.eexchange.backend.Entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    Page<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    boolean existsByPostIdAndReporterId(Long postId, Long reporterId);

    long countByPostIdAndStatus(Long postId, ReportStatus status);

    // Admin queue: oldest first, with the post and reporter ready for the DTO.
    @EntityGraph(attributePaths = {"post", "reporter"})
    Page<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter"})
    List<Report> findByPostIdOrderByCreatedAtAsc(Long postId);

    // One query for open-report counts across a whole page (avoids N+1).
    interface PostOpenCount {
        Long getPostId();

        long getTotal();
    }

    @Query("""
            select r.post.id as postId, count(r) as total
            from Report r
            where r.post.id in :postIds
              and r.status = :status
            group by r.post.id
            """)
    List<PostOpenCount> countByPostIdsAndStatus(
            @Param("postIds") Collection<Long> postIds,
            @Param("status") ReportStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Report r
               set r.status = :resolvedStatus,
                   r.resolvedBy = :admin,
                   r.resolvedAt = :resolvedAt,
                   r.resolutionNote = :note
             where r.post.id = :postId
               and r.status = :openStatus
            """)
    int resolveAllOpenForPost(@Param("postId") Long postId,
                              @Param("resolvedStatus") ReportStatus resolvedStatus,
                              @Param("openStatus") ReportStatus openStatus,
                              @Param("admin") Admin admin,
                              @Param("resolvedAt") Instant resolvedAt,
                              @Param("note") String note);

    Optional<Report> findByIdAndStatus(Long id, ReportStatus status);


    @Query("""
            select count(r) from Report r
            where r.post.user.id = :userId
              and r.status = :status
            """)
    long countByPostOwnerIdAndStatus(@Param("userId") Long userId,
                                     @Param("status") ReportStatus status);

}