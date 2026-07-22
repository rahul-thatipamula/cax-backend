package com.cax.cax_backend.notification.repository;

import com.cax.cax_backend.notification.model.OrganizationPostMilestoneState;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OrganizationPostMilestoneStateRepository extends MongoRepository<OrganizationPostMilestoneState, String> {
    Optional<OrganizationPostMilestoneState> findByPostId(String postId);
}
