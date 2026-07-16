package com.cax.cax_backend.manualverification.repository;

import com.cax.cax_backend.manualverification.model.ManualVerification;
import com.cax.cax_backend.manualverification.model.ManualVerification.VerificationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ManualVerificationRepository extends MongoRepository<ManualVerification, String> {

    List<ManualVerification> findAllByOrderBySubmittedAtDesc();

    List<ManualVerification> findByStatusOrderBySubmittedAtDesc(VerificationStatus status);

    Optional<ManualVerification> findFirstByUserIdOrderBySubmittedAtDesc(String userId);

    List<ManualVerification> findByUserIdOrderBySubmittedAtDesc(String userId);

    List<ManualVerification> findByIdCardHash(String idCardHash);

    List<ManualVerification> findByStudentIdNumberHash(String studentIdNumberHash);

    long countByStatus(VerificationStatus status);

    // Approved records past their validity — due for yearly re-verification.
    List<ManualVerification> findByStatusAndValidUntilBefore(VerificationStatus status, Instant instant);

    // Approved records expiring soon — for reminder pushes.
    List<ManualVerification> findByStatusAndValidUntilBetween(VerificationStatus status, Instant from, Instant to);
}
