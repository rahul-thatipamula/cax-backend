package com.cax.cax_backend.bulletineventsubmission.controller;

import com.cax.cax_backend.bulletineventsubmission.model.BulletinEventSubmission;
import com.cax.cax_backend.bulletineventsubmission.model.BulletinSubmissionAuditLog;
import com.cax.cax_backend.bulletineventsubmission.service.BulletinEventSubmissionService;
import com.cax.cax_backend.bulletineventsubmission.service.BulletinSubmissionAuditService;
import com.cax.cax_backend.common.annotation.AdminActivityLog;
import com.cax.cax_backend.common.dto.ApiResponse;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin review queue for organizer-submitted bulletin events (see PublicBulletinSubmissionController
 *  for the public-facing submit endpoint). */
@RestController
@RequestMapping("/api/bulletin-event-submissions")
@RequiredArgsConstructor
public class BulletinEventSubmissionController {

    private final BulletinEventSubmissionService service;
    private final BulletinSubmissionAuditService auditService;

    @GetMapping("/admin/all")
    @AdminActivityLog(action = "List Bulletin Event Submissions")
    public ResponseEntity<ApiResponse<List<BulletinEventSubmission>>> getAll(
            @RequestParam(required = false) String status,
            Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(service.getAll(status)));
    }

    @GetMapping("/admin/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStats(Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(service.getStats()));
    }

    @PutMapping("/admin/{id}/approve")
    @AdminActivityLog(action = "Approve Bulletin Event Submission", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<BulletinEventSubmission>> approve(
            @PathVariable String id, Authentication auth) {
        String adminId = checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success("Submission approved and published", service.approve(id, adminId)));
    }

    @PutMapping("/admin/{id}/reject")
    @AdminActivityLog(action = "Reject Bulletin Event Submission", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<BulletinEventSubmission>> reject(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        String adminId = checkAdmin(auth);
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        return ResponseEntity.ok(ApiResponse.success("Submission rejected", service.reject(id, adminId, reason)));
    }

    // ── Security trail ────────────────────────────────────────────────────────

    /** Submitter metadata (IP, user agent, CAPTCHA outcome) for every attempt against the
     *  public form — including the ones that were rejected and never became submissions.
     *  Filter by {@code outcome} (ACCEPTED / CAPTCHA_FAILED / VALIDATION_FAILED), or pivot
     *  on a suspect {@code clientIp} / {@code organizerEmail}. */
    @GetMapping("/admin/audit-logs")
    @AdminActivityLog(action = "List Bulletin Submission Audit Logs")
    public ResponseEntity<ApiResponse<List<BulletinSubmissionAuditLog>>> getAuditLogs(
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) String organizerEmail,
            Authentication auth) {
        checkAdmin(auth);
        if (clientIp != null && !clientIp.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(auditService.getByClientIp(clientIp)));
        }
        if (organizerEmail != null && !organizerEmail.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(auditService.getByOrganizerEmail(organizerEmail)));
        }
        return ResponseEntity.ok(ApiResponse.success(auditService.getAll(outcome)));
    }

    private String checkAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        Claims claims = (Claims) auth.getCredentials();
        Boolean isAdmin = claims.get("isAdmin", Boolean.class);
        if (!Boolean.TRUE.equals(isAdmin)) {
            throw new com.cax.cax_backend.common.exception.AuthException.AdminOnlyException();
        }
        return (String) auth.getPrincipal();
    }
}
