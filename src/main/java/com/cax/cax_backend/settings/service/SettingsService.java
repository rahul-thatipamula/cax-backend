package com.cax.cax_backend.settings.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.cax.cax_backend.settings.dto.SettingsDTO;
import com.cax.cax_backend.settings.model.UserSettings;
import com.cax.cax_backend.settings.repository.SettingsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settingsRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Fetch user settings, creating defaults if they don't exist.
     *
     * Uses an atomic upsert rather than find-then-save: two concurrent calls
     * for the same brand-new user used to both see an empty list and both
     * insert a default doc, leaving duplicate userId rows that later crashed
     * every plain findByUserId() lookup with "returned non unique result".
     */
    public UserSettings getSettings(String userId) {
        List<UserSettings> settingsList = settingsRepository.findAllByUserId(userId);
        if (!settingsList.isEmpty()) {
            UserSettings primarySettings = settingsList.get(0);
            if (settingsList.size() > 1) {
                // Soft-delete leftover duplicates to clean up the DB
                for (int i = 1; i < settingsList.size(); i++) {
                    try {
                        UserSettings duplicate = settingsList.get(i);
                        duplicate.setDeleted(true);
                        duplicate.setDeletedAt(Instant.now());
                        settingsRepository.save(duplicate);
                    } catch (Exception ignored) {}
                }
            }
            return primarySettings;
        }

        Query query = new Query(Criteria.where("userId").is(userId).and("deleted").ne(true));
        Update update = new Update()
                .setOnInsert("userId", userId)
                .setOnInsert("notificationsEnabled", true)
                .setOnInsert("emailNotificationsEnabled", true)
                .setOnInsert("pushNotificationsEnabled", true)
                .setOnInsert("darkMode", false)
                .setOnInsert("language", "English")
                .setOnInsert("showOnlineStatus", true)
                .setOnInsert("allowMessages", true)
                .setOnInsert("deleted", false)
                .setOnInsert("createdAt", Instant.now())
                .setOnInsert("updatedAt", Instant.now());
        return mongoTemplate.findAndModify(
                query, update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                UserSettings.class);
    }

    /**
     * Get settings as a map (compatible with frontend expectations)
     */
    public Map<String, Object> getSettingsAsMap(String userId) {
        UserSettings settings = getSettings(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("notificationsEnabled", settings.isNotificationsEnabled());
        result.put("darkMode", settings.isDarkMode());
        result.put("language", settings.getLanguage());
        result.put("emailNotificationsEnabled", settings.isEmailNotificationsEnabled());
        result.put("pushNotificationsEnabled", settings.isPushNotificationsEnabled());
        result.put("showOnlineStatus", settings.isShowOnlineStatus());
        result.put("allowMessages", settings.isAllowMessages());
        return result;
    }

    /**
     * Update user settings (partial update)
     */
    public UserSettings updateSettings(String userId, SettingsDTO settingsDTO) {
        UserSettings settings = getSettings(userId);

        // Update only provided fields
        if (settingsDTO.getNotificationsEnabled() != null) {
            settings.setNotificationsEnabled(settingsDTO.getNotificationsEnabled());
        }
        if (settingsDTO.getEmailNotificationsEnabled() != null) {
            settings.setEmailNotificationsEnabled(settingsDTO.getEmailNotificationsEnabled());
        }
        if (settingsDTO.getPushNotificationsEnabled() != null) {
            settings.setPushNotificationsEnabled(settingsDTO.getPushNotificationsEnabled());
        }
        if (settingsDTO.getDarkMode() != null) {
            settings.setDarkMode(settingsDTO.getDarkMode());
        }
        if (settingsDTO.getLanguage() != null) {
            settings.setLanguage(settingsDTO.getLanguage());
        }
        if (settingsDTO.getShowOnlineStatus() != null) {
            settings.setShowOnlineStatus(settingsDTO.getShowOnlineStatus());
        }
        if (settingsDTO.getAllowMessages() != null) {
            settings.setAllowMessages(settingsDTO.getAllowMessages());
        }

        settings.setUpdatedAt(Instant.now());
        return settingsRepository.save(settings);
    }

    /**
     * Update notification preference
     */
    public UserSettings updateNotificationPreference(String userId, boolean enabled) {
        UserSettings settings = getSettings(userId);
        settings.setNotificationsEnabled(enabled);
        settings.setUpdatedAt(Instant.now());
        return settingsRepository.save(settings);
    }

    /**
     * Update theme preference
     */
    public UserSettings updateThemePreference(String userId, boolean darkMode) {
        UserSettings settings = getSettings(userId);
        settings.setDarkMode(darkMode);
        settings.setUpdatedAt(Instant.now());
        return settingsRepository.save(settings);
    }

    /**
     * Update language preference
     */
    public UserSettings updateLanguagePreference(String userId, String language) {
        UserSettings settings = getSettings(userId);
        settings.setLanguage(language);
        settings.setUpdatedAt(Instant.now());
        return settingsRepository.save(settings);
    }
}
