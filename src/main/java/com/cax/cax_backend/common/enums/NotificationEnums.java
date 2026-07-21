package com.cax.cax_backend.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public class NotificationEnums {

    public enum NotificationType {
        FEED("feed"), SYSTEM("system"),
        EVENT("event"), ARCADE("arcade");
        private final String value;
        NotificationType(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }

    public enum NotificationPriority {
        LOW("low"), MEDIUM("medium"), HIGH("high"), URGENT("urgent");
        private final String value;
        NotificationPriority(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }
}
