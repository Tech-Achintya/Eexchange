package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.CollegeEmailDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface CollegeEmailDomainRepository extends JpaRepository<CollegeEmailDomain, Long> {
    Optional<CollegeEmailDomain> findByDomain(String domain);
    boolean existsByDomain(String domain);
    List<CollegeEmailDomain> findByCollegeId(Long collegeId);
}
