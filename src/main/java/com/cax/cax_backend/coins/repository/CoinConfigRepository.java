package com.cax.cax_backend.coins.repository;

import com.cax.cax_backend.coins.model.CoinConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CoinConfigRepository extends MongoRepository<CoinConfig, String> {
}
