package com.cax.cax_backend.reward.repository;

import com.cax.cax_backend.reward.model.Task;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends MongoRepository<Task, String> {
    List<Task> findByEnabled(boolean enabled);
}
