package com.cax.cax_backend.aicredit.repository;

import com.cax.cax_backend.aicredit.model.AICreditConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AICreditRepository extends MongoRepository<AICreditConfig, String> {
}
