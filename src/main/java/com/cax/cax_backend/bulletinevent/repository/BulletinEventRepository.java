package com.cax.cax_backend.bulletinevent.repository;

import com.cax.cax_backend.bulletinevent.model.BulletinEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface BulletinEventRepository extends MongoRepository<BulletinEvent, String> {

    // Student-facing: active, not deleted, global OR matching collegeId
    @Query("{ 'deleted': { $ne: true }, 'active': true, '$or': [ { 'global': true }, { 'collegeIds': ?0 } ] }")
    List<BulletinEvent> findActiveByGlobalOrCollegeId(String collegeId);

    // Admin: all non-deleted (including inactive) — ordered by createdAt desc
    @Query("{ 'deleted': { $ne: true } }")
    List<BulletinEvent> findAllNotDeleted();

    /** Active bulletins starting after now, scoped to global or the given college. */
    @Query("{ 'deleted': { $ne: true }, 'active': true, 'eventStartDate': { '$gt': ?1 }, '$or': [ { 'global': true }, { 'collegeIds': ?0 } ] }")
    List<BulletinEvent> findUpcoming(String collegeId, Instant now);

    /** Active bulletins that have started but not yet ended, scoped to global or the given college. */
    @Query("{ 'deleted': { $ne: true }, 'active': true, 'eventStartDate': { '$lte': ?1 }, 'eventEndDate': { '$gt': ?1 }, '$or': [ { 'global': true }, { 'collegeIds': ?0 } ] }")
    List<BulletinEvent> findOngoing(String collegeId, Instant now);

    /** Active bulletins that have already ended, scoped to global or the given college. */
    @Query("{ 'deleted': { $ne: true }, 'active': true, 'eventEndDate': { '$lte': ?1 }, '$or': [ { 'global': true }, { 'collegeIds': ?0 } ] }")
    List<BulletinEvent> findCompleted(String collegeId, Instant now);

    /** Public-facing (caxone.in/postEvent): most recent live global bulletins, no auth. */
    @Query("{ 'deleted': { $ne: true }, 'active': true, 'global': true }")
    List<BulletinEvent> findActiveGlobal();
}

