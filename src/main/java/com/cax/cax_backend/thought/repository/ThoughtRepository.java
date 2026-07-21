package com.cax.cax_backend.thought.repository;

import com.cax.cax_backend.thought.model.Thought;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ThoughtRepository extends MongoRepository<Thought, String> {

    @Query(value = "{ 'collegeId': ?0, 'deleted': { $ne: true } }", sort = "{ 'createdAt': -1 }")
    List<Thought> findByCollegeIdOrderByCreatedAtDesc(String collegeId, Pageable pageable);

    @Query(value = "{ 'deleted': { $ne: true } }", sort = "{ 'createdAt': -1 }")
    List<Thought> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = "{ 'userId': ?0, 'deleted': { $ne: true } }", sort = "{ 'createdAt': -1 }")
    List<Thought> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query("{ 'collegeId': ?0, 'disabled': { $ne: true }, 'deleted': { $ne: true } }")
    List<Thought> findActiveByCollegeId(String collegeId, Pageable pageable);

    @Query("{ 'disabled': { $ne: true }, 'deleted': { $ne: true } }")
    List<Thought> findActiveAll(Pageable pageable);

    @Query("{ 'userId': ?0, 'disabled': { $ne: true }, 'deleted': { $ne: true } }")
    List<Thought> findActiveByUserId(String userId, Pageable pageable);

    // Internal use only (denormalized-field maintenance in UserProfileUpdatedListener) —
    // intentionally not filtered by deleted/disabled since it must touch every matching doc.
    List<Thought> findByUserId(String userId);
    List<Thought> findByCommentsUserId(String userId);

    @Query(value = "{ 'collegeId': ?0, 'disabled': { $ne: true }, 'deleted': { $ne: true }, 'createdAt': { $gt: ?1 } }", count = true)
    long countActiveByCollegeIdSince(String collegeId, Instant timestamp);

    @Query(value = "{ 'disabled': { $ne: true }, 'deleted': { $ne: true }, 'createdAt': { $gt: ?0 } }", count = true)
    long countActiveSince(Instant timestamp);

    @Query(value = "{ 'disabled': { $ne: true }, 'deleted': { $ne: true }, 'createdAt': { $gt: ?1 }, 'userId': { $ne: ?0 } }", count = true)
    long countActiveExcludingUserSince(String userId, Instant timestamp);

    @Query(value = "{ 'userId': ?0, 'disabled': { $ne: true }, 'deleted': { $ne: true }, 'createdAt': { $gt: ?1 } }", count = true)
    long countActiveByUserSince(String userId, Instant since);

    @Query(value = "{ 'userId': ?0, 'deleted': { $ne: true } }", count = true)
    long countActiveByUserId(String userId);
}
