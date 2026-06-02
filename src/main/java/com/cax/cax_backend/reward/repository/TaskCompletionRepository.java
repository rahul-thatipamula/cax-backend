package com.cax.cax_backend.reward.repository;

import com.cax.cax_backend.reward.model.TaskCompletion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskCompletionRepository extends MongoRepository<TaskCompletion, String> {
    List<TaskCompletion> findByUserId(String userId);
    Optional<TaskCompletion> findByUserIdAndTaskId(String userId, String taskId);
    long countByUserId(String userId);
}
