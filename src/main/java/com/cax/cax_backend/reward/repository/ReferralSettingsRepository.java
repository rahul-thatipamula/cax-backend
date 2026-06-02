package com.cax.cax_backend.reward.repository;

import com.cax.cax_backend.reward.model.ReferralSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReferralSettingsRepository extends MongoRepository<ReferralSettings, String> {
}
