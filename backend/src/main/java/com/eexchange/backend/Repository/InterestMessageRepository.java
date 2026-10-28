package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.InterestMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InterestMessageRepository extends JpaRepository<InterestMessage, Long> {
    Page<InterestMessage> findByPostIdOrderByCreatedAtDesc(Long postId, Pageable pageable);
    Page<InterestMessage> findBySenderIdOrderByCreatedAtDesc(Long senderId, Pageable pageable);
    boolean existsByPostIdAndSenderId(Long postId, Long senderId);
    Optional<InterestMessage> findByPostIdAndSenderId(Long postId, Long senderId);

    void deleteAllBySenderId(Long userId);
}
