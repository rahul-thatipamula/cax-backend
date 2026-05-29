package com.cax.cax_backend.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public class NotificationEnums {

    public enum NotificationType {
        ORDER("order"), PRODUCT("product"), WALLET("wallet"),
        FEED("feed"), SYSTEM("system"),
        ID_CARD("id_card"),
        WALLET_RECHARGE("wallet_recharge"), WALLET_RECHARGE_FAILED("wallet_recharge_failed");
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
