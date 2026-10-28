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
}
