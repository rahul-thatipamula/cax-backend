package com.cax.cax_backend.arcade.repository;

import com.cax.cax_backend.arcade.model.ArcadeParticipant;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ArcadeParticipantRepository extends MongoRepository<ArcadeParticipant, String> {

    Optional<ArcadeParticipant> findBySessionIdAndCaxId(String sessionId, String caxId);

    List<ArcadeParticipant> findBySessionId(String sessionId);

    List<ArcadeParticipant> findBySessionIdOrderByScoreDesc(String sessionId);

    long countBySessionId(String sessionId);

    /** Everything this player has ever joined — the backing query for their game history. */
    List<ArcadeParticipant> findByCaxIdOrderByJoinedAtDesc(String caxId);

    boolean existsBySessionIdAndCaxId(String sessionId, String caxId);

    void deleteBySessionId(String sessionId);
}
