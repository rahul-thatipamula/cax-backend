package com.cax.cax_backend.manualverification.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.AuthException;
import com.cax.cax_backend.manualverification.model.ManualVerification;
import com.cax.cax_backend.manualverification.service.ManualVerificationService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manual-verifications")
@RequiredArgsConstructor
public class ManualVerificationController {

    private final ManualVerificationService service;

    // ── User endpoints ────────────────────────────────────────────────────

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<ManualVerification>> submit(
            Authentication auth,
            @RequestBody Map<String, String> body) {
        String userId = requireUser(auth);
        ManualVerification record = service.submit(
                userId,
                body.get("collegeId"),
                body.get("idCardUrl"),
                body.get("idCardHash"));
        return ResponseEntity.ok(ApiResponse.success("Verification submitted for review", record));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myStatus(Authentication auth) {
        String userId = requireUser(auth);
        return ResponseEntity.ok(ApiResponse.success(service.getMyStatus(userId)));
    }

    /** Every verification record the caller has ever submitted, newest first. */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ManualVerification>>> myHistory(Authentication auth) {
        String userId = requireUser(auth);
        return ResponseEntity.ok(ApiResponse.success(service.getMyHistory(userId)));
    }

    // ── Admin endpoints ───────────────────────────────────────────────────

    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<ManualVerification>>> getAll(
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

    @GetMapping("/admin/{id}/id-card")
    public ResponseEntity<ApiResponse<Map<String, String>>> getIdCard(
            @PathVariable String id,
            Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", service.getIdCardViewUrl(id))));
    }

    @PutMapping("/admin/{id}/approve")
    public ResponseEntity<ApiResponse<ManualVerification>> approve(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        String adminId = checkAdmin(auth);
        String collegeId = body != null ? body.get("collegeId") : null;
        String note = body != null ? body.getOrDefault("adminNote", "") : "";
        String studentIdNumber = body != null ? body.get("studentIdNumber") : null;
        return ResponseEntity.ok(ApiResponse.success(service.approve(id, adminId, collegeId, note, studentIdNumber)));
    }

    @PutMapping("/admin/{id}/reject")
    public ResponseEntity<ApiResponse<ManualVerification>> reject(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        String adminId = checkAdmin(auth);
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        return ResponseEntity.ok(ApiResponse.success(service.reject(id, adminId, reason)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String requireUser(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new AuthException.UnauthorizedException("Not authenticated");
        }
        return (String) auth.getPrincipal();
    }

    private String checkAdmin(Authentication auth) {
        String userId = requireUser(auth);
        Claims claims = (Claims) auth.getCredentials();
        Boolean isAdmin = claims.get("isAdmin", Boolean.class);
        if (!Boolean.TRUE.equals(isAdmin)) throw new AuthException.AdminOnlyException();
        return userId;
    }
}
