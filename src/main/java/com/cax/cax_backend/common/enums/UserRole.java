package com.cax.cax_backend.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UserRole {
    STUDENT("student"),
    ADMIN("admin"),
    SUPER_STUDENT("superstudent"),
    FACULTY("faculty"),
    ALUMNI("alumni");

    private final String value;

    UserRole(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    public static UserRole fromValue(String v) {
        for (UserRole r : values()) {
            if (r.value.equalsIgnoreCase(v)) return r;
        }
        return STUDENT;
    }
}
