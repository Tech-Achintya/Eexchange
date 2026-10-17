package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.Report;
import com.eexchange.backend.Entity.Enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    Page<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);
    boolean existsByPostIdAndReporterId(Long postId, Long reporterId);
    long countByPostIdAndStatus(Long postId, ReportStatus status);
}
