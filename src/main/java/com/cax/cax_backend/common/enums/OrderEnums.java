package com.cax.cax_backend.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public class OrderEnums {

    public enum OrderStatus {
        PENDING("pending"), CONFIRMED("confirmed"), PROCESSING("processing"),
        SHIPPED("shipped"), DELIVERED("delivered"), CANCELLED("cancelled"),
        REFUNDED("refunded"), ACCEPTED("accepted"), REJECTED("rejected"),
        COMPLETED("completed");

        private final String value;
        OrderStatus(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }

    public enum PaymentStatus {
        PENDING("pending"), PAID("paid"), REFUNDED("refunded"), FAILED("failed");

        private final String value;
        PaymentStatus(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }

    public enum PaymentMethod {
        CASH("cash"), WALLET("wallet"), UPI("upi"), ONLINE("online");

        private final String value;
        PaymentMethod(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }

    public enum DisputeStatus {
        NONE("none"), OPEN("open"), RESOLVED("resolved"), ESCALATED("escalated");

        private final String value;
        DisputeStatus(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }
}
