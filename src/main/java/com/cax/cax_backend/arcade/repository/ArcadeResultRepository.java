package com.cax.cax_backend.arcade.repository;

import com.cax.cax_backend.arcade.model.ArcadeResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ArcadeResultRepository extends MongoRepository<ArcadeResult, String> {

    Optional<ArcadeResult> findBySessionId(String sessionId);

    /** "Games I have played", newest first — one indexed query against participantCaxIds. */
    List<ArcadeResult> findByParticipantCaxIdsContainsOrderByEndedAtDesc(String caxId, Pageable pageable);

    long countByParticipantCaxIdsContains(String caxId);

    long countByWinnerCaxIdsContains(String caxId);

    List<ArcadeResult> findByParticipantCaxIdsContains(String caxId);
}
