package com.cax.cax_backend.notification.dto;

import com.cax.cax_backend.common.enums.NotificationEnums.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationAdminView {

    private String id;
    private String title;
    private String body;
    private NotificationType type;
    private String imageUrl;
    private NotificationPriority priority;
    private boolean read;
    private Map<String, String> data;
    private Instant createdAt;
    private Instant readAt;

    private Recipient recipient;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Recipient {
        private String userId;
        private String name;
        private String email;
        private String picture;
        private String collegeName;
    }
}
