package com.cax.cax_backend.bugreport.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cax.cax_backend.bugreport.model.BugReport;
import com.cax.cax_backend.bugreport.repository.BugReportRepository;
import com.cax.cax_backend.common.enums.BugReportEnums.BugSeverity;
import com.cax.cax_backend.common.enums.BugReportEnums.BugStatus;
import com.cax.cax_backend.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BugReportService {

    private final BugReportRepository bugReportRepository;

    /**
     * Submit a new bug report
     */
    public BugReport submitBugReport(String userId, String title, String description, 
                                     String stepsToReproduce, String category, 
                                     String severity, Map<String, String> environment) {
        BugReport report = BugReport.builder()
                .userId(userId)
                .title(title)
                .description(description)
                .stepsToReproduce(stepsToReproduce)
                .category(category != null ? category : "General")
                .severity(severity != null ? BugSeverity.valueOf(severity.toUpperCase()) : BugSeverity.MEDIUM)
                .status(BugStatus.OPEN)
                .environment(environment)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        return bugReportRepository.save(report);
    }

    /**
     * Get all bug reports submitted by a user
     */
    public List<BugReport> getUserBugReports(String userId) {
        return bugReportRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Get a specific bug report by ID
     */
    public BugReport getBugReportById(String reportId) {
        return bugReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Bug Report", reportId));
    }

    /**
     * Get all bug reports (admin only)
     */
    public List<BugReport> getAllBugReports() {
        return bugReportRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Get bug reports by status
     */
    public List<BugReport> getBugReportsByStatus(BugStatus status) {
        return bugReportRepository.findByStatus(status);
    }

    /**
     * Update bug report status (admin only)
     */
    public BugReport updateBugReportStatus(String reportId, BugStatus newStatus, String resolution) {
        BugReport report = getBugReportById(reportId);
        report.setStatus(newStatus);
        report.setResolution(resolution);
        report.setUpdatedAt(Instant.now());
        
        if (newStatus == BugStatus.RESOLVED) {
            report.setResolvedAt(Instant.now());
        }
        
        return bugReportRepository.save(report);
    }

    /**
     * Update bug report severity (admin only)
     */
    public BugReport updateBugReportSeverity(String reportId, BugSeverity severity) {
        BugReport report = getBugReportById(reportId);
        report.setSeverity(severity);
        report.setUpdatedAt(Instant.now());
        return bugReportRepository.save(report);
    }

    /**
     * Assign bug report to a user (admin only)
     */
    public BugReport assignBugReport(String reportId, String assigneeId) {
        BugReport report = getBugReportById(reportId);
        report.setAssignedTo(assigneeId);
        report.setUpdatedAt(Instant.now());
        return bugReportRepository.save(report);
    }

    /**
     * Delete a bug report (admin only)
     */
    public void deleteBugReport(String reportId) {
        bugReportRepository.deleteById(reportId);
    }

    /**
     * Count open bug reports
     */
    public long getOpenBugReportsCount() {
        return bugReportRepository.findByStatus(BugStatus.OPEN).size();
    }

    /**
     * Get statistics on bug reports
     */
    public Map<String, Object> getBugReportStatistics() {
        long totalReports = bugReportRepository.count();
        long openReports = bugReportRepository.findByStatus(BugStatus.OPEN).size();
        long resolvedReports = bugReportRepository.findByStatus(BugStatus.RESOLVED).size();
        
        return Map.of(
                "total", totalReports,
                "open", openReports,
                "resolved", resolvedReports,
                "pending", totalReports - openReports - resolvedReports
        );
    }
}
