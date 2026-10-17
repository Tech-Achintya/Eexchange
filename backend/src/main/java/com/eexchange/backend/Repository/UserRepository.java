package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Entity.Enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Page<User> findByCollegeId(Long collegeId, Pageable pageable);
    Page<User> findByStatus(UserStatus status, Pageable pageable);
}
