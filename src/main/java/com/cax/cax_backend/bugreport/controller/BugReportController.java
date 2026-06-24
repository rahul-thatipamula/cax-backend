package com.cax.cax_backend.bugreport.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cax.cax_backend.common.annotation.AdminActivityLog;
import com.cax.cax_backend.bugreport.model.BugReport;
import com.cax.cax_backend.bugreport.service.BugReportService;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.enums.BugReportEnums.BugSeverity;
import com.cax.cax_backend.common.enums.BugReportEnums.BugStatus;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/bug-reports")
@RequiredArgsConstructor
public class BugReportController {

    private final BugReportService bugReportService;

    @PostMapping
    public ResponseEntity<ApiResponse<BugReport>> submitBugReport(
            Authentication auth,
            @RequestBody Map<String, Object> body) {
        String userId = (String) auth.getPrincipal();
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        String stepsToReproduce = (String) body.getOrDefault("stepsToReproduce", "");
        String category = (String) body.getOrDefault("category", "General");
        String severity = (String) body.getOrDefault("severity", "MEDIUM");
        @SuppressWarnings("unchecked")
        Map<String, String> environment = (Map<String, String>) body.getOrDefault("environment", Map.of());

        BugReport report = bugReportService.submitBugReport(
                userId, title, description, stepsToReproduce,
                category, severity, environment);

        return ResponseEntity.ok(ApiResponse.created("Bug report submitted successfully", report));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BugReport>>> getUserBugReports(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        List<BugReport> reports = bugReportService.getUserBugReports(userId);
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BugReport>> getBugReport(
            Authentication auth,
            @PathVariable String id) {
        String userId = (String) auth.getPrincipal();
        BugReport report = bugReportService.getBugReportById(id);
        boolean isAdmin = Boolean.TRUE.equals(((Claims) auth.getCredentials()).get("isAdmin", Boolean.class));
        if (!isAdmin && !report.getUserId().equals(userId)) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("You do not have access to this bug report.");
        }
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/admin/all")
    @AdminActivityLog(action = "List All Bug Reports")
    public ResponseEntity<ApiResponse<List<BugReport>>> getAllBugReports(Authentication auth) {
        checkAdmin(auth);
        List<BugReport> reports = bugReportService.getAllBugReports();
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    @GetMapping("/admin/stats")
    @AdminActivityLog(action = "View Bug Report Stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatistics(Authentication auth) {
        checkAdmin(auth);
        Map<String, Object> stats = bugReportService.getBugReportStatistics();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @PutMapping("/{id}/status")
    @AdminActivityLog(action = "Update Bug Status", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<BugReport>> updateStatus(
            Authentication auth,
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        checkAdmin(auth);
        String status = body.get("status");
        String resolution = body.getOrDefault("resolution", "");
        BugReport report = bugReportService.updateBugReportStatus(
                id, BugStatus.valueOf(status.toUpperCase()), resolution);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @PutMapping("/{id}/severity")
    @AdminActivityLog(action = "Update Bug Severity", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<BugReport>> updateSeverity(
            Authentication auth,
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        checkAdmin(auth);
        String severity = body.get("severity");
        BugReport report = bugReportService.updateBugReportSeverity(
                id, BugSeverity.valueOf(severity.toUpperCase()));
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @PutMapping("/{id}/assign")
    @AdminActivityLog(action = "Assign Bug Report", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<BugReport>> assignBugReport(
            Authentication auth,
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        checkAdmin(auth);
        String assigneeId = body.get("assigneeId");
        BugReport report = bugReportService.assignBugReport(id, assigneeId);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @DeleteMapping("/{id}")
    @AdminActivityLog(action = "Delete Bug Report", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<Void>> deleteBugReport(
            Authentication auth,
            @PathVariable String id) {
        checkAdmin(auth);
        bugReportService.deleteBugReport(id);
        return ResponseEntity.ok(ApiResponse.success("Bug report deleted successfully"));
    }

    private void checkAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        Claims claims = (Claims) auth.getCredentials();
        Boolean isAdmin = claims.get("isAdmin", Boolean.class);
        if (!Boolean.TRUE.equals(isAdmin)) {
            throw new com.cax.cax_backend.common.exception.AuthException.AdminOnlyException();
        }
    }
}
