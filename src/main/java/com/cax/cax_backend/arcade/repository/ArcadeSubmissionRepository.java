package com.cax.cax_backend.arcade.repository;

import com.cax.cax_backend.arcade.model.ArcadeSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ArcadeSubmissionRepository extends MongoRepository<ArcadeSubmission, String> {

    List<ArcadeSubmission> findByRoundId(String roundId);

    Optional<ArcadeSubmission> findByRoundIdAndCaxId(String roundId, String caxId);

    long countByRoundId(String roundId);

    void deleteBySessionId(String sessionId);
}
