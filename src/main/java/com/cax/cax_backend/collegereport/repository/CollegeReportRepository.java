package com.cax.cax_backend.collegereport.repository;

import com.cax.cax_backend.collegereport.model.CollegeReport;
import com.cax.cax_backend.collegereport.model.CollegeReport.ReportStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CollegeReportRepository extends MongoRepository<CollegeReport, String> {
    List<CollegeReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);
    List<CollegeReport> findAllByOrderByCreatedAtDesc();
    long countByStatus(ReportStatus status);
    boolean existsByEmailAndStatus(String email, ReportStatus status);
}
