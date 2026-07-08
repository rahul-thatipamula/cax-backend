package com.cax.cax_backend.thought.service;

import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.thought.dto.ReportedThoughtDetailDto;
import com.cax.cax_backend.thought.event.ThoughtDisabledEvent;
import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.model.ThoughtReport;
import com.cax.cax_backend.thought.repository.ThoughtRepository;
import com.cax.cax_backend.thought.repository.ThoughtReportRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThoughtReportService {

    private final ThoughtReportRepository thoughtReportRepository;
    private final ThoughtRepository thoughtRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${cax.thoughts.report-threshold:3}")
    private int reportThreshold;

    public void reportThought(String reporterUserId, String thoughtId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException.BadRequestException("Reason for reporting must not be empty");
        }

        Thought thought = thoughtRepository.findById(thoughtId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Thought", thoughtId));

        if (thought.isDeleted()) {
            throw new BusinessException.ResourceNotFoundException("Thought", thoughtId);
        }

        if (thought.isDisabled()) {
            throw new BusinessException.BadRequestException("This thought is already disabled.");
        }

        Optional<ThoughtReport> existing = thoughtReportRepository.findByPostIdAndReporterUserId(thoughtId, reporterUserId);
        if (existing.isPresent()) {
            throw new BusinessException.BadRequestException("You have already reported this thought.");
        }

        User reporter = userService.getUserByUserId(reporterUserId);

        ThoughtReport report = ThoughtReport.builder()
                .postId(thoughtId)
                .reporterUserId(reporterUserId)
                .reporterName(reporter.getName())
                .reporterEmail(reporter.getEmail())
                .reason(reason.trim())
                .build();

        thoughtReportRepository.save(report);
        log.info("User {} reported thought {}. Reason: {}", reporterUserId, thoughtId, reason);

        long reportCount = thoughtReportRepository.countByPostId(thoughtId);
        if (reportCount >= reportThreshold) {
            thought.setDisabled(true);
            Thought saved = thoughtRepository.save(thought);
            log.warn("Thought {} auto-disabled. Reports ({}) reached threshold ({})", thoughtId, reportCount, reportThreshold);
            eventPublisher.publishEvent(new ThoughtDisabledEvent(this, saved));
        }
    }

    public List<ReportedThoughtDetailDto> getReportedThoughtsForAdmin() {
        List<ThoughtReport> allReports = thoughtReportRepository.findAllByOrderByReportedAtDesc();

        Map<String, List<ThoughtReport>> reportsByThought = allReports.stream()
                .collect(Collectors.groupingBy(ThoughtReport::getPostId));

        return reportsByThought.entrySet().stream()
                .map(entry -> {
                    String thoughtId = entry.getKey();
                    List<ThoughtReport> reports = entry.getValue();
                    Thought thought = thoughtRepository.findById(thoughtId).orElse(null);
                    if (thought == null || thought.isDeleted()) return null;
                    return ReportedThoughtDetailDto.builder()
                            .thought(thought)
                            .reportCount(reports.size())
                            .reports(reports)
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Long.compare(b.getReportCount(), a.getReportCount()))
                .collect(Collectors.toList());
    }

    public void dismissReports(String thoughtId) {
        List<ThoughtReport> reports = thoughtReportRepository.findByPostId(thoughtId);
        Instant now = Instant.now();
        for (ThoughtReport report : reports) {
            report.setDeleted(true);
            report.setDeletedAt(now);
            thoughtReportRepository.save(report);
        }
        thoughtRepository.findById(thoughtId).ifPresent(thought -> {
            if (thought.isDisabled()) {
                thought.setDisabled(false);
                thoughtRepository.save(thought);
                log.info("Thought {} re-enabled after reports dismissed", thoughtId);
            }
        });
    }
}
