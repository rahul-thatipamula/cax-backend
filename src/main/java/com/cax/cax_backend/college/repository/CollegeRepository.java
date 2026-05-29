package com.cax.cax_backend.college.repository;

import com.cax.cax_backend.college.model.College;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface CollegeRepository extends MongoRepository<College, String> {
    List<College> findByIsActiveTrue();
    List<College> findByCollegeNameContainingIgnoreCase(String name);
}
