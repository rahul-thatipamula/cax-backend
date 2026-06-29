package com.cax.cax_backend.coins.repository;

import com.cax.cax_backend.coins.model.AdRewardConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AdRewardConfigRepository extends MongoRepository<AdRewardConfig, String> {

    Optional<AdRewardConfig> findByAdTypeAndActiveTrue(String adType);

    List<AdRewardConfig> findAllByOrderByCreatedAtDesc();
}
