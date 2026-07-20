package com.cax.cax_backend.bulletineventsubmission.repository;

import com.cax.cax_backend.bulletineventsubmission.model.BulletinSubmissionAuditLog;
import com.cax.cax_backend.bulletineventsubmission.model.BulletinSubmissionAuditLog.Outcome;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface BulletinSubmissionAuditLogRepository extends MongoRepository<BulletinSubmissionAuditLog, String> {

    List<BulletinSubmissionAuditLog> findAllByOrderByCreatedAtDesc();

    List<BulletinSubmissionAuditLog> findByOutcomeOrderByCreatedAtDesc(Outcome outcome);

    List<BulletinSubmissionAuditLog> findByClientIpOrderByCreatedAtDesc(String clientIp);

    List<BulletinSubmissionAuditLog> findByOrganizerEmailOrderByCreatedAtDesc(String organizerEmail);

    /** Backs abuse checks like "how many attempts from this IP in the last hour". */
    long countByClientIpAndCreatedAtAfter(String clientIp, Instant since);
}
