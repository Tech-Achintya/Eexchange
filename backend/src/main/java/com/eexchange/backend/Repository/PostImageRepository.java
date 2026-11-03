package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findByPostIdOrderByPositionAsc(Long postId);

    // All images for a page of posts in ONE query (used by /mine and detail).
    List<PostImage> findByPostIdInOrderByPostIdAscPositionAsc(Collection<Long> postIds);

    // First image (lowest position) of each post in ONE query (used by the feed).
    @Query("""
            select pi from PostImage pi
            where pi.post.id in :postIds
              and pi.position = (select min(pi2.position)
                                 from PostImage pi2
                                 where pi2.post.id = pi.post.id)
            """)
    List<PostImage> findFirstImagesByPostIds(@Param("postIds") Collection<Long> postIds);

    void deleteByPostId(Long postId);
}