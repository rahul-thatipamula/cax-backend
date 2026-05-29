package com.cax.cax_backend.event.repository;

import com.cax.cax_backend.event.model.Event;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends MongoRepository<Event, String> {
    List<Event> findByClubId(String clubId);
    List<Event> findByClubIdAndStatus(String clubId, String status);
    List<Event> findByStatus(String status);
}
