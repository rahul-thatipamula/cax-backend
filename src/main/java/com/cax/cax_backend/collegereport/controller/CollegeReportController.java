package com.cax.cax_backend.collegereport.controller;

import com.cax.cax_backend.collegereport.model.CollegeReport;
import com.cax.cax_backend.collegereport.model.CollegeReport.ReportStatus;
import com.cax.cax_backend.collegereport.service.CollegeReportService;
import com.cax.cax_backend.common.dto.ApiResponse;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/college-reports")
@RequiredArgsConstructor
public class CollegeReportController {

    private final CollegeReportService service;

    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<CollegeReport>>> getAll(
            @RequestParam(required = false) String status,
            Authentication auth) {
        checkAdmin(auth);
        List<CollegeReport> reports = status != null
                ? service.getByStatus(ReportStatus.valueOf(status.toUpperCase()))
                : service.getAll();
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    @GetMapping("/admin/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStats(Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(service.getStats()));
    }

    @PutMapping("/admin/{id}/resolve")
    public ResponseEntity<ApiResponse<CollegeReport>> resolve(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        checkAdmin(auth);
        String note = body != null ? body.getOrDefault("adminNote", "") : "";
        return ResponseEntity.ok(ApiResponse.success(service.resolve(id, note)));
    }

    @PutMapping("/admin/{id}/dismiss")
    public ResponseEntity<ApiResponse<CollegeReport>> dismiss(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        checkAdmin(auth);
        String note = body != null ? body.getOrDefault("adminNote", "") : "";
        return ResponseEntity.ok(ApiResponse.success(service.dismiss(id, note)));
    }

    @DeleteMapping("/admin/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String id,
            Authentication auth) {
        checkAdmin(auth);
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Report deleted"));
    }

    private void checkAdmin(Authentication auth) {
        if (auth == null) throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("Not authenticated");
        Claims claims = (Claims) auth.getCredentials();
        Boolean isAdmin = claims.get("isAdmin", Boolean.class);
        if (!Boolean.TRUE.equals(isAdmin)) throw new com.cax.cax_backend.common.exception.AuthException.AdminOnlyException();
    }
}
