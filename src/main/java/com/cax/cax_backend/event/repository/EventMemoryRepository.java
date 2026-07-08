package com.cax.cax_backend.event.repository;

import com.cax.cax_backend.event.model.EventMemory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventMemoryRepository extends MongoRepository<EventMemory, String> {

    @Query(value = "{ 'eventId': ?0, 'hidden': ?1, 'deleted': { $ne: true } }", sort = "{ 'uploadedAt': -1 }")
    Page<EventMemory> findByEventIdAndHiddenOrderByUploadedAtDesc(String eventId, boolean hidden, Pageable pageable);

    @Query(value = "{ 'eventId': ?0, 'deleted': { $ne: true } }", sort = "{ 'uploadedAt': -1 }")
    Page<EventMemory> findByEventIdOrderByUploadedAtDesc(String eventId, Pageable pageable);

    @Query("{ 'eventId': ?0, 'deleted': { $ne: true } }")
    List<EventMemory> findAllByEventId(String eventId);
}
