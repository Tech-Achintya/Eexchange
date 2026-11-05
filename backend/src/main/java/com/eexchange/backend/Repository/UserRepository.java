package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Entity.Enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Page<User> findByCollegeId(Long collegeId, Pageable pageable);
    Page<User> findByStatus(UserStatus status, Pageable pageable);
    @EntityGraph(attributePaths = {"college"})
    @Query(value = """
            select u from User u
            where u.deletedAt is null
              and (:status is null or u.status = :status)
              and (:collegeId is null or u.college.id = :collegeId)
              and (:q is null
                   or lower(u.email) like :q escape '!'
                   or lower(u.name) like :q escape '!')
            """,
            countQuery = """
            select count(u) from User u
            where u.deletedAt is null
              and (:status is null or u.status = :status)
              and (:collegeId is null or u.college.id = :collegeId)
              and (:q is null
                   or lower(u.email) like :q escape '!'
                   or lower(u.name) like :q escape '!')
            """)
    Page<User> findForAdmin(@Param("q") String q,
                            @Param("status") UserStatus status,
                            @Param("collegeId") Long collegeId,
                            Pageable pageable);

    @Query("""
            select u from User u
            where u.status = :status
              and u.blockedUntil is not null
              and u.blockedUntil <= :now
            order by u.blockedUntil asc
            """)
    List<User> findExpiredTempBlocks(@Param("status") UserStatus status,
                                     @Param("now") Instant now,
                                     Pageable pageable);

    // Conditional expiry: if an admin already acted, this changes 0 rows.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u
               set u.status = :activeStatus,
                   u.blockedUntil = null,
                   u.blockReason = null,
                   u.blockedAt = null
             where u.id = :userId
               and u.status = :tempStatus
               and u.blockedUntil is not null
               and u.blockedUntil <= :now
            """)
    int unblockIfExpired(@Param("userId") Long userId,
                         @Param("activeStatus") UserStatus activeStatus,
                         @Param("tempStatus") UserStatus tempStatus,
                         @Param("now") Instant now);

}
