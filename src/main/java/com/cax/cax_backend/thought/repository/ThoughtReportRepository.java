package com.cax.cax_backend.thought.repository;

import com.cax.cax_backend.thought.model.ThoughtReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThoughtReportRepository extends MongoRepository<ThoughtReport, String> {
    List<ThoughtReport> findByPostId(String postId);
    Optional<ThoughtReport> findByPostIdAndReporterUserId(String postId, String reporterUserId);
    void deleteByPostId(String postId);
    long countByPostId(String postId);
    List<ThoughtReport> findAllByOrderByReportedAtDesc();
}
