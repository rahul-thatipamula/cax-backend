package com.cax.cax_backend.bulletinevent.service;

import com.cax.cax_backend.bulletinevent.model.BulletinEvent;
import com.cax.cax_backend.bulletinevent.repository.BulletinEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BulletinEventService {

    private final BulletinEventRepository bulletinEventRepository;

    /** Admin: all non-deleted bulletins (active + inactive). */
    public List<BulletinEvent> getAllBulletinEvents() {
        return bulletinEventRepository.findAllNotDeleted();
    }

    /** Student-facing: only active, non-deleted, scoped to college or global. */
    public List<BulletinEvent> getBulletinEventsForUser(String collegeId) {
        String cid = (collegeId == null || collegeId.isBlank()) ? "" : collegeId;
        return bulletinEventRepository.findActiveByGlobalOrCollegeId(cid);
    }

    public Optional<BulletinEvent> getBulletinEventById(String id) {
        return bulletinEventRepository.findById(id);
    }

    public BulletinEvent createBulletinEvent(BulletinEvent bulletinEvent) {
        bulletinEvent.setCreatedAt(Instant.now());
        bulletinEvent.setUpdatedAt(Instant.now());
        bulletinEvent.setActive(true);
        bulletinEvent.setDeleted(false);
        return bulletinEventRepository.save(bulletinEvent);
    }

    public BulletinEvent updateBulletinEvent(String id, BulletinEvent updateData) {
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

