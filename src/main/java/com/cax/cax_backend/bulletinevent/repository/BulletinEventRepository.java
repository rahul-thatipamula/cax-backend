package com.cax.cax_backend.bulletinevent.repository;

import com.cax.cax_backend.bulletinevent.model.BulletinEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BulletinEventRepository extends MongoRepository<BulletinEvent, String> {

    // Student-facing: active, not deleted, global OR matching collegeId
    @Query("{ 'deleted': { $ne: true }, 'active': true, '$or': [ { 'global': true }, { 'collegeIds': ?0 } ] }")
    List<BulletinEvent> findActiveByGlobalOrCollegeId(String collegeId);

    // Admin: all non-deleted (including inactive) — ordered by createdAt desc
    @Query("{ 'deleted': { $ne: true } }")
    List<BulletinEvent> findAllNotDeleted();
}

