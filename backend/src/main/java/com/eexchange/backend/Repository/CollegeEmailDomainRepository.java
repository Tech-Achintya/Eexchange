package com.eexchange.backend.Repository;

import com.eexchange.backend.DTO.CachedCollegeDomain;
import com.eexchange.backend.Entity.CollegeEmailDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollegeEmailDomainRepository extends JpaRepository<CollegeEmailDomain, Long> {
    Optional<CollegeEmailDomain> findByDomain(String domain);
    boolean existsByDomain(String domain);
    List<CollegeEmailDomain> findByCollegeId(Long collegeId);

    @Query("""
        SELECT new com.eexchange.backend.DTO.CachedCollegeDomain(
            d.domain,
            c.id,
            c.name,
            c.isActive
        )
        FROM CollegeEmailDomain d
        JOIN d.college c
    """)
    List<CachedCollegeDomain> findAllCachedDomains();
}
