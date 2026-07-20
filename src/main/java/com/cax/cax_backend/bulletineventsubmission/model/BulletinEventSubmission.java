package com.cax.cax_backend.bulletineventsubmission.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * An event submitted by an external organizer through the public "Post Event"
 * page (caxone.in/postEvent). Held here — separate from the live BulletinEvent
 * collection — until an admin reviews it; approval promotes the fields into a
 * real BulletinEvent (see BulletinEventSubmissionService.approve).
 */
@Document(collection = "bulletin_event_submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulletinEventSubmission {

    @Id
    private String id;

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 150, message = "Title must be between 3 and 150 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 3000, message = "Description must be between 10 and 3000 characters")
    private String description;

    private String coverImage; // Poster/banner image URL supplied by the organizer

    @NotBlank(message = "External link is required")
    private String externalLink; // External registration/info URL

    private Instant eventStartDate;
    private Instant eventEndDate;
    private Instant registrationEndDate;

    private String conductedBy; // Organiser / host / club name

    @Builder.Default
    private boolean global = true;

    private List<String> collegeIds; // Targeted colleges if global is false

    // ── Organizer / submitter details ───────────────────────────────────────
    @NotBlank(message = "Organizer name is required")
    private String organizerName;

    @NotBlank(message = "Organizer email is required")
    private String organizerEmail;

    private String organizerPhone;
    private String organizerCollege;

    // ── Review workflow ──────────────────────────────────────────────────────
    @Indexed
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @Builder.Default
    private Instant submittedAt = Instant.now();

    private Instant reviewedAt;
    private String reviewedBy; // admin userId
    private String rejectionReason;

    /** The BulletinEvent created from this submission once approved. */
    private String linkedBulletinEventId;

    public enum SubmissionStatus {
        PENDING, APPROVED, REJECTED
    }
}
