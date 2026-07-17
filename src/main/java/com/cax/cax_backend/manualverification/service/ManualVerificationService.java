package com.cax.cax_backend.manualverification.service;

import com.cax.cax_backend.college.model.College;
import com.cax.cax_backend.college.repository.CollegeRepository;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.common.service.R2StorageService;
import com.cax.cax_backend.common.util.EncryptionUtils;
import com.cax.cax_backend.manualverification.model.ManualVerification;
import com.cax.cax_backend.manualverification.model.ManualVerification.VerificationStatus;
import com.cax.cax_backend.manualverification.repository.ManualVerificationRepository;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.user.event.CollegeSelectedEvent;
import com.cax.cax_backend.user.model.CollegeDetails;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualVerificationService {

    // Yearly re-verification anchor: every July 19, Asia/Kolkata.
    private static final MonthDay REVERIFY_ANCHOR = MonthDay.of(7, 19);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Shortest plausible student ID number (after whitespace stripped) — guards
    // against accidental single-digit/near-empty entries defeating dedup.
    private static final int MIN_STUDENT_ID_LENGTH = 4;

    private final ManualVerificationRepository repository;
    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final R2StorageService r2StorageService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    /** Next July 19 (IST) strictly after the given instant. */
    public static Instant nextValidUntil(Instant from) {
        LocalDate date = from.atZone(IST).toLocalDate();
        LocalDate anchor = REVERIFY_ANCHOR.atYear(date.getYear());
        if (!anchor.isAfter(date)) {
            anchor = REVERIFY_ANCHOR.atYear(date.getYear() + 1);
        }
        return anchor.atStartOfDay(IST).toInstant();
    }

    // ── User-facing ───────────────────────────────────────────────────────

    public ManualVerification submit(String userId, String collegeId, String idCardUrl, String idCardHash) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("User"));

        if (idCardUrl == null || idCardUrl.isBlank()) {
            throw new BusinessException.BadRequestException("ID card image is required");
        }
        if (collegeId == null || collegeId.isBlank()) {
            throw new BusinessException.BadRequestException("College selection is required");
        }
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("College"));

        Optional<ManualVerification> latest = repository.findFirstByUserIdOrderBySubmittedAtDesc(userId);
        if (latest.isPresent() && latest.get().getStatus() == VerificationStatus.PENDING) {
            throw new BusinessException.BadRequestException("A verification request is already under review");
        }
        boolean isReverify = "REVERIFY_REQUIRED".equals(user.getManualVerificationStatus())
                || "EXPIRED".equals(user.getManualVerificationStatus());
        if (!isReverify && user.isIdVerified()
                && user.getVerificationMethod() != User.VerificationMethod.MANUAL_ID_CARD) {
            throw new BusinessException.BadRequestException("Account is already verified via college email");
        }

        // Duplicate ID-card detection: same image hash submitted by another account.
        List<String> duplicateUsers = new ArrayList<>();
        if (idCardHash != null && !idCardHash.isBlank()) {
            duplicateUsers = repository.findByIdCardHash(idCardHash.trim()).stream()
                    .map(ManualVerification::getUserId)
                    .filter(u -> !userId.equals(u))
                    .distinct()
                    .collect(Collectors.toList());
        }

        ManualVerification record = ManualVerification.builder()
                .userId(userId)
                .email(user.getEmail())
                .name(user.getDisplayName())
                .picture(user.getPicture())
                .collegeId(college.getId())
                .collegeName(college.getCollegeName())
                .idCardUrlEncrypted(EncryptionUtils.encrypt(idCardUrl.trim()))
                .idCardHash(idCardHash != null ? idCardHash.trim() : null)
                .flaggedDuplicate(!duplicateUsers.isEmpty())
                .duplicateUserIds(duplicateUsers.isEmpty() ? null : duplicateUsers)
                .reverifyOfRecordId(isReverify && latest.isPresent() ? latest.get().getId() : null)
                .build();
        record = repository.save(record);

        user.setManualVerificationStatus("PENDING");
        user.setVerificationSubmittedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        if (!duplicateUsers.isEmpty()) {
            log.warn("Manual verification {} flagged: idCardHash already submitted by users {}",
                    record.getId(), duplicateUsers);
        }
        log.info("Manual verification submitted: user={} college={} record={}", userId, college.getCollegeName(), record.getId());
        return record;
    }

    public Map<String, Object> getMyStatus(String userId) {
        Optional<ManualVerification> latest = repository.findFirstByUserIdOrderBySubmittedAtDesc(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (latest.isEmpty()) {
            result.put("status", "NOT_SUBMITTED");
            return result;
        }
        ManualVerification r = latest.get();
        result.put("status", r.getStatus().name());
        result.put("collegeId", r.getCollegeId());
        result.put("collegeName", r.getCollegeName());
        result.put("submittedAt", r.getSubmittedAt());
        result.put("reviewedAt", r.getReviewedAt());
        result.put("rejectionReason", r.getRejectionReason());
        result.put("validUntil", r.getValidUntil());
        return result;
    }

    /** Every verification record the user has ever submitted, newest first — permanent audit trail. */
    public List<ManualVerification> getMyHistory(String userId) {
        return repository.findByUserIdOrderBySubmittedAtDesc(userId);
    }

    // ── Admin ─────────────────────────────────────────────────────────────

    public List<ManualVerification> getAll(String status) {
        if (status == null || status.isBlank()) {
            return repository.findAllByOrderBySubmittedAtDesc();
        }
        return repository.findByStatusOrderBySubmittedAtDesc(VerificationStatus.valueOf(status.toUpperCase()));
    }

    public Map<String, Long> getStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("pending", repository.countByStatus(VerificationStatus.PENDING));
        stats.put("approved", repository.countByStatus(VerificationStatus.APPROVED));
        stats.put("rejected", repository.countByStatus(VerificationStatus.REJECTED));
        stats.put("expired", repository.countByStatus(VerificationStatus.EXPIRED));
        stats.put("total", repository.count());
        return stats;
    }

    /** Short-lived presigned URL so an admin can view the (privately stored) ID card. */
    public String getIdCardViewUrl(String recordId) {
        ManualVerification record = repository.findById(recordId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Verification record"));
        String url = EncryptionUtils.decrypt(record.getIdCardUrlEncrypted());
        return r2StorageService.generatePresignedGetUrl(url);
    }

    public ManualVerification approve(String recordId, String adminUserId, String collegeIdOverride, String note,
                                       String studentIdNumber) {
        ManualVerification record = repository.findById(recordId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Verification record"));
        if (record.getStatus() != VerificationStatus.PENDING) {
            throw new BusinessException.BadRequestException("Only pending requests can be approved");
        }

        String collegeId = (collegeIdOverride != null && !collegeIdOverride.isBlank())
                ? collegeIdOverride : record.getCollegeId();
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("College"));

        User user = userRepository.findByUserId(record.getUserId())
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("User"));

        // Student ID number is mandatory on approval — it's the only signal used
        // for cross-account duplicate detection, so a missing/short value would
        // silently defeat that check.
        if (studentIdNumber == null || studentIdNumber.isBlank()) {
            throw new BusinessException.BadRequestException("Student ID number is required to approve");
        }
        String normalized = studentIdNumber.trim().toUpperCase().replaceAll("\\s+", "");
        if (normalized.length() < MIN_STUDENT_ID_LENGTH) {
            throw new BusinessException.BadRequestException(
                    "Student ID number looks too short — please re-check the ID card");
        }

        // Duplicate ID-number detection: same student ID (keyed hash) already
        // approved under another account — admin enters this, never the student.
        String hash = EncryptionUtils.hmacSHA256(normalized);
        List<String> duplicateUsers = repository.findByStudentIdNumberHash(hash).stream()
                .map(ManualVerification::getUserId)
                .filter(u -> !user.getUserId().equals(u))
                .distinct()
                .collect(Collectors.toList());
        record.setStudentIdNumberHash(hash);
        record.setFlaggedDuplicateIdNumber(!duplicateUsers.isEmpty());
        record.setDuplicateIdNumberUserIds(duplicateUsers.isEmpty() ? null : duplicateUsers);
        if (!duplicateUsers.isEmpty()) {
            log.warn("Manual verification {} flagged: studentIdNumberHash already used by users {}",
                    record.getId(), duplicateUsers);
        }

        Instant now = Instant.now();
        record.setStatus(VerificationStatus.APPROVED);
        record.setReviewedAt(now);
        record.setReviewedBy(adminUserId);
        record.setAdminNote(note);
        record.setCollegeId(college.getId());
        record.setCollegeName(college.getCollegeName());
        record.setValidUntil(nextValidUntil(now));
        record = repository.save(record);

        boolean firstAssignment = user.getCollegeDetails() == null;
        user.setCollegeDetailsAdded(true);
        user.setCollegeDetails(CollegeDetails.builder()
                .collegeId(college.getId())
                .collegeName(college.getCollegeName())
                .collegeCode(college.getCollegeCode())
                .location(college.getLocation())
                .build());
        if (firstAssignment || user.getCollegeAddedAt() == null) {
            user.setCollegeAddedAt(now);
        }
        user.setIdVerified(true);
        user.setVerificationMethod(User.VerificationMethod.MANUAL_ID_CARD);
        user.setManualVerificationStatus("APPROVED");
        user.setVerificationValidUntil(record.getValidUntil());
        user.setUpdatedAt(now);
        userRepository.save(user);
        eventPublisher.publishEvent(new CollegeSelectedEvent(this, user));

        notifyUser(user, "Verification Approved",
                "Your enrollment verification was approved. Welcome to " + college.getCollegeName() + " on CAX.");
        log.info("Manual verification {} approved by {} → user {} assigned to {}",
                recordId, adminUserId, user.getUserId(), college.getCollegeName());
        return record;
    }

    public ManualVerification reject(String recordId, String adminUserId, String reason) {
        ManualVerification record = repository.findById(recordId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Verification record"));
        if (record.getStatus() != VerificationStatus.PENDING) {
            throw new BusinessException.BadRequestException("Only pending requests can be rejected");
        }
        Instant now = Instant.now();
        record.setStatus(VerificationStatus.REJECTED);
        record.setReviewedAt(now);
        record.setReviewedBy(adminUserId);
        record.setRejectionReason(reason != null && !reason.isBlank() ? reason : "ID card could not be verified");
        record = repository.save(record);

        userRepository.findByUserId(record.getUserId()).ifPresent(user -> {
            // A rejected re-verification shouldn't strip a still-valid approval.
            if (!user.isIdVerified() || user.getVerificationMethod() == User.VerificationMethod.MANUAL_ID_CARD) {
                user.setManualVerificationStatus("REJECTED");
            }
            user.setUpdatedAt(now);
            userRepository.save(user);
            notifyUser(user, "Verification Update",
                    "Your enrollment verification was unsuccessful: " + recordFinalReason(reason) + " You may submit a new photo for review.");
        });
        log.info("Manual verification {} rejected by {}", recordId, adminUserId);
        return record;
    }

    private String recordFinalReason(String reason) {
        return (reason != null && !reason.isBlank()) ? reason : "the photo was unclear or invalid.";
    }

    private void notifyUser(User user, String title, String body) {
        try {
            notificationService.createNotification(user.getUserId(), title, body,
                    NotificationType.SYSTEM, new HashMap<>());
        } catch (Exception e) {
            log.error("Failed to notify user {} about verification update: {}", user.getUserId(), e.getMessage());
        }
    }

    // ── Admin: manual re-verification trigger ────────────────────────────────

    /**
     * Force a currently-verified manual-track user to re-verify (suspected
     * fraud, compromised ID, etc.) — the same state the yearly scheduler
     * puts them in, but on demand. Access is not revoked; the user keeps
     * using the app until they resubmit and are reviewed, or until this is
     * escalated (they're simply asked again, same as the July 19 cycle).
     */
    public User requestReverification(String userId, String adminUserId, String reason) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("User"));
        if (user.getVerificationMethod() != User.VerificationMethod.MANUAL_ID_CARD || !user.isIdVerified()) {
            throw new BusinessException.BadRequestException(
                    "Only a currently manually-verified user can be asked to re-verify");
        }
        if ("REVERIFY_REQUIRED".equals(user.getManualVerificationStatus())) {
            return user;
        }
        user.setManualVerificationStatus("REVERIFY_REQUIRED");
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        notifyUser(user, "Re-Verification Requested",
                (reason != null && !reason.isBlank())
                        ? "Please re-upload your enrollment documentation: " + reason
                        : "Please re-upload your current enrollment documentation to maintain full access.");
        log.info("Manual re-verification requested for user {} by admin {}", userId, adminUserId);
        return user;
    }

    // ── Yearly re-verification (called by the scheduler) ────────────────────

    /** Remind users whose approval expires within the next 7 days (at most every 3 days). */
    public void sendExpiryReminders() {
        Instant now = Instant.now();
        List<ManualVerification> expiring = repository.findByStatusAndValidUntilBetween(
                VerificationStatus.APPROVED, now, now.plusSeconds(7L * 24 * 3600));
        for (ManualVerification record : expiring) {
            if (record.getLastReminderSentAt() != null
                    && record.getLastReminderSentAt().isAfter(now.minusSeconds(3L * 24 * 3600))) {
                continue;
            }
            userRepository.findByUserId(record.getUserId()).ifPresent(user ->
                    notifyUser(user, "Annual Verification Due Soon",
                            "Your student enrollment verification is set to expire on July 19. Please submit your current documentation to maintain full access."));
            record.setLastReminderSentAt(now);
            repository.save(record);
        }
        if (!expiring.isEmpty()) {
            log.info("Sent {} manual-verification expiry reminders", expiring.size());
        }
    }

    /**
     * Handle approvals past July 19. Grace period of 30 days: the user keeps
     * access (never interrupted mid-session) but is asked to re-verify; after
     * the grace period the account drops back to unverified and is routed to
     * the verification screen on next app open.
     */
    public void processExpiredApprovals() {
        Instant now = Instant.now();
        List<ManualVerification> due = repository.findByStatusAndValidUntilBefore(VerificationStatus.APPROVED, now);
        for (ManualVerification record : due) {
            boolean graceOver = record.getValidUntil().plusSeconds(30L * 24 * 3600).isBefore(now);
            Optional<User> userOpt = userRepository.findByUserId(record.getUserId());
            if (userOpt.isEmpty()) continue;
            User user = userOpt.get();

            if (graceOver) {
                record.setStatus(VerificationStatus.EXPIRED);
                repository.save(record);
                user.setIdVerified(false);
                user.setManualVerificationStatus("EXPIRED");
                user.setUpdatedAt(now);
                userRepository.save(user);
                notifyUser(user, "Verification Expired",
                        "Your student enrollment verification has expired. Please submit current documentation to restore full access.");
                log.info("Manual verification expired for user {}", user.getUserId());
            } else if (!"REVERIFY_REQUIRED".equals(user.getManualVerificationStatus())) {
                user.setManualVerificationStatus("REVERIFY_REQUIRED");
                user.setUpdatedAt(now);
                userRepository.save(user);
                notifyUser(user, "Annual Verification Required",
                        "Please submit your current enrollment documentation. You have a 30-day grace period during which your access will continue uninterrupted.");
                log.info("Manual re-verification requested for user {}", user.getUserId());
            }
        }
    }
}
