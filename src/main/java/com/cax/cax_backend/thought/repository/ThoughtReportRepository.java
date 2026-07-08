package com.cax.cax_backend.thought.repository;

import com.cax.cax_backend.thought.model.ThoughtReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThoughtReportRepository extends MongoRepository<ThoughtReport, String> {

    @Query("{ 'postId': ?0, 'deleted': { $ne: true } }")
    List<ThoughtReport> findByPostId(String postId);

    @Query("{ 'postId': ?0, 'reporterUserId': ?1, 'deleted': { $ne: true } }")
    Optional<ThoughtReport> findByPostIdAndReporterUserId(String postId, String reporterUserId);

    @Query(value = "{ 'postId': ?0, 'deleted': { $ne: true } }", count = true)
    long countByPostId(String postId);

    @Query(value = "{ 'deleted': { $ne: true } }", sort = "{ 'reportedAt': -1 }")
    List<ThoughtReport> findAllByOrderByReportedAtDesc();
}
