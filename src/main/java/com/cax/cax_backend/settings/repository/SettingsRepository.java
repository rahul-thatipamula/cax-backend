package com.cax.cax_backend.settings.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.cax.cax_backend.settings.model.UserSettings;

@Repository
public interface SettingsRepository extends MongoRepository<UserSettings, String> {
    Optional<UserSettings> findByUserId(String userId);
    java.util.List<UserSettings> findAllByUserId(String userId);
}
