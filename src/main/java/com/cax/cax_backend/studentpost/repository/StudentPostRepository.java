package com.cax.cax_backend.studentpost.repository;

import com.cax.cax_backend.studentpost.model.StudentPost;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.time.Instant;

@Repository
public interface StudentPostRepository extends MongoRepository<StudentPost, String> {
    List<StudentPost> findByCollegeIdOrderByCreatedAtDesc(String collegeId, Pageable pageable);
    List<StudentPost> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<StudentPost> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query("{ 'collegeId': ?0, 'disabled': { $ne: true } }")
    List<StudentPost> findActiveByCollegeId(String collegeId, Pageable pageable);

    @Query("{ 'disabled': { $ne: true } }")
    List<StudentPost> findActiveAll(Pageable pageable);

    @Query("{ 'userId': ?0, 'disabled': { $ne: true } }")
    List<StudentPost> findActiveByUserId(String userId, Pageable pageable);

    List<StudentPost> findByUserId(String userId);
    List<StudentPost> findByCommentsUserId(String userId);

    @Query(value = "{ 'collegeId': ?0, 'disabled': { $ne: true }, 'createdAt': { $gt: ?1 } }", count = true)
    long countActiveByCollegeIdSince(String collegeId, Instant timestamp);

    @Query(value = "{ 'disabled': { $ne: true }, 'createdAt': { $gt: ?0 } }", count = true)
    long countActiveSince(Instant timestamp);

    @Query(value = "{ 'disabled': { $ne: true }, 'createdAt': { $gt: ?1 }, 'userId': { $ne: ?0 } }", count = true)
    long countActiveExcludingUserSince(String userId, Instant timestamp);
}
