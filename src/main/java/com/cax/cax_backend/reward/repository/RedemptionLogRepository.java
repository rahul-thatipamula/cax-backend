package com.cax.cax_backend.reward.repository;

import com.cax.cax_backend.reward.model.RedemptionLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RedemptionLogRepository extends MongoRepository<RedemptionLog, String> {
    List<RedemptionLog> findByUserId(String userId);
}
