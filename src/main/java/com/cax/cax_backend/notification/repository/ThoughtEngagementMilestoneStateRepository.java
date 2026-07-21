package com.cax.cax_backend.notification.repository;

import com.cax.cax_backend.notification.model.ThoughtEngagementMilestoneState;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ThoughtEngagementMilestoneStateRepository extends MongoRepository<ThoughtEngagementMilestoneState, String> {
    Optional<ThoughtEngagementMilestoneState> findByThoughtId(String thoughtId);
}
