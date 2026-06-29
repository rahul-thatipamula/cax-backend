package com.cax.cax_backend.event.repository;

import com.cax.cax_backend.event.model.Event;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends MongoRepository<Event, String> {
    Optional<Event> findByIdempotencyKey(String idempotencyKey);

    List<Event> findByOrganizationId(String organizationId);
    List<Event> findByOrganizationIdAndStatus(String organizationId, String status);
    List<Event> findByStatus(String status);
    List<Event> findByStatusAndGlobalTrue(String status);
    List<Event> findByCollegeIdAndStatus(String collegeId, String status);

    /** Finds events where the given org is listed as a collaborator. */
    @Query("{ 'collaborators.organizationId': ?0 }")
    List<Event> findByCollaboratingOrganizationId(String organizationId);
}
