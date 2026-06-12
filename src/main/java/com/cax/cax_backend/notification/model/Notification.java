package com.cax.cax_backend.notification.model;

import com.cax.cax_backend.common.enums.NotificationEnums.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Notification {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String title;
    private String body;
    private NotificationType type;
    private String imageUrl;

    @Builder.Default
    private NotificationPriority priority = NotificationPriority.MEDIUM;

    @Builder.Default
    private boolean read = false;

    private Map<String, String> data;

    @Builder.Default
    private Instant createdAt = Instant.now();
    private Instant readAt;
}
