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
import com.fasterxml.jackson.annotation.JsonProperty;

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
    private String clubId;

    private String createdByUserId;
    private String name;
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

    @JsonProperty("global")
    @Builder.Default
    private boolean global = false;

    // When true, participants must submit ID card details (number, name on card,
    // department/branch) while registering for this event.
    @JsonProperty("idCardRequired")
    @Builder.Default
    private boolean idCardRequired = false;

    @Builder.Default
    private double fee = 0;

    private String upiId;
    private String upiQrCode; // Cloudflare R2 Storage URL of QR code image

    // Status: ACTIVE, COMPLETED, CANCELLED
    @Builder.Default
    private String status = "ACTIVE";

    @Builder.Default
    private Instant createdAt = Instant.now();

    private List<String> eventImages;

    private List<EventCoordinator> coordinators;

    private List<String> guidelines;
    private List<EventJury> jury;
    private List<EventGuest> guests;

    private String collegeId;
    private String collegeName;

    @Builder.Default
    private int notificationsSentCount = 0;

    private Instant updatedAt;

    private String websiteUrl;

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
}
