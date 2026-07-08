package com.cax.cax_backend.college.repository;

import com.cax.cax_backend.college.model.College;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CollegeRepository extends MongoRepository<College, String> {

    @Query("{ 'isActive': true, 'deleted': { $ne: true } }")
    List<College> findByIsActiveTrue();

    @Query("{ 'collegeName': { $regex: ?0, $options: 'i' }, 'deleted': { $ne: true } }")
    List<College> findByCollegeNameContainingIgnoreCase(String name);

    boolean existsByCollegeCodeIgnoreCase(String collegeCode);
    boolean existsByCollegeNameIgnoreCase(String collegeName);
    Optional<College> findByCollegeCode(String collegeCode);

    @Query("{ 'deleted': { $ne: true } }")
    List<College> findAllActive();
}
