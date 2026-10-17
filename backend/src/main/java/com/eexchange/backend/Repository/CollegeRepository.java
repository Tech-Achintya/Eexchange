package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.College;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface CollegeRepository extends JpaRepository<College, Long> {
    Optional<College> findByName(String name);
    Optional<College> findByIsOthersTrue();
    List<College> findByIsActiveTrue();
    boolean existsByName(String name);
}
