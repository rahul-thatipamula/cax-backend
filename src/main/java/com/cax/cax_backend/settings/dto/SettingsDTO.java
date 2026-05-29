package com.cax.cax_backend.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SettingsDTO {

    // Notification settings
    private Boolean notificationsEnabled;

    private Boolean emailNotificationsEnabled;

    private Boolean pushNotificationsEnabled;

    // Theme settings
    private Boolean darkMode;

    // Language settings
    private String language;

    // Additional preferences
    private Boolean soundEnabled;

    private Boolean vibrationEnabled;

    private Boolean showOnlineStatus;

    private Boolean allowMessages;
}
