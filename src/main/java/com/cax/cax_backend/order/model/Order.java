package com.cax.cax_backend.order.model;

import com.cax.cax_backend.common.enums.OrderEnums.*;
import com.cax.cax_backend.common.converter.InstantDeserializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Order {

    @Id
    private String id;

    @Indexed
    private String buyerId;
    @Indexed
    private String sellerId;
    private String productId;
    private String collegeId;

    // Denormalized snapshots
    private ProductSnapshot productSnapshot;
    private UserSnapshot buyerSnapshot;
    private UserSnapshot sellerSnapshot;

    private int quantity;
    private double totalPrice;
    private double unitPrice;

    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    private PaymentInfo payment;
    private PickupInfo pickup;

    private String cancelReason;
    private String cancelledBy;

    // Rating
    private Integer buyerRating;
    private String buyerReview;
    private Integer sellerRating;
    private String sellerReview;

    // Dispute
    @Builder.Default
    private DisputeStatus disputeStatus = DisputeStatus.NONE;
    private String disputeReason;

    @Builder.Default
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant createdAt = Instant.now();
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant updatedAt;
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant completedAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProductSnapshot {
        private String productId;
        private String name;
        private double price;
        private String image;
        private String category;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserSnapshot {
        private String userId;
        private String name;
        private String picture;
        private String collegeName;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PaymentInfo {
        @Builder.Default
        private PaymentStatus status = PaymentStatus.PENDING;
        private PaymentMethod method;
        private String transactionId;
        private Instant paidAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PickupInfo {
        private String location;
        private String instructions;
        private Instant scheduledAt;
        private boolean confirmed;
    }
}
