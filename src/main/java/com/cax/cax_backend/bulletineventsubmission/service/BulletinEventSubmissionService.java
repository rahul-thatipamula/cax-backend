package com.cax.cax_backend.bulletineventsubmission.service;

import com.cax.cax_backend.bulletinevent.model.BulletinEvent;
import com.cax.cax_backend.bulletinevent.service.BulletinEventService;
import com.cax.cax_backend.bulletineventsubmission.model.BulletinEventSubmission;
import com.cax.cax_backend.bulletineventsubmission.model.BulletinEventSubmission.SubmissionStatus;
import com.cax.cax_backend.bulletineventsubmission.repository.BulletinEventSubmissionRepository;
import com.cax.cax_backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulletinEventSubmissionService {

    private final BulletinEventSubmissionRepository repository;
    private final BulletinEventService bulletinEventService;

    // ── Public (organizer-facing) ────────────────────────────────────────────

    /** Accepts an organizer's raw submission and stores it as PENDING, regardless of any
     *  status/id fields the client may have sent — those are always server-controlled. */
    public BulletinEventSubmission submit(BulletinEventSubmission incoming) {
        if (incoming.getTitle() == null || incoming.getTitle().isBlank()) {
            throw new BusinessException.BadRequestException("Title is required");
        }
        if (incoming.getDescription() == null || incoming.getDescription().isBlank()) {
            throw new BusinessException.BadRequestException("Description is required");
        }
        if (incoming.getExternalLink() == null || incoming.getExternalLink().isBlank()) {
            throw new BusinessException.BadRequestException("External link is required");
        }
        if (incoming.getOrganizerName() == null || incoming.getOrganizerName().isBlank()) {
            throw new BusinessException.BadRequestException("Organizer name is required");
        }
        if (incoming.getOrganizerEmail() == null || incoming.getOrganizerEmail().isBlank()) {
            throw new BusinessException.BadRequestException("Organizer email is required");
        }
        // A non-global event with no colleges would be visible to nobody — reject rather than
        // silently store an event no student can ever see.
        if (!incoming.isGlobal() && (incoming.getCollegeIds() == null || incoming.getCollegeIds().isEmpty())) {
            throw new BusinessException.BadRequestException(
                    "A college-specific event must target at least one college");
        }

        BulletinEventSubmission submission = BulletinEventSubmission.builder()
                .title(incoming.getTitle().trim())
                .description(incoming.getDescription().trim())
                .coverImage(incoming.getCoverImage())
                .externalLink(incoming.getExternalLink().trim())
                .eventStartDate(incoming.getEventStartDate())
                .eventEndDate(incoming.getEventEndDate())
                .registrationEndDate(incoming.getRegistrationEndDate())
                .conductedBy(incoming.getConductedBy())
                // Targeting comes from the organizer (validated upstream against real colleges);
                // an admin can still re-scope it at approval time.
                .global(incoming.isGlobal())
                .collegeIds(incoming.isGlobal() ? null : incoming.getCollegeIds())
                .organizerName(incoming.getOrganizerName().trim())
                .organizerEmail(incoming.getOrganizerEmail().trim())
                .organizerPhone(incoming.getOrganizerPhone())
                .organizerCollege(incoming.getOrganizerCollege())
                .status(SubmissionStatus.PENDING)
                .submittedAt(Instant.now())
                .build();

        submission = repository.save(submission);
        log.info("Bulletin event submission received: id={} title='{}' organizer={}",
                submission.getId(), submission.getTitle(), submission.getOrganizerEmail());
        return submission;
    }

    // ── Admin ─────────────────────────────────────────────────────────────

    public List<BulletinEventSubmission> getAll(String status) {
        if (status == null || status.isBlank()) {
            return repository.findAllByOrderBySubmittedAtDesc();
        }
        return repository.findByStatusOrderBySubmittedAtDesc(SubmissionStatus.valueOf(status.toUpperCase()));
    }

    public Map<String, Long> getStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("pending", repository.countByStatus(SubmissionStatus.PENDING));
        stats.put("approved", repository.countByStatus(SubmissionStatus.APPROVED));
        stats.put("rejected", repository.countByStatus(SubmissionStatus.REJECTED));
        stats.put("total", repository.count());
        return stats;
    }

    /** Promotes a pending submission into a live BulletinEvent (via the existing
     *  BulletinEventService, so it gets the same active/deleted/score-init handling as
     *  an admin-authored event) and marks the submission APPROVED. */
    public BulletinEventSubmission approve(String id, String adminUserId) {
        BulletinEventSubmission submission = repository.findById(id)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Bulletin event submission"));
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            throw new BusinessException.BadRequestException("Only pending submissions can be approved");
        }

        BulletinEvent event = BulletinEvent.builder()
                .title(submission.getTitle())
                .description(submission.getDescription())
                .coverImage(submission.getCoverImage())
                .externalLink(submission.getExternalLink())
                .eventStartDate(submission.getEventStartDate())
                .eventEndDate(submission.getEventEndDate())
                .registrationEndDate(submission.getRegistrationEndDate())
                .conductedBy(submission.getConductedBy())
                .global(submission.isGlobal())
                .collegeIds(submission.getCollegeIds())
                .build();
        BulletinEvent saved = bulletinEventService.createBulletinEvent(event);

        Instant now = Instant.now();
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewedAt(now);
        submission.setReviewedBy(adminUserId);
        submission.setLinkedBulletinEventId(saved.getId());
        submission = repository.save(submission);

        log.info("Bulletin event submission {} approved by {} -> bulletin event {}",
                id, adminUserId, saved.getId());
        return submission;
    }

    public BulletinEventSubmission reject(String id, String adminUserId, String reason) {
        BulletinEventSubmission submission = repository.findById(id)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Bulletin event submission"));
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            throw new BusinessException.BadRequestException("Only pending submissions can be rejected");
        }

        Instant now = Instant.now();
        submission.setStatus(SubmissionStatus.REJECTED);
        submission.setReviewedAt(now);
        submission.setReviewedBy(adminUserId);
        submission.setRejectionReason(reason != null && !reason.isBlank() ? reason : "Not accepted for the CAX bulletin");
        submission = repository.save(submission);

        log.info("Bulletin event submission {} rejected by {}", id, adminUserId);
        return submission;
    }
}
