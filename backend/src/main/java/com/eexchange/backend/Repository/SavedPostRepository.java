package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.SavedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedPostRepository extends JpaRepository<SavedPost, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    Optional<SavedPost> findByUserIdAndPostId(Long userId, Long postId);

    List<SavedPost> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserIdAndPostId(Long userId, Long postId);

    @Query("""
            select sp.post.id
            from SavedPost sp
            where sp.user.id = :userId
            order by sp.createdAt desc
            """)
    List<Long> findPostIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO saved_posts (user_id, post_id, created_at)
            VALUES (:userId, :postId, now())
            ON CONFLICT ON CONSTRAINT uq_saved_posts_user_post DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("postId") Long postId);
}