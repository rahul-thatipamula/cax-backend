package com.cax.cax_backend.studentpost.service;

import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.studentpost.dto.ReportedPostDetailDto;
import com.cax.cax_backend.studentpost.model.StudentPost;
import com.cax.cax_backend.studentpost.model.ThoughtReport;
import com.cax.cax_backend.studentpost.repository.StudentPostRepository;
import com.cax.cax_backend.studentpost.repository.ThoughtReportRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.cax.cax_backend.studentpost.event.StudentPostDisabledEvent;

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
    private final StudentPostRepository studentPostRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${cax.thoughts.report-threshold:3}")
    private int reportThreshold;

    public void reportPost(String reporterUserId, String postId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException.BadRequestException("Reason for reporting must not be empty");
        }

        StudentPost post = studentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("StudentPost", postId));

        if (post.isDisabled()) {
            throw new BusinessException.BadRequestException("This thought is already disabled.");
        }

        Optional<ThoughtReport> existingReport = thoughtReportRepository.findByPostIdAndReporterUserId(postId, reporterUserId);
        if (existingReport.isPresent()) {
            throw new BusinessException.BadRequestException("You have already reported this thought.");
        }

        User reporter = userService.getUserByUserId(reporterUserId);

        ThoughtReport report = ThoughtReport.builder()
                .postId(postId)
                .reporterUserId(reporterUserId)
                .reporterName(reporter.getName())
                .reporterEmail(reporter.getEmail())
                .reason(reason.trim())
                .build();

        thoughtReportRepository.save(report);
        log.info("User {} reported post {}. Reason: {}", reporterUserId, postId, reason);

        long reportCount = thoughtReportRepository.countByPostId(postId);
        if (reportCount >= reportThreshold) {
            post.setDisabled(true);
            StudentPost saved = studentPostRepository.save(post);
            log.warn("Post {} automatically disabled. Report count ({}) reached threshold ({})", postId, reportCount, reportThreshold);
            eventPublisher.publishEvent(new StudentPostDisabledEvent(this, saved));
        }
    }

    public List<ReportedPostDetailDto> getReportedPostsForAdmin() {
        List<ThoughtReport> allReports = thoughtReportRepository.findAllByOrderByReportedAtDesc();
        
        Map<String, List<ThoughtReport>> reportsByPost = allReports.stream()
                .collect(Collectors.groupingBy(ThoughtReport::getPostId));

        return reportsByPost.entrySet().stream()
                .map(entry -> {
                    String postId = entry.getKey();
                    List<ThoughtReport> reports = entry.getValue();
                    StudentPost post = studentPostRepository.findById(postId).orElse(null);
                    if (post == null) {
                        return null; // Skip if post was deleted entirely
                    }
                    return ReportedPostDetailDto.builder()
                            .post(post)
                            .reportCount(reports.size())
                            .reports(reports)
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Long.compare(b.getReportCount(), a.getReportCount()))
                .collect(Collectors.toList());
    }

    public void dismissReports(String postId) {
        // Dismissing reports clears them and activates the post if it was auto-disabled
        thoughtReportRepository.deleteByPostId(postId);
        studentPostRepository.findById(postId).ifPresent(post -> {
            if (post.isDisabled()) {
                post.setDisabled(false);
                studentPostRepository.save(post);
                log.info("Post {} re-enabled after reports dismissal", postId);
            }
        });
    }
}
