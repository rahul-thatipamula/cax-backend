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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/bug-reports")
@RequiredArgsConstructor
public class BugReportController {
    
    private final BugReportService bugReportService;

    /**
     * POST /api/bug-reports - Submit a new bug report
     */
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

    /**
     * GET /api/bug-reports - Get all bug reports from current user
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BugReport>>> getUserBugReports(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        List<BugReport> reports = bugReportService.getUserBugReports(userId);
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    /**
     * GET /api/bug-reports/{id} - Get a specific bug report
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BugReport>> getBugReport(@PathVariable String id) {
        BugReport report = bugReportService.getBugReportById(id);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /**
     * GET /api/bug-reports/all - Get all bug reports (admin only)
     */
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<BugReport>>> getAllBugReports() {
        List<BugReport> reports = bugReportService.getAllBugReports();
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    /**
     * GET /api/bug-reports/admin/stats - Get bug report statistics
     */
    @GetMapping("/admin/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatistics() {
        Map<String, Object> stats = bugReportService.getBugReportStatistics();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * PUT /api/bug-reports/{id}/status - Update bug report status (admin only)
     */
    @PutMapping("/{id}/status")
    @AdminActivityLog(action = "Update Bug Status", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<BugReport>> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        String resolution = body.getOrDefault("resolution", "");
        
        BugReport report = bugReportService.updateBugReportStatus(
                id, BugStatus.valueOf(status.toUpperCase()), resolution);
        
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /**
     * PUT /api/bug-reports/{id}/severity - Update bug report severity (admin only)
     */
    @PutMapping("/{id}/severity")
    @AdminActivityLog(action = "Update Bug Severity", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<BugReport>> updateSeverity(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String severity = body.get("severity");
        BugReport report = bugReportService.updateBugReportSeverity(
                id, BugSeverity.valueOf(severity.toUpperCase()));
        
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /**
     * PUT /api/bug-reports/{id}/assign - Assign bug report (admin only)
     */
    @PutMapping("/{id}/assign")
    @AdminActivityLog(action = "Assign Bug Report", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<BugReport>> assignBugReport(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String assigneeId = body.get("assigneeId");
        BugReport report = bugReportService.assignBugReport(id, assigneeId);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /**
     * DELETE /api/bug-reports/{id} - Delete a bug report (admin only)
     */
    @DeleteMapping("/{id}")
    @AdminActivityLog(action = "Delete Bug Report", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<Void>> deleteBugReport(@PathVariable String id) {
        bugReportService.deleteBugReport(id);
        return ResponseEntity.ok(ApiResponse.success("Bug report deleted successfully"));
    }
}
