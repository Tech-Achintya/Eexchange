package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.Enums.ListingType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // Main student college feed (filtered by college and APPROVED status)
    Page<Post> findByCollegeIdAndStatus(Long collegeId, PostStatus status, Pageable pageable);

    // Filter by college, status, and category
    Page<Post> findByCollegeIdAndStatusAndCategoryId(Long collegeId, PostStatus status, Long categoryId, Pageable pageable);

    // Filter by college, status, and listing type (SELL, EXCHANGE, FREE)
    Page<Post> findByCollegeIdAndStatusAndListingType(Long collegeId, PostStatus status, ListingType listingType, Pageable pageable);

    // My listings
    Page<Post> findByUserId(Long userId, Pageable pageable);

    // Admin moderation queue
    Page<Post> findByStatus(PostStatus status, Pageable pageable);

    // Account deletion: removes the user's posts. DB ON DELETE CASCADE handles
    // post_images, reports, saved_posts and interest_messages.
    @Modifying
    @Query("delete from Post p where p.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);


    // Feed. @EntityGraph fetches category + user (both to-one) without a
    // collection fetch-join, so pagination stays correct.
    @EntityGraph(attributePaths = {"category", "user"})
    @Query(value = """
            select p from Post p
            where p.college.id = :collegeId
              and p.status = :status
              and (p.expiresAt is null or p.expiresAt > :now)
              and (:categoryId is null or p.category.id = :categoryId)
              and (:listingType is null or p.listingType = :listingType)
              and (:q is null
                   or lower(p.title) like :q escape '!'
                   or lower(p.description) like :q escape '!')
              and (:minPrice is null or p.price >= :minPrice)
              and (:maxPrice is null or p.price <= :maxPrice)
            """,
            countQuery = """
            select count(p) from Post p
            where p.college.id = :collegeId
              and p.status = :status
              and (p.expiresAt is null or p.expiresAt > :now)
              and (:categoryId is null or p.category.id = :categoryId)
              and (:listingType is null or p.listingType = :listingType)
              and (:q is null
                   or lower(p.title) like :q escape '!'
                   or lower(p.description) like :q escape '!')
              and (:minPrice is null or p.price >= :minPrice)
              and (:maxPrice is null or p.price <= :maxPrice)
            """)
    Page<Post> findFeed(@Param("collegeId") Long collegeId,
                        @Param("status") PostStatus status,
                        @Param("now") Instant now,
                        @Param("categoryId") Long categoryId,
                        @Param("listingType") ListingType listingType,
                        @Param("q") String q,
                        @Param("minPrice") java.math.BigDecimal minPrice,
                        @Param("maxPrice") java.math.BigDecimal maxPrice,
                        Pageable pageable);

    // Detail by id. Visibility rules are applied in the service.
    @EntityGraph(attributePaths = {"category", "user"})
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findDetailById(@Param("id") Long id);

    // My items: everything except DELETED, optional status filter.
    @EntityGraph(attributePaths = {"category", "user"})
    @Query(value = """
            select p from Post p
            where p.user.id = :userId
              and p.status <> :deletedStatus
              and (:status is null or p.status = :status)
            """,
            countQuery = """
            select count(p) from Post p
            where p.user.id = :userId
              and p.status <> :deletedStatus
              and (:status is null or p.status = :status)
            """)
    Page<Post> findMine(@Param("userId") Long userId,
                        @Param("deletedStatus") PostStatus deletedStatus,
                        @Param("status") PostStatus status,
                        Pageable pageable);


    // Retry job: pending posts that still have attempts left, oldest first.
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("""
            select p from Post p
            where p.status = :status
              and p.moderationAttempts < :maxAttempts
              and p.updatedAt <= :now
            order by p.updatedAt asc
            """)
    List<Post> findPendingForModeration(@Param("status") PostStatus status,
                                        @Param("maxAttempts") int maxAttempts,
                                        @Param("now") Instant now,
                                        Pageable pageable);

    // Atomic auto-hide: only the first report that crosses the threshold wins.
    // Concurrent reports cannot double-apply because the status predicate fails.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Post p
               set p.status = :hiddenStatus,
                   p.hiddenReason = :hiddenReason
             where p.id = :postId
               and p.status = :approvedStatus
            """)
    int hideIfApproved(@Param("postId") Long postId,
                       @Param("hiddenStatus") PostStatus hiddenStatus,
                       @Param("hiddenReason") String hiddenReason,
                       @Param("approvedStatus") PostStatus approvedStatus);

    // Admin list: every college, every status.
    @EntityGraph(attributePaths = {"user", "college", "category"})
    @Query(value = """
            select p from Post p
            where (:status is null or p.status = :status)
              and (:collegeId is null or p.college.id = :collegeId)
              and (:q is null or lower(p.title) like :q escape '!')
            """,
            countQuery = """
            select count(p) from Post p
            where (:status is null or p.status = :status)
              and (:collegeId is null or p.college.id = :collegeId)
              and (:q is null or lower(p.title) like :q escape '!')
            """)
    Page<Post> findForAdmin(@Param("status") PostStatus status,
                            @Param("collegeId") Long collegeId,
                            @Param("q") String q,
                            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "college", "category"})
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findAdminDetailById(@Param("id") Long id);
    interface UserPostTotal {
        Long getUserId();

        long getTotal();
    }

    interface UserPostStatusCount {
        PostStatus getStatus();

        long getTotal();
    }

    @Query("""
            select p.user.id as userId, count(p) as total
            from Post p
            where p.user.id in :userIds
              and p.status <> :excludedStatus
            group by p.user.id
            """)
    List<UserPostTotal> countByUserIdsExcludingStatus(
            @Param("userIds") Collection<Long> userIds,
            @Param("excludedStatus") PostStatus excludedStatus);

    @Query("""
            select p.status as status, count(p) as total
            from Post p
            where p.user.id = :userId
            group by p.status
            """)
    List<UserPostStatusCount> countByUserIdGroupByStatus(
            @Param("userId") Long userId);

    // TEMP block: only APPROVED posts; REPORTS/ADMIN-hidden rows are untouched.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Post p
               set p.status = :hiddenStatus,
                   p.hiddenReason = :hiddenReason
             where p.user.id = :userId
               and p.status = :approvedStatus
            """)
    int hideApprovedByUserId(@Param("userId") Long userId,
                             @Param("hiddenStatus") PostStatus hiddenStatus,
                             @Param("hiddenReason") String hiddenReason,
                             @Param("approvedStatus") PostStatus approvedStatus);

    // UNBLOCK: only posts hidden because of USER_BLOCKED.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Post p
               set p.status = :approvedStatus,
                   p.hiddenReason = null
             where p.user.id = :userId
               and p.status = :hiddenStatus
               and p.hiddenReason = :hiddenReason
            """)
    int restoreUserBlockedByUserId(@Param("userId") Long userId,
                                   @Param("approvedStatus") PostStatus approvedStatus,
                                   @Param("hiddenStatus") PostStatus hiddenStatus,
                                   @Param("hiddenReason") String hiddenReason);

}
