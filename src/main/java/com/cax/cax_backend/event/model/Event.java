package com.cax.cax_backend.event.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Transient;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "events")
@CompoundIndexes({
    @CompoundIndex(name = "college_status_global_idx", def = "{'collegeId': 1, 'status': 1, 'global': 1}"),
    @CompoundIndex(name = "global_status_idx", def = "{'global': 1, 'status': 1}")
})
public class Event {
    @Id
    private String id;

    @Indexed
    private String organizationId;

    private String createdByUserId;

    @NotBlank(message = "Event name is required")
    @Size(min = 3, max = 100, message = "Event name must be between 3 and 100 characters")
    private String name;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 3000, message = "Description must be between 10 and 3000 characters")
    private String description;

    private String logo; // Cloudflare R2 Storage URL

    // Dates
    private Instant registrationEndDate;
    private Instant eventStartDate;
    private Instant eventEndDate;

    // Payment
    @JsonProperty("isPaid")
    @Builder.Default
    private boolean isPaid = false;

    /**
     * Controls which payment method participants use for a paid event.
     * RAZORPAY   — online checkout via Razorpay (no UPI ID/QR required).
     * MANUAL_UPI — organiser-verified UPI transfer (requires upiId).
     * Ignored when isPaid is false.
     */
    @Builder.Default
    private String paymentMode = "RAZORPAY";

    @JsonProperty("global")
    @Builder.Default
    private boolean global = false;

    // When true, participants must submit ID card details (number, name on card,
    // department/branch) while registering for this event.
    @JsonProperty("idCardRequired")
    @Builder.Default
    private boolean idCardRequired = false;

    @JsonProperty("requiredFields")
    @Builder.Default
    private List<String> requiredFields = new java.util.ArrayList<>();

    @Transient
    private long joinedCount;

    @DecimalMin(value = "0", message = "Fee cannot be negative")
    @DecimalMax(value = "999999", message = "Fee cannot exceed ₹9,99,999")
    @Builder.Default
    private double fee = 0;

    @Size(max = 60, message = "UPI ID cannot exceed 60 characters")
    private String upiId;

    private String upiQrCode; // Cloudflare R2 Storage URL of QR code image

    // Status: ACTIVE, COMPLETED, CANCELLED
    @Builder.Default
    private String status = "ACTIVE";

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Size(max = 9, message = "An event can have at most 9 gallery images")
    private List<String> eventImages;

    @Valid
    @Size(max = 4, message = "An event can have at most 4 coordinators")
    private List<EventCoordinator> coordinators;

    @Size(max = 20, message = "An event can have at most 20 guidelines")
    private List<@NotBlank(message = "Guideline cannot be blank") @Size(min = 3, max = 300, message = "Each guideline must be between 3 and 300 characters") String> guidelines;

    @Valid
    @Size(max = 5, message = "An event can have at most 5 jury members")
    private List<EventJury> jury;

    @Valid
    @Size(max = 5, message = "An event can have at most 5 guests")
    private List<EventGuest> guests;

    /**
     * Organizations that are collaborating on this event.
     * Only the primary org (organizationId) can invite collaborators.
     * Collaborators with status ACCEPTED gain manage access (verify, check-in)
     * and the event appears on their profile. No hard cap — any number of orgs
     * can collaborate on large multi-org events.
     */
    @Builder.Default
    private List<EventCollaborator> collaborators = new java.util.ArrayList<>();

    /** Org IDs to add as collaborators at creation time. Not persisted — resolved in service. */
    @Transient
    private List<String> collaboratorIds;

    private String collegeId;
    private String collegeName;

    @Builder.Default
    private int notificationsSentCount = 0;

    private Instant updatedAt;

    @Size(max = 500, message = "Website URL cannot exceed 500 characters")
    private String websiteUrl;

    // When true, registration and payment are handled on an external platform.
    // CAX only shows event info; isPaid, fee, upiId, upiQrCode, and idCardRequired
    // must remain false/null — enforced server-side regardless of client payload.
    @JsonProperty("isExternallyManaged")
    @Builder.Default
    private boolean isExternallyManaged = false;

    // Required when isExternallyManaged is true. Must be a valid https:// URL.
    @Size(max = 500, message = "Registration link cannot exceed 500 characters")
    private String externalRegistrationUrl;

    @Indexed(unique = true, partialFilter = "{'idempotencyKey': {'$type': 'string'}}")
    private String idempotencyKey;

    @Builder.Default
    private boolean deleted = false;

    private Instant deletedAt;


    public List<EventCoordinator> getCoordinators() {
        return coordinators == null ? new java.util.ArrayList<>() : coordinators;
    }

    public List<String> getGuidelines() {
        return guidelines == null ? new java.util.ArrayList<>() : guidelines;
    }

    public List<EventJury> getJury() {
        return jury == null ? new java.util.ArrayList<>() : jury;
    }

    public List<EventGuest> getGuests() {
        return guests == null ? new java.util.ArrayList<>() : guests;
    }

    public List<EventCollaborator> getCollaborators() {
        return collaborators == null ? new java.util.ArrayList<>() : collaborators;
    }
}
