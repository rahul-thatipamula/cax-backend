package com.cax.cax_backend.event.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "event_participants")
@CompoundIndexes({
    @CompoundIndex(name = "event_user_idx", def = "{'eventId': 1, 'userId': 1}", unique = true)
})
public class EventParticipant {
    @Id
    private String id;

    // Optimistic lock: guards against two concurrent updates (e.g. a duplicate
    // Razorpay success callback) silently overwriting each other's status change.
    @Version
    private Long version;

    @Indexed
    private String eventId;

    @Indexed
    private String userId;

    private String name;
    private String email;
    private String picture;
    private String college;
    private String collegeId;

    // ID card details collected at registration (only when the event requires it)
    private String idCardNumber;
    private String idCardName;        // Full name as printed on the ID card
    private String idCardDepartment;  // Department / branch as printed on the ID card

    // Customizable details collected at registration
    private String gender;
    private String dateOfBirth;
    private String registerNumber;
    private String department;
    private String yearOfStudy;
    private String phoneNumber;
    private String collegeName;

    // Payment details (for paid events)
    @Builder.Default
    private double amountPaid = 0;

    private String utrNumber;
    private String paymentScreenshot; // Cloudflare R2 Storage URL

    // Razorpay payment details (set when payment is made via Razorpay)
    private String razorpayOrderId;
    private String razorpayPaymentId;

    // STATUSES: PENDING_APPROVAL, PENDING_PAYMENT, PAYMENT_SUBMITTED, VERIFIED, REJECTED
    @Builder.Default
    private String status = "PENDING_PAYMENT";

    private String verifiedByUserId;
    /** Display name of the person who verified this participant (denormalized). */
    private String verifiedByName;
    /** Organization the verifier belongs to at the time of verification. */
    private String verifiedByOrganizationId;
    /** Display name of the verifier's organization (denormalized). */
    private String verifiedByOrganizationName;
    private Instant verifiedAt;

    private String ticketCode;

    /** Team this participant belongs to (null for individual registrations). */
    @Indexed
    private String teamId;

    /** Denormalized team name so organizer lists render without a team lookup. */
    private String teamName;

    @com.fasterxml.jackson.annotation.JsonProperty("isTeamLeader")
    @Builder.Default
    private boolean isTeamLeader = false;

    @Builder.Default
    private boolean checkedIn = false;

    private Instant checkedInAt;
    /** User ID of the person who performed the physical check-in scan. */
    private String checkedInByUserId;
    /** Display name of the check-in operator (denormalized). */
    private String checkedInByName;
    /** Organization the check-in operator belongs to. */
    private String checkedInByOrganizationId;
    /** Display name of the check-in operator's organization (denormalized). */
    private String checkedInByOrganizationName;

    @Builder.Default
    private boolean suspicious = false;

    private String suspiciousNote;

    @Builder.Default
    private Instant registeredAt = Instant.now();

    @Builder.Default
    private List<PaymentHistoryEntry> paymentHistory = new java.util.ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentHistoryEntry {
        private String status;
        private String utrNumber;
        private String paymentScreenshot;
        private double amountPaid;
        private Instant timestamp;

        private String verifiedByUserId;
        private String verifiedByName;
        private String verifiedByOrganizationId;
        private String verifiedByOrganizationName;
    }
}
