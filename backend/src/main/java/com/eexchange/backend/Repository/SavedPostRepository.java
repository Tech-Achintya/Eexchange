package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.SavedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedPostRepository extends JpaRepository<SavedPost, Long> {
    boolean existsByUserIdAndPostId(Long userId, Long postId);
    Optional<SavedPost> findByUserIdAndPostId(Long userId, Long postId);
    List<SavedPost> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteByUserIdAndPostId(Long userId, Long postId);
}
