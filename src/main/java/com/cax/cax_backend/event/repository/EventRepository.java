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

    @Query("{ 'organizationId': ?0, 'deleted': { $ne: true } }")
    List<Event> findByOrganizationId(String organizationId);

    @Query("{ 'organizationId': ?0, 'status': ?1, 'deleted': { $ne: true } }")
    List<Event> findByOrganizationIdAndStatus(String organizationId, String status);

    @Query("{ 'status': ?0, 'deleted': { $ne: true } }")
    List<Event> findByStatus(String status);

    @Query("{ 'status': ?0, 'global': true, 'deleted': { $ne: true } }")
    List<Event> findByStatusAndGlobalTrue(String status);

    @Query("{ 'collegeId': ?0, 'status': ?1, 'deleted': { $ne: true } }")
    List<Event> findByCollegeIdAndStatus(String collegeId, String status);

    /** Finds events where the given org is listed as a collaborator. */
    @Query("{ 'collaborators.organizationId': ?0, 'deleted': { $ne: true } }")
    List<Event> findByCollaboratingOrganizationId(String organizationId);
}
