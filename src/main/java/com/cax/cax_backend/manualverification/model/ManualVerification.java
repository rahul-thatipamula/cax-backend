package com.cax.cax_backend.manualverification.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * A manual ID-card verification request from a user who signed in with a
 * personal (non-college) email. Records are permanent — they are never
 * deleted, even after review, so a re-submission of the same ID card from a
 * different email can always be traced back through {@code idCardHash}.
 */
@Document(collection = "manualVerifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManualVerification {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String email;

    private String name;
    private String picture;

    // College the user claims to belong to (admin confirms/overrides on approval)
    private String collegeId;
    private String collegeName;

    // Encrypted at rest (AES-GCM via EncryptionUtils). Never serialized to clients;
    // admins fetch a short-lived presigned view URL through a dedicated endpoint.
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String idCardUrlEncrypted;

    // SHA-256 of the ID-card image bytes, used to detect the same physical card
    // being submitted from multiple accounts/emails.
    @Indexed
    private String idCardHash;

    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    // Set when the same idCardHash already exists under a different user.
    @Builder.Default
    private boolean flaggedDuplicate = false;
    private List<String> duplicateUserIds;

    // HMAC-SHA256 of the student ID number, entered by the admin (never by the
    // student) while reviewing the ID card — never the raw number. Keyed hash
    // (not plain SHA-256) because ID numbers are low-entropy and would
    // otherwise be brute-forceable from a DB dump.
    @Indexed
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String studentIdNumberHash;

    // Set when the same studentIdNumberHash already exists under a different user.
    @Builder.Default
    private boolean flaggedDuplicateIdNumber = false;
    private List<String> duplicateIdNumberUserIds;

    @Builder.Default
    private Instant submittedAt = Instant.now();

    private Instant reviewedAt;
    private String reviewedBy;
    private String adminNote;
    private String rejectionReason;

    // Approval is valid until the next July 19 (Asia/Kolkata). Yearly
    // re-verification is required after this date.
    private Instant validUntil;

    // Set when this record is a yearly re-verification of a previous approval.
    private String reverifyOfRecordId;

    // Tracks the last expiry-reminder push so users aren't spammed daily.
    private Instant lastReminderSentAt;

    public enum VerificationStatus {
        PENDING, APPROVED, REJECTED, EXPIRED
    }
}
