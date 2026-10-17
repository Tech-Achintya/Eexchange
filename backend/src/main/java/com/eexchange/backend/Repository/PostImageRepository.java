package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    List<PostImage> findByPostIdOrderByPositionAsc(Long postId);
    void deleteByPostId(Long postId);
}
