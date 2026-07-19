package com.cax.cax_backend.event.repository;

import com.cax.cax_backend.event.model.EventScore;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventScoreRepository extends MongoRepository<EventScore, String> {
    Optional<EventScore> findByEventId(String eventId);

    /** Organization's highest-scoring already-completed events, used to seed a new event's base score. */
    List<EventScore> findTop3ByOrganizationIdAndEventEndDateBeforeOrderByScoreDesc(String organizationId, Instant now);
}
