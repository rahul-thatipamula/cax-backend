package com.cax.cax_backend.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class ProductEnums {

    public enum ProductCategory {
        ELECTRONICS("electronics"), BOOKS("books"), CLOTHING("clothing"),
        FURNITURE("furniture"), SPORTS("sports"), STATIONERY("stationery"),
        ACCESSORIES("accessories"), FOOD("food"), SERVICES("services"), OTHER("other");

        private final String value;
        ProductCategory(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
        @JsonCreator
        public static ProductCategory fromValue(String value) {
            for (ProductCategory item : values()) {
                if (item.value.equalsIgnoreCase(value) || item.name().equalsIgnoreCase(value)) return item;
            }
            return OTHER;
        }
    }

    public enum ProductCondition {
        NEW("new"), LIKE_NEW("like_new"), GOOD("good"),
        FAIR("fair"), POOR("poor");

        private final String value;
        ProductCondition(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
        @JsonCreator
        public static ProductCondition fromValue(String value) {
            for (ProductCondition item : values()) {
                if (item.value.equalsIgnoreCase(value) || item.name().equalsIgnoreCase(value)) return item;
            }
            return GOOD;
        }
    }

    public enum ProductStatus {
        ACTIVE("active"), SOLD("sold"), PENDING_REVIEW("pending_review"),
        BANNED("banned"), EXPIRED("expired"), DRAFT("draft");

        private final String value;
        ProductStatus(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
        @JsonCreator
        public static ProductStatus fromValue(String value) {
            for (ProductStatus item : values()) {
                if (item.value.equalsIgnoreCase(value) || item.name().equalsIgnoreCase(value)) return item;
            }
            return PENDING_REVIEW;
        }
    }

    public enum ModerationStatus {
        PENDING("pending"), APPROVED("approved"), REJECTED("rejected");

        private final String value;
        ModerationStatus(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
        @JsonCreator
        public static ModerationStatus fromValue(String value) {
            for (ModerationStatus item : values()) {
                if (item.value.equalsIgnoreCase(value) || item.name().equalsIgnoreCase(value)) return item;
            }
            return PENDING;
        }
    }
}
