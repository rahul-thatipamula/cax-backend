package com.cax.cax_backend.attendance.service;

import com.cax.cax_backend.attendance.dto.CreateTermRequest;
import com.cax.cax_backend.attendance.dto.SubjectRequest;
import com.cax.cax_backend.attendance.dto.UpdateTermRequest;
import com.cax.cax_backend.attendance.model.AttendanceSubject;
import com.cax.cax_backend.attendance.model.AttendanceTerm;
import com.cax.cax_backend.attendance.repository.AttendanceTermRepository;
import com.cax.cax_backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceTermService {

    /** Sane caps so a single account can't blow up a document / abuse storage. */
    private static final int MAX_TERMS_PER_USER = 30;
    private static final int MAX_SUBJECTS_PER_TERM = 40;
    private static final int MAX_NAME_LENGTH = 80;

    private final AttendanceTermRepository termRepository;

    public List<AttendanceTerm> listMine(String userId) {
        return termRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public AttendanceTerm createTerm(String userId, CreateTermRequest request) {
        String name = requireName(request.getName());

        if (termRepository.countByUserId(userId) >= MAX_TERMS_PER_USER) {
            throw new BusinessException.BadRequestException("Term limit reached (" + MAX_TERMS_PER_USER + ")");
        }

        boolean hasExisting = !termRepository.findByUserIdOrderByCreatedAtDesc(userId).isEmpty();

        AttendanceTerm term = AttendanceTerm.builder()
                .userId(userId)
                .name(name)
                .subjects(new ArrayList<>())
                .isActive(!hasExisting)
                .createdAt(Instant.now())
                .build();

        if (hasExisting) {
            deactivateAllExcept(userId, null);
            term.setActive(true);
        }

        return termRepository.save(term);
    }

    public AttendanceTerm renameTerm(String userId, String termId, UpdateTermRequest request) {
        AttendanceTerm term = requireOwnedTerm(userId, termId);
        term.setName(requireName(request.getName()));
        term.setUpdatedAt(Instant.now());
        return termRepository.save(term);
    }

    public AttendanceTerm activateTerm(String userId, String termId) {
        requireOwnedTerm(userId, termId);
        deactivateAllExcept(userId, termId);
        AttendanceTerm term = requireOwnedTerm(userId, termId);
        term.setActive(true);
        term.setUpdatedAt(Instant.now());
        return termRepository.save(term);
    }

    public void deleteTerm(String userId, String termId) {
        AttendanceTerm term = requireOwnedTerm(userId, termId);
        boolean wasActive = term.isActive();
        termRepository.deleteByIdAndUserId(termId, userId);

        if (wasActive) {
            List<AttendanceTerm> remaining = termRepository.findByUserIdOrderByCreatedAtDesc(userId);
            if (!remaining.isEmpty()) {
                AttendanceTerm next = remaining.get(0);
                next.setActive(true);
                termRepository.save(next);
            }
        }
    }

    public AttendanceTerm addSubject(String userId, String termId, SubjectRequest request) {
        AttendanceTerm term = requireOwnedTerm(userId, termId);
        validateSubject(request);

        if (term.getSubjects().size() >= MAX_SUBJECTS_PER_TERM) {
            throw new BusinessException.BadRequestException("Subject limit reached (" + MAX_SUBJECTS_PER_TERM + ")");
        }

        AttendanceSubject subject = AttendanceSubject.builder()
                .id(UUID.randomUUID().toString())
                .name(requireName(request.getName()))
                .attended(request.getAttended())
                .total(request.getTotal())
                .targetPercentage(request.getTargetPercentage())
                .build();

        term.getSubjects().add(subject);
        term.setUpdatedAt(Instant.now());
        return termRepository.save(term);
    }

    public AttendanceTerm updateSubject(String userId, String termId, String subjectId, SubjectRequest request) {
        AttendanceTerm term = requireOwnedTerm(userId, termId);
        validateSubject(request);

        AttendanceSubject subject = term.getSubjects().stream()
                .filter(s -> s.getId().equals(subjectId))
                .findFirst()
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Subject", subjectId));

        subject.setName(requireName(request.getName()));
        subject.setAttended(request.getAttended());
        subject.setTotal(request.getTotal());
        subject.setTargetPercentage(request.getTargetPercentage());

        term.setUpdatedAt(Instant.now());
        return termRepository.save(term);
    }

    public AttendanceTerm deleteSubject(String userId, String termId, String subjectId) {
        AttendanceTerm term = requireOwnedTerm(userId, termId);
        boolean removed = term.getSubjects().removeIf(s -> s.getId().equals(subjectId));
        if (!removed) {
            throw new BusinessException.ResourceNotFoundException("Subject", subjectId);
        }
        term.setUpdatedAt(Instant.now());
        return termRepository.save(term);
    }

    private void deactivateAllExcept(String userId, String keepTermId) {
        List<AttendanceTerm> terms = termRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (AttendanceTerm term : terms) {
            boolean shouldBeActive = term.getId().equals(keepTermId);
            if (term.isActive() != shouldBeActive) {
                term.setActive(shouldBeActive);
                termRepository.save(term);
            }
        }
    }

    private AttendanceTerm requireOwnedTerm(String userId, String termId) {
        return termRepository.findByIdAndUserId(termId, userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Term", termId));
    }

    private String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException.BadRequestException("Name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException.BadRequestException("Name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private void validateSubject(SubjectRequest request) {
        if (request.getAttended() < 0 || request.getTotal() < 0) {
            throw new BusinessException.BadRequestException("Attended/total classes cannot be negative");
        }
        if (request.getAttended() > request.getTotal()) {
            throw new BusinessException.BadRequestException("Attended classes cannot exceed total classes");
        }
        if (request.getTargetPercentage() < 0 || request.getTargetPercentage() > 100) {
            throw new BusinessException.BadRequestException("Target percentage must be between 0 and 100");
        }
    }
}
