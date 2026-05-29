package com.cax.cax_backend.settings.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.settings.dto.SettingsDTO;
import com.cax.cax_backend.settings.model.UserSettings;
import com.cax.cax_backend.settings.service.SettingsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    /**
     * GET /api/settings - Fetch all user settings
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSettings(Authentication auth) {
        String userId = auth.getName();
        Map<String, Object> settings = settingsService.getSettingsAsMap(userId);
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    /**
     * POST /api/settings - Update user settings (partial update)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserSettings>> updateSettings(
            Authentication auth,
            @RequestBody SettingsDTO settingsDTO) {
        String userId = auth.getName();
        UserSettings updatedSettings = settingsService.updateSettings(userId, settingsDTO);
        return ResponseEntity.ok(ApiResponse.success(updatedSettings));
    }

    /**
     * PUT /api/settings - Update user settings (partial update)
     */
    @PutMapping
    public ResponseEntity<ApiResponse<UserSettings>> putSettings(
            Authentication auth,
            @RequestBody SettingsDTO settingsDTO) {
        String userId = auth.getName();
        UserSettings updatedSettings = settingsService.updateSettings(userId, settingsDTO);
        return ResponseEntity.ok(ApiResponse.success(updatedSettings));
    }

    /**
     * GET /api/settings/theme - Fetch theme preference
     */
    @GetMapping("/theme")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getThemePreference(Authentication auth) {
        String userId = auth.getName();
        UserSettings settings = settingsService.getSettings(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("isDarkMode", settings.isDarkMode());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/settings/theme - Update theme preference
     */
    @PostMapping("/theme")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateThemePreference(
            Authentication auth,
            @RequestBody Map<String, Boolean> body) {
        String userId = auth.getName();
        Boolean isDarkMode = body.get("isDarkMode");
        if (isDarkMode == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("isDarkMode field is required", 1400, 400));
        }
        UserSettings settings = settingsService.updateThemePreference(userId, isDarkMode);
        Map<String, Object> response = new HashMap<>();
        response.put("isDarkMode", settings.isDarkMode());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/settings/notifications - Fetch notification preference
     */
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNotificationPreference(Authentication auth) {
        String userId = auth.getName();
        UserSettings settings = settingsService.getSettings(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", settings.isNotificationsEnabled());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/settings/notifications - Update notification preference
     */
    @PostMapping("/notifications")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateNotificationPreference(
            Authentication auth,
            @RequestBody Map<String, Boolean> body) {
        String userId = auth.getName();
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("enabled field is required", 1400, 400));
        }
        UserSettings settings = settingsService.updateNotificationPreference(userId, enabled);
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", settings.isNotificationsEnabled());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/settings/language - Fetch language preference
     */
    @GetMapping("/language")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLanguagePreference(Authentication auth) {
        String userId = auth.getName();
        UserSettings settings = settingsService.getSettings(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("language", settings.getLanguage());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/settings/language - Update language preference
     */
    @PostMapping("/language")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateLanguagePreference(
            Authentication auth,
            @RequestBody Map<String, String> body) {
        String userId = auth.getName();
        String language = body.get("language");
        if (language == null || language.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("language field is required", 1400, 400));
        }
        UserSettings settings = settingsService.updateLanguagePreference(userId, language);
        Map<String, Object> response = new HashMap<>();
        response.put("language", settings.getLanguage());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
