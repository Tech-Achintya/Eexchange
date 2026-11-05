package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.InterestMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InterestMessageRepository extends JpaRepository<InterestMessage, Long> {

    Page<InterestMessage> findByPostIdOrderByCreatedAtDesc(Long postId, Pageable pageable);

    Page<InterestMessage> findBySenderIdOrderByCreatedAtDesc(Long senderId, Pageable pageable);

    boolean existsByPostIdAndSenderId(Long postId, Long senderId);

    Optional<InterestMessage> findByPostIdAndSenderId(Long postId, Long senderId);

    void deleteAllBySenderId(Long userId);

    // Received: caller's own posts, excluding DELETED posts.
    // EntityGraph fetches post + sender, so no N+1 on the page itself.
    @EntityGraph(attributePaths = {"post", "sender"})
    @Query(value = """
            select m from InterestMessage m
            where m.post.user.id = :ownerId
              and m.post.status <> :deletedStatus
              and (:unreadOnly = false or m.isRead = false)
            """,
            countQuery = """
            select count(m) from InterestMessage m
            where m.post.user.id = :ownerId
              and m.post.status <> :deletedStatus
              and (:unreadOnly = false or m.isRead = false)
            """)
    Page<InterestMessage> findReceived(@Param("ownerId") Long ownerId,
                                       @Param("deletedStatus") PostStatus deletedStatus,
                                       @Param("unreadOnly") boolean unreadOnly,
                                       Pageable pageable);

    // Sent: only the caller's messages; post is fetched for title/status.
    @EntityGraph(attributePaths = {"post"})
    @Query(value = """
            select m from InterestMessage m
            where m.sender.id = :senderId
            """,
            countQuery = """
            select count(m) from InterestMessage m
            where m.sender.id = :senderId
            """)
    Page<InterestMessage> findSent(@Param("senderId") Long senderId, Pageable pageable);

    @Query("""
            select count(m) from InterestMessage m
            where m.post.user.id = :ownerId
              and m.post.status <> :deletedStatus
              and m.isRead = false
            """)
    long countUnreadForOwner(@Param("ownerId") Long ownerId,
                             @Param("deletedStatus") PostStatus deletedStatus);

    // Mark-read: fetch post + owner in one query.
    @EntityGraph(attributePaths = {"post", "post.user"})
    @Query("select m from InterestMessage m where m.id = :id")
    Optional<InterestMessage> findDetailById(@Param("id") Long id);
}