package com.cax.cax_backend.bulletineventsubmission.repository;

import com.cax.cax_backend.bulletineventsubmission.model.BulletinEventSubmission;
import com.cax.cax_backend.bulletineventsubmission.model.BulletinEventSubmission.SubmissionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BulletinEventSubmissionRepository extends MongoRepository<BulletinEventSubmission, String> {

    List<BulletinEventSubmission> findAllByOrderBySubmittedAtDesc();

    List<BulletinEventSubmission> findByStatusOrderBySubmittedAtDesc(SubmissionStatus status);

    long countByStatus(SubmissionStatus status);
}
