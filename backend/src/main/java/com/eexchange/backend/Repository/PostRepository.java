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

}
