package com.cax.cax_backend.settings.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "user_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSettings {

    @Id
    private String id;

    @Indexed(unique = true, name = "userId_unique")
    private String userId;

    // Notification settings
    @Builder.Default
    private boolean notificationsEnabled = true;

    @Builder.Default
    private boolean emailNotificationsEnabled = true;

    @Builder.Default
    private boolean pushNotificationsEnabled = true;

    // Theme settings
    @Builder.Default
    private boolean darkMode = true;

    // Language settings
    @Builder.Default
    private String language = "English";

    // Additional preferences
    @Builder.Default
    private boolean showOnlineStatus = true;

    @Builder.Default
    private boolean allowMessages = true;

    // Timestamps
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Builder.Default
    private boolean deleted = false;

    private Instant deletedAt;
}
