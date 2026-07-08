package com.cax.cax_backend.collegereport.repository;

import com.cax.cax_backend.collegereport.model.CollegeReport;
import com.cax.cax_backend.collegereport.model.CollegeReport.ReportStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface CollegeReportRepository extends MongoRepository<CollegeReport, String> {

    @Query(value = "{ 'status': ?0, 'deleted': { $ne: true } }", sort = "{ 'createdAt': -1 }")
    List<CollegeReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    @Query(value = "{ 'deleted': { $ne: true } }", sort = "{ 'createdAt': -1 }")
    List<CollegeReport> findAllByOrderByCreatedAtDesc();

    @Query(value = "{ 'status': ?0, 'deleted': { $ne: true } }", count = true)
    long countByStatus(ReportStatus status);

    boolean existsByEmailAndStatus(String email, ReportStatus status);
}
