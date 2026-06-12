package com.cax.cax_backend.settings.repository;

import com.cax.cax_backend.settings.model.SystemSetting;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemSettingRepository extends MongoRepository<SystemSetting, String> {
}
