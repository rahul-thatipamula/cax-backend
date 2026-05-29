package com.cax.cax_backend.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public class WalletEnums {

    public enum TransactionType {
        CREDIT("credit"), DEBIT("debit");
        private final String value;
        TransactionType(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }

    public enum TransactionReason {
        RECHARGE("recharge"), PROMOTION("promotion"), ORDER_PAYMENT("order_payment"),
        ORDER_REFUND("order_refund"), REWARD("reward"), ADMIN_ADJUSTMENT("admin_adjustment"),
        SIGNUP_BONUS("signup_bonus"),
        AI_SUMMARY("ai_summary"), AI_PRO_SUMMARY("ai_pro_summary");
        private final String value;
        TransactionReason(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }

    public enum TransactionStatus {
        COMPLETED("completed"), PENDING("pending"), FAILED("failed"), REVERSED("reversed");
        private final String value;
        TransactionStatus(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }
}
