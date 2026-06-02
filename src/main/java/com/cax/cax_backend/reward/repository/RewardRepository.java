package com.cax.cax_backend.reward.repository;

import com.cax.cax_backend.reward.model.Reward;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RewardRepository extends MongoRepository<Reward, String> {
    List<Reward> findByEnabled(boolean enabled);
}
