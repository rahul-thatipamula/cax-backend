package com.cax.cax_backend.event.repository;

import com.cax.cax_backend.event.model.EventParticipant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventParticipantRepository extends MongoRepository<EventParticipant, String> {
    List<EventParticipant> findByEventId(String eventId);
    Optional<EventParticipant> findByEventIdAndUserId(String eventId, String userId);
    List<EventParticipant> findByEventIdAndStatus(String eventId, String status);
    boolean existsByEventIdAndUserId(String eventId, String userId);
    Optional<EventParticipant> findByEventIdAndTicketCode(String eventId, String ticketCode);
    Optional<EventParticipant> findByTicketCode(String ticketCode);
}
