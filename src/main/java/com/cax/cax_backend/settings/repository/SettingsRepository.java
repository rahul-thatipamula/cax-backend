package com.cax.cax_backend.settings.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.cax.cax_backend.settings.model.UserSettings;

@Repository
public interface SettingsRepository extends MongoRepository<UserSettings, String> {

    @Query("{ 'userId': ?0, 'deleted': { $ne: true } }")
    java.util.List<UserSettings> findAllByUserId(String userId);
}
