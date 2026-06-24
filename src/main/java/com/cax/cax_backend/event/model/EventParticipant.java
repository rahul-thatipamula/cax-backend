package com.cax.cax_backend.event.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

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

    // Payment details (for paid events)
    @Builder.Default
    private double amountPaid = 0;

    private String utrNumber;
    private String paymentScreenshot; // Cloudflare R2 Storage URL

    // STATUSES: PENDING_APPROVAL, PENDING_PAYMENT, PAYMENT_SUBMITTED, VERIFIED, REJECTED
    @Builder.Default
    private String status = "PENDING_PAYMENT";

    private String verifiedByUserId;
    private Instant verifiedAt;

    private String ticketCode;

    @Builder.Default
    private boolean checkedIn = false;

    private Instant checkedInAt;

    @Builder.Default
    private boolean suspicious = false;

    private String suspiciousNote;

    @Builder.Default
    private Instant registeredAt = Instant.now();
}
