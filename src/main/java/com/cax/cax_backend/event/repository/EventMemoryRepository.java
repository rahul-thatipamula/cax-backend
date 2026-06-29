package com.cax.cax_backend.event.repository;

import com.cax.cax_backend.event.model.EventMemory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventMemoryRepository extends MongoRepository<EventMemory, String> {
    Page<EventMemory> findByEventIdAndHiddenOrderByUploadedAtDesc(String eventId, boolean hidden, Pageable pageable);
    Page<EventMemory> findByEventIdOrderByUploadedAtDesc(String eventId, Pageable pageable);
    void deleteByEventId(String eventId);
}
