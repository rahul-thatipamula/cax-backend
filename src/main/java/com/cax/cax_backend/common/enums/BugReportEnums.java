package com.cax.cax_backend.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class BugReportEnums {

    public enum BugSeverity {
        LOW("low"), MEDIUM("medium"), HIGH("high"), CRITICAL("critical");
        private final String value;
        BugSeverity(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
        @JsonCreator
        public static BugSeverity fromValue(String value) {
            for (BugSeverity item : values()) {
                if (item.value.equalsIgnoreCase(value) || item.name().equalsIgnoreCase(value)) return item;
            }
            return MEDIUM;
        }
    }

    public enum BugStatus {
        OPEN("open"), IN_PROGRESS("in_progress"), RESOLVED("resolved"),
        CLOSED("closed"), WONT_FIX("wont_fix");
        private final String value;
        BugStatus(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
        @JsonCreator
        public static BugStatus fromValue(String value) {
            for (BugStatus item : values()) {
                if (item.value.equalsIgnoreCase(value) || item.name().equalsIgnoreCase(value)) return item;
            }
            return OPEN;
        }
    }
}
