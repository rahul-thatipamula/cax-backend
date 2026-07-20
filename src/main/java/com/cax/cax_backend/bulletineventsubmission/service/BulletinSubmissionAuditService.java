package com.cax.cax_backend.bulletineventsubmission.service;

import com.cax.cax_backend.bulletineventsubmission.model.BulletinSubmissionAuditLog;
import com.cax.cax_backend.bulletineventsubmission.model.BulletinSubmissionAuditLog.Outcome;
import com.cax.cax_backend.bulletineventsubmission.repository.BulletinSubmissionAuditLogRepository;
import com.cax.cax_backend.common.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Writes the security trail for public "Post Event" submissions into
 * {@code bulletin_submission_audit_logs}. Every write is best-effort: an audit
 * failure must never turn a good submission into an error for the organizer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulletinSubmissionAuditService {

    /** Headers are attacker-controlled — cap them so a huge header can't bloat a document. */
    private static final int MAX_HEADER_LEN = 512;

    private final BulletinSubmissionAuditLogRepository repository;

    /** One attempt against the public form, as seen by the controller. */
    public record Attempt(
            String organizerEmail,
            String organizerName,
            String organizerPhone,
            String organizerCollege,
            String eventTitle,
            String captchaToken,
            boolean captchaPassed,
            Outcome outcome,
            String failureReason,
            String submissionId) {}

    public void record(HttpServletRequest request, Attempt attempt) {
        try {
            BulletinSubmissionAuditLog entry = BulletinSubmissionAuditLog.builder()
                    .submissionId(attempt.submissionId())
                    .outcome(attempt.outcome())
                    .failureReason(attempt.failureReason())
                    .organizerEmail(lower(attempt.organizerEmail()))
                    .organizerName(attempt.organizerName())
                    .organizerPhone(attempt.organizerPhone())
                    .organizerCollege(attempt.organizerCollege())
                    .eventTitle(attempt.eventTitle())
                    .clientIp(ClientIpUtil.getClientIp(request))
                    .forwardedFor(header(request, "X-Forwarded-For"))
                    .userAgent(header(request, "User-Agent"))
                    .origin(header(request, "Origin"))
                    .referer(header(request, "Referer"))
                    .acceptLanguage(header(request, "Accept-Language"))
                    .captchaPassed(attempt.captchaPassed())
                    .captchaTokenFingerprint(fingerprint(attempt.captchaToken()))
                    .createdAt(Instant.now())
                    .build();

            repository.save(entry);

            if (attempt.outcome() != Outcome.ACCEPTED) {
                log.warn("Public bulletin submission rejected: outcome={} ip={} email={} reason={}",
                        attempt.outcome(), entry.getClientIp(), entry.getOrganizerEmail(), attempt.failureReason());
            }
        } catch (Exception e) {
            // Never propagate — the organizer's submission matters more than its audit row.
            log.error("Failed to write bulletin submission audit log: {}", e.getMessage());
        }
    }

    // ── Admin reads ───────────────────────────────────────────────────────────

    public List<BulletinSubmissionAuditLog> getAll(String outcome) {
        if (outcome == null || outcome.isBlank()) {
            return repository.findAllByOrderByCreatedAtDesc();
        }
        return repository.findByOutcomeOrderByCreatedAtDesc(Outcome.valueOf(outcome.toUpperCase()));
    }

    public List<BulletinSubmissionAuditLog> getByClientIp(String clientIp) {
        return repository.findByClientIpOrderByCreatedAtDesc(clientIp);
    }

    public List<BulletinSubmissionAuditLog> getByOrganizerEmail(String email) {
        return repository.findByOrganizerEmailOrderByCreatedAtDesc(lower(email));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > MAX_HEADER_LEN ? value.substring(0, MAX_HEADER_LEN) : value;
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    /** Short SHA-256 prefix — enough to spot the same token being replayed, useless as a credential. */
    private static String fingerprint(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception e) {
            return null;
        }
    }
}
