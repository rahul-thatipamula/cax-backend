package com.cax.cax_backend.notification.repository;

import com.cax.cax_backend.notification.model.CollegeWaterMilestoneState;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CollegeWaterMilestoneStateRepository extends MongoRepository<CollegeWaterMilestoneState, String> {
    Optional<CollegeWaterMilestoneState> findByCollegeId(String collegeId);
}
