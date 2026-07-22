package com.cax.cax_backend.event.repository;

import com.cax.cax_backend.event.model.EventAnalytics;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventAnalyticsRepository extends MongoRepository<EventAnalytics, String> {

    Optional<EventAnalytics> findByEventId(String eventId);

    List<EventAnalytics> findByCollegeIdAndIsGlobalFalse(String collegeId);

    List<EventAnalytics> findByIsGlobalTrue();

    void deleteByEventId(String eventId);
}
