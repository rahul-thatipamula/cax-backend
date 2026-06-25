package com.cax.cax_backend.thought.repository;

import com.cax.cax_backend.thought.model.ThoughtEngagementScore;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThoughtEngagementScoreRepository extends MongoRepository<ThoughtEngagementScore, String> {

    Optional<ThoughtEngagementScore> findByThoughtId(String thoughtId);

    List<ThoughtEngagementScore> findTop10ByOrderByEngagementScoreDesc();

    List<ThoughtEngagementScore> findTop10ByCollegeIdOrderByEngagementScoreDesc(String collegeId);

    void deleteByThoughtId(String thoughtId);

    /**
     * Finds all scores that have crossed the trending threshold but whose
     * author has NOT yet been notified (trendingNotifiedAt is null).
     * Used by the scheduler to send one-time trending notifications.
     */
    List<ThoughtEngagementScore> findByEngagementScoreGreaterThanEqualAndTrendingNotifiedAtIsNull(double threshold);
}
