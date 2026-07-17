package com.cax.cax_backend.event.repository;

import com.cax.cax_backend.event.model.EventTeam;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventTeamRepository extends MongoRepository<EventTeam, String> {
    Optional<EventTeam> findByEventIdAndTeamCode(String eventId, String teamCode);
    List<EventTeam> findByEventId(String eventId);
    boolean existsByEventIdAndTeamCode(String eventId, String teamCode);
}
