package com.cax.cax_backend.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Type of college organization. Stored as a string in MongoDB so adding
 * new values here is the only change required in future.
 */
public enum OrganizationType {
    CLUB("CLUB", "Club"),
    COMMUNITY("COMMUNITY", "Community"),
    SOCIETY("SOCIETY", "Society");

    private final String value;
    private final String displayName;

    OrganizationType(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    @JsonValue
    public String getValue() { return value; }

    public String getDisplayName() { return displayName; }

    @JsonCreator
    public static OrganizationType fromValue(String v) {
        if (v == null || v.isBlank()) return CLUB;
        for (OrganizationType t : values()) {
            if (t.value.equalsIgnoreCase(v.trim())) return t;
        }
        return CLUB;
    }
}
