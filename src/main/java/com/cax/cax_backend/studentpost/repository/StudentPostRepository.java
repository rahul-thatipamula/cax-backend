package com.cax.cax_backend.studentpost.repository;

import com.cax.cax_backend.studentpost.model.StudentPost;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.List;

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
}
