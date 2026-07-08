package com.cax.cax_backend.collegereport.service;

import com.cax.cax_backend.collegereport.model.CollegeReport;
import com.cax.cax_backend.collegereport.model.CollegeReport.ReportStatus;
import com.cax.cax_backend.collegereport.repository.CollegeReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollegeReportService {

    private final CollegeReportRepository repository;

    public CollegeReport createReport(String email, String domain, String name,
                                      String picture, String detectedCollegeName,
                                      String detectedCollegeId, String reason) {
        if (repository.existsByEmailAndStatus(email, ReportStatus.PENDING)) {
            log.info("Duplicate college report ignored for email={}", email);
            return null;
        }
        CollegeReport report = CollegeReport.builder()
                .email(email)
                .domain(domain)
                .name(name)
                .picture(picture)
                .detectedCollegeName(detectedCollegeName)
                .detectedCollegeId(detectedCollegeId)
                .reason(reason)
                .status(ReportStatus.PENDING)
                .build();
        report = repository.save(report);
        log.info("CollegeReport created for email={} domain={}", email, domain);
        return report;
    }

    public List<CollegeReport> getAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public List<CollegeReport> getByStatus(ReportStatus status) {
        return repository.findByStatusOrderByCreatedAtDesc(status);
    }

    public Map<String, Long> getStats() {
        return Map.of(
                "pending", repository.countByStatus(ReportStatus.PENDING),
                "resolved", repository.countByStatus(ReportStatus.RESOLVED),
                "dismissed", repository.countByStatus(ReportStatus.DISMISSED),
                "total", repository.count()
        );
    }

    public CollegeReport resolve(String id, String adminNote) {
        CollegeReport report = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report not found"));
        if (report.isDeleted()) {
            throw new RuntimeException("Report not found");
        }
        report.setStatus(ReportStatus.RESOLVED);
        report.setResolvedAt(Instant.now());
        report.setAdminNote(adminNote);
        return repository.save(report);
    }

    public CollegeReport dismiss(String id, String adminNote) {
        CollegeReport report = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report not found"));
        if (report.isDeleted()) {
            throw new RuntimeException("Report not found");
        }
        report.setStatus(ReportStatus.DISMISSED);
        report.setResolvedAt(Instant.now());
        report.setAdminNote(adminNote);
        return repository.save(report);
    }

    public boolean hasPendingReport(String email) {
        return repository.existsByEmailAndStatus(email, ReportStatus.PENDING);
    }

    public void delete(String id) {
        CollegeReport report = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report not found"));
        if (report.isDeleted()) {
            throw new RuntimeException("Report not found");
        }
        report.setDeleted(true);
        report.setDeletedAt(Instant.now());
        repository.save(report);
    }
}
