package com.cax.cax_backend.notification.repository;

import com.cax.cax_backend.notification.model.UserWaterPeerNudgeState;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserWaterPeerNudgeStateRepository extends MongoRepository<UserWaterPeerNudgeState, String> {
    Optional<UserWaterPeerNudgeState> findByUserId(String userId);
}
