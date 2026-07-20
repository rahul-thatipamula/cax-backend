package com.cax.cax_backend.bulletineventsubmission.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Forensic trail for every attempt against the public "Post Event" form
 * (caxone.in/postEvent) — written whether the attempt succeeded, failed CAPTCHA,
 * or failed validation. Kept out of {@link BulletinEventSubmission} on purpose:
 * that document is admin-facing review data, this one is security data (IP,
 * user agent, CAPTCHA outcome) used to spot abuse and trace a bad submission
 * back to its source.
 */
@Document(collection = "bulletin_submission_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulletinSubmissionAuditLog {

    @Id
    private String id;

    /** The stored submission, when the attempt got that far. Null for rejected attempts. */
    @Indexed
    private String submissionId;

    @Indexed
    private Outcome outcome;

    /** Why the attempt was rejected — null when accepted. */
    private String failureReason;

    // ── Who submitted (snapshot; survives even if the submission is never stored) ──
    @Indexed
    private String organizerEmail;
    private String organizerName;
    private String organizerPhone;
    private String organizerCollege;
    private String eventTitle;

    // ── Where it came from ────────────────────────────────────────────────────
    /** Best-effort client IP (first X-Forwarded-For hop, else socket address). */
    @Indexed
    private String clientIp;

    /** Raw X-Forwarded-For chain — keeps the proxy hops the single IP above drops. */
    private String forwardedFor;

    private String userAgent;
    private String origin;
    private String referer;
    private String acceptLanguage;

    // ── CAPTCHA ───────────────────────────────────────────────────────────────
    private boolean captchaPassed;

    /** SHA-256 prefix of the Turnstile token — lets us correlate replays of the same
     *  token without ever storing the credential itself. */
    private String captchaTokenFingerprint;

    @Indexed
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum Outcome {
        /** Stored as a pending submission. */
        ACCEPTED,
        /** Turnstile rejected the token. */
        CAPTCHA_FAILED,
        /** Passed CAPTCHA but failed date/field validation. */
        VALIDATION_FAILED
    }
}
