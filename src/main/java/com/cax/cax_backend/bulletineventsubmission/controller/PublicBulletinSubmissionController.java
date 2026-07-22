package com.cax.cax_backend.bulletineventsubmission.controller;

import com.cax.cax_backend.bulletinevent.model.BulletinEvent;
import com.cax.cax_backend.bulletinevent.service.BulletinEventService;
import com.cax.cax_backend.college.repository.CollegeRepository;
import com.cax.cax_backend.bulletineventsubmission.model.BulletinEventSubmission;
import com.cax.cax_backend.bulletineventsubmission.model.BulletinSubmissionAuditLog.Outcome;
import com.cax.cax_backend.bulletineventsubmission.service.BulletinEventSubmissionService;
import com.cax.cax_backend.bulletineventsubmission.service.BulletinSubmissionAuditService;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.common.turnstile.TurnstileService;
import com.cax.cax_backend.common.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public, unauthenticated endpoints backing caxone.in/postEvent — auto-permitted by
 * SecurityConfig's existing "/api/public/**" allowlist (no SecurityConfig change needed).
 * The submit endpoint is CAPTCHA-gated (TurnstileService) and IP rate-limited (see
 * RateLimitConfigService's "public-bulletin-submit" rule).
 */
@RestController
@RequestMapping("/api/public/bulletin-events")
@RequiredArgsConstructor
public class PublicBulletinSubmissionController {

    /** Hard cap on how many colleges one submission may target — a targeted event is meant to
     *  be narrow; anything wider should just be submitted as global. */
    private static final int MAX_TARGET_COLLEGES = 25;

    private final BulletinEventSubmissionService submissionService;
    private final BulletinEventService bulletinEventService;
    private final TurnstileService turnstileService;
    private final BulletinSubmissionAuditService auditService;
    private final CollegeRepository collegeRepository;
    private final com.cax.cax_backend.bulletinevent.service.BulletinEventAnalyticsService analyticsService;

    @GetMapping("/top")
    public ResponseEntity<ApiResponse<List<BulletinEvent>>> topEvents(
            @RequestParam(required = false, defaultValue = "4") int limit) {
        return ResponseEntity.ok(ApiResponse.success(bulletinEventService.getTopGlobalBulletinEvents(limit)));
    }

    /** College picker for the public form. Deliberately returns only id/name/code — the full
     *  College document carries emailDomains and student counts that an anonymous caller has
     *  no reason to see. Searching/filtering happens client-side over this small list. */
    @GetMapping("/colleges")
    public ResponseEntity<ApiResponse<List<CollegeOption>>> colleges() {
        List<CollegeOption> options = collegeRepository.findAllActive().stream()
                .map(c -> new CollegeOption(c.getId(), c.getCollegeName(), c.getCollegeCode()))
                .sorted(java.util.Comparator.comparing(
                        CollegeOption::name, java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(options));
    }

    public record CollegeOption(String id, String name, String code) {}

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<BulletinEventSubmission>> submit(
            @Valid @RequestBody SubmitRequest request,
            HttpServletRequest httpRequest) {
        // Client IP via ClientIpUtil, not getRemoteAddr() — behind the load balancer the
        // socket address is the proxy's, which would make Turnstile's remoteip check useless
        // and every audit row look like it came from the same host.
        String clientIp = ClientIpUtil.getClientIp(httpRequest);

        boolean captchaOk = turnstileService.verify(request.turnstileToken, clientIp);
        if (!captchaOk) {
            auditService.record(httpRequest, request.toAttempt(
                    false, Outcome.CAPTCHA_FAILED, "Turnstile verification failed", null));
            throw new BusinessException.BadRequestException("CAPTCHA verification failed. Please try again.");
        }

        try {
            request.validateDates();
            validateTargeting(request);
        } catch (BusinessException.BadRequestException e) {
            auditService.record(httpRequest, request.toAttempt(
                    true, Outcome.VALIDATION_FAILED, e.getMessage(), null));
            throw e;
        }

        BulletinEventSubmission submission = submissionService.submit(request.toSubmission());
        auditService.record(httpRequest, request.toAttempt(
                true, Outcome.ACCEPTED, null, submission.getId()));

        return ResponseEntity.ok(ApiResponse.created(
                "Thanks! Your event was submitted and is pending review by the CAX team.", submission));
    }

    /** Targeting is the one place a public caller supplies references to other documents, so
     *  the IDs are checked against real, non-deleted colleges rather than trusted. Without this
     *  an anonymous caller could seed submissions pointing at arbitrary or non-existent ids. */
    private void validateTargeting(SubmitRequest request) {
        if (request.isGlobalEvent()) {
            return;
        }
        List<String> ids = request.normalizedCollegeIds();
        if (ids.isEmpty()) {
            throw new BusinessException.BadRequestException(
                    "Select at least one college, or mark the event as open to all colleges.");
        }
        if (ids.size() > MAX_TARGET_COLLEGES) {
            throw new BusinessException.BadRequestException(
                    "Select at most " + MAX_TARGET_COLLEGES + " colleges, or mark the event as open to all.");
        }
        long found = collegeRepository.findAllActive().stream()
                .filter(c -> ids.contains(c.getId()))
                .count();
        if (found != ids.size()) {
            throw new BusinessException.BadRequestException("One or more selected colleges are not valid.");
        }
    }

    /** Plain request DTO — deliberately separate from BulletinEventSubmission so a public,
     *  unauthenticated caller can never set status/id/review fields directly. */
    public static class SubmitRequest {

        /** Accepts http(s) URLs only — blocks javascript:/data: payloads reaching the app. */
        private static final String URL_REGEX = "^https?://[^\\s]{4,500}$";

        @NotBlank(message = "Event name is required")
        @Size(min = 3, max = 150, message = "Event name must be between 3 and 150 characters")
        public String title;

        @NotBlank(message = "Description is required")
        @Size(min = 10, max = 3000, message = "Description must be between 10 and 3000 characters")
        public String description;

        @NotBlank(message = "Poster image URL is required")
        @Pattern(regexp = URL_REGEX, message = "Poster image must be a valid http(s) URL")
        @Size(max = 500, message = "Poster image URL must be at most 500 characters")
        public String coverImage;

        @NotBlank(message = "Registration link is required")
        @Pattern(regexp = URL_REGEX, message = "Registration link must be a valid http(s) URL")
        @Size(max = 500, message = "Registration link must be at most 500 characters")
        public String externalLink;

        @NotNull(message = "Event start date is required")
        public java.time.Instant eventStartDate;

        @NotNull(message = "Event end date is required")
        public java.time.Instant eventEndDate;

        @NotNull(message = "Registration close date is required")
        public java.time.Instant registrationEndDate;

        @NotBlank(message = "Host name is required")
        @Size(min = 2, max = 100, message = "Host name must be between 2 and 100 characters")
        public String conductedBy;

        @NotBlank(message = "Your name is required")
        @Size(min = 2, max = 100, message = "Your name must be between 2 and 100 characters")
        public String organizerName;

        @NotBlank(message = "Your email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 150, message = "Email must be at most 150 characters")
        public String organizerEmail;

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[+]?[0-9\\s-]{7,20}$", message = "Enter a valid phone number")
        public String organizerPhone;

        @NotBlank(message = "College is required")
        @Size(min = 2, max = 150, message = "College must be between 2 and 150 characters")
        public String organizerCollege;

        /** True (the default) = visible to every college; false = only the colleges in
         *  collegeIds. Boxed so an older client that omits the field still means "global". */
        public Boolean global;

        /** Target colleges when global is false. Validated against the colleges collection. */
        public List<String> collegeIds;

        @NotBlank(message = "CAPTCHA verification is required")
        public String turnstileToken;

        boolean isGlobalEvent() {
            return global == null || global;
        }

        /** Trimmed, de-duplicated, blank-free view of the requested college ids. */
        List<String> normalizedCollegeIds() {
            if (collegeIds == null) {
                return List.of();
            }
            return collegeIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
        }

        /** Cross-field date sanity that bean validation annotations cannot express. */
        void validateDates() {
            if (eventEndDate.isBefore(eventStartDate)) {
                throw new BusinessException.BadRequestException("Event end date must be after the start date.");
            }
            if (registrationEndDate.isAfter(eventEndDate)) {
                throw new BusinessException.BadRequestException(
                        "Registration must close on or before the event ends.");
            }
            if (eventStartDate.isBefore(java.time.Instant.now().minus(java.time.Duration.ofDays(1)))) {
                throw new BusinessException.BadRequestException("Event start date cannot be in the past.");
            }
        }

        BulletinEventSubmission toSubmission() {
            return BulletinEventSubmission.builder()
                    .title(title.trim())
                    .description(description.trim())
                    .coverImage(coverImage.trim())
                    .externalLink(externalLink.trim())
                    .eventStartDate(eventStartDate)
                    .eventEndDate(eventEndDate)
                    .registrationEndDate(registrationEndDate)
                    .conductedBy(conductedBy.trim())
                    .organizerName(organizerName.trim())
                    .organizerEmail(organizerEmail.trim().toLowerCase())
                    .organizerPhone(organizerPhone.trim())
                    .organizerCollege(organizerCollege.trim())
                    .global(isGlobalEvent())
                    .collegeIds(isGlobalEvent() ? null : normalizedCollegeIds())
                    .build();
        }

        /** Snapshot of the submitter for the security trail — written for rejected attempts
         *  too, so a failed run doesn't vanish without a trace. */
        BulletinSubmissionAuditService.Attempt toAttempt(
                boolean captchaPassed, Outcome outcome, String failureReason, String submissionId) {
            return new BulletinSubmissionAuditService.Attempt(
                    organizerEmail, organizerName, organizerPhone, organizerCollege, title,
                    turnstileToken, captchaPassed, outcome, failureReason, submissionId);
        }
    }
}
