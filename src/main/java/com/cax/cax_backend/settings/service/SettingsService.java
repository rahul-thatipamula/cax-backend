package com.cax.cax_backend.settings.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cax.cax_backend.settings.dto.SettingsDTO;
import com.cax.cax_backend.settings.model.UserSettings;
import com.cax.cax_backend.settings.repository.SettingsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settingsRepository;

    /**
     * Fetch user settings, creating defaults if they don't exist
     */
    public UserSettings getSettings(String userId) {
        return settingsRepository.findByUserId(userId).orElseGet(() -> {
            // Create default settings if they don't exist
            UserSettings defaultSettings = UserSettings.builder()
                    .userId(userId)
                    .notificationsEnabled(true)
                    .emailNotificationsEnabled(true)
                    .pushNotificationsEnabled(true)
                    .darkMode(false)
                    .language("English")
                    .soundEnabled(true)
                    .vibrationEnabled(true)
                    .showOnlineStatus(true)
                    .allowMessages(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            return settingsRepository.save(defaultSettings);
        });
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
        result.put("soundEnabled", settings.isSoundEnabled());
        result.put("vibrationEnabled", settings.isVibrationEnabled());
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
        if (settingsDTO.getSoundEnabled() != null) {
            settings.setSoundEnabled(settingsDTO.getSoundEnabled());
        }
        if (settingsDTO.getVibrationEnabled() != null) {
            settings.setVibrationEnabled(settingsDTO.getVibrationEnabled());
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
