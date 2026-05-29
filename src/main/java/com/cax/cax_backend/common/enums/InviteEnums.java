package com.cax.cax_backend.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public class InviteEnums {

    public enum InviteType {
        CLUB("club");
        private final String value;
        InviteType(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }

    public enum InviteStatus {
        PENDING("pending"), ACCEPTED("accepted"), DECLINED("declined"),
        EXPIRED("expired"), CANCELLED("cancelled");
        private final String value;
        InviteStatus(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }
}
