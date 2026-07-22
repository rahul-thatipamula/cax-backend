package com.cax.cax_backend.bulletinevent.service;

import com.cax.cax_backend.bulletinevent.event.BulletinEventCreatedEvent;
import com.cax.cax_backend.bulletinevent.model.BulletinEvent;
import com.cax.cax_backend.bulletinevent.model.BulletinEventScore;
import com.cax.cax_backend.bulletinevent.repository.BulletinEventRepository;
import com.cax.cax_backend.bulletinevent.repository.BulletinEventScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulletinEventService {

    private final BulletinEventRepository bulletinEventRepository;
    private final BulletinEventScoreRepository bulletinEventScoreRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Admin: all non-deleted bulletins (active + inactive). */
    public List<BulletinEvent> getAllBulletinEvents() {
        return bulletinEventRepository.findAllNotDeleted();
    }

    /** Student-facing: only active, non-deleted, scoped to college or global. */
    public List<BulletinEvent> getBulletinEventsForUser(String collegeId) {
        String cid = (collegeId == null || collegeId.isBlank()) ? "" : collegeId;
        return bulletinEventRepository.findActiveByGlobalOrCollegeId(cid);
    }

    /** Student-facing: active bulletins starting after now, scoped to college or global. */
    public List<BulletinEvent> getUpcomingBulletinEvents(String collegeId, Instant now) {
        String cid = (collegeId == null || collegeId.isBlank()) ? "" : collegeId;
        return bulletinEventRepository.findUpcoming(cid, now);
    }

    /** Student-facing: active bulletins currently in progress, scoped to college or global. */
    public List<BulletinEvent> getOngoingBulletinEvents(String collegeId, Instant now) {
        String cid = (collegeId == null || collegeId.isBlank()) ? "" : collegeId;
        return bulletinEventRepository.findOngoing(cid, now);
    }

    /** Student-facing: active bulletins that have already ended, scoped to college or global. */
    public List<BulletinEvent> getCompletedBulletinEvents(String collegeId, Instant now) {
        String cid = (collegeId == null || collegeId.isBlank()) ? "" : collegeId;
        return bulletinEventRepository.findCompleted(cid, now);
    }

    public Optional<BulletinEvent> getBulletinEventById(String id) {
        return bulletinEventRepository.findById(id);
    }

    /** Public-facing (caxone.in/postEvent): most recent live global bulletins, capped at {@code limit}. */
    public List<BulletinEvent> getTopGlobalBulletinEvents(int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 10));
        return bulletinEventRepository.findActiveGlobal().stream()
                .sorted(java.util.Comparator.comparing(BulletinEvent::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .limit(cappedLimit)
                .collect(java.util.stream.Collectors.toList());
    }

    private void validateDates(BulletinEvent event) {
        if (event.getEventStartDate() != null && event.getEventEndDate() != null) {
            if (event.getEventEndDate().isBefore(event.getEventStartDate())) {
                throw new IllegalArgumentException("Event end date cannot be before start date");
            }
        }
        if (event.getRegistrationEndDate() != null && event.getEventStartDate() != null) {
            if (event.getEventStartDate().isBefore(event.getRegistrationEndDate())) {
                throw new IllegalArgumentException("Registration deadline cannot be after event start date");
            }
        }
        if (event.getRegistrationEndDate() != null && event.getEventEndDate() != null) {
            if (event.getEventEndDate().isBefore(event.getRegistrationEndDate())) {
                throw new IllegalArgumentException("Registration deadline cannot be after event end date");
            }
        }
    }

    public BulletinEvent createBulletinEvent(BulletinEvent bulletinEvent) {
        validateDates(bulletinEvent);
        bulletinEvent.setCreatedAt(Instant.now());
        bulletinEvent.setUpdatedAt(Instant.now());
        bulletinEvent.setActive(true);
        bulletinEvent.setDeleted(false);
        BulletinEvent saved = bulletinEventRepository.save(bulletinEvent);
        initializeScore(saved.getId());

        try {
            eventPublisher.publishEvent(new BulletinEventCreatedEvent(this, saved));
        } catch (Exception e) {
            log.error("Failed to publish BulletinEventCreatedEvent for bulletin event: {}", saved.getId(), e);
        }

        return saved;
    }

    /** Creates the (always-zero) score tracking record for a new bulletin event, kept in its own collection. */
    private void initializeScore(String bulletinEventId) {
        Instant now = Instant.now();
        BulletinEventScore score = BulletinEventScore.builder()
                .bulletinEventId(bulletinEventId)
                .score(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        bulletinEventScoreRepository.save(score);
    }

    public BulletinEvent updateBulletinEvent(String id, BulletinEvent updateData) {
        validateDates(updateData);
        BulletinEvent existing = bulletinEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BulletinEvent not found"));

        existing.setTitle(updateData.getTitle());
        existing.setDescription(updateData.getDescription());
        existing.setCoverImage(updateData.getCoverImage());
        existing.setExternalLink(updateData.getExternalLink());
        existing.setEventStartDate(updateData.getEventStartDate());
        existing.setEventEndDate(updateData.getEventEndDate());
        existing.setRegistrationEndDate(updateData.getRegistrationEndDate());
        existing.setConductedBy(updateData.getConductedBy());
        existing.setGlobal(updateData.isGlobal());
        existing.setCollegeIds(updateData.getCollegeIds());
        existing.setUpdatedAt(Instant.now());

        return bulletinEventRepository.save(existing);
    }

    /** Toggle active/inactive visibility for students. */
    public BulletinEvent toggleActive(String id) {
        BulletinEvent existing = bulletinEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BulletinEvent not found"));
        existing.setActive(!existing.isActive());
        existing.setUpdatedAt(Instant.now());
        return bulletinEventRepository.save(existing);
    }

    /** Soft delete — sets deleted=true and records timestamp, never removes the document. */
    public void softDeleteBulletinEvent(String id) {
        BulletinEvent existing = bulletinEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BulletinEvent not found"));
        existing.setDeleted(true);
        existing.setActive(false);
        existing.setDeletedAt(Instant.now());
        bulletinEventRepository.save(existing);
    }
}

