package com.cax.cax_backend.arcade.repository;

import com.cax.cax_backend.arcade.model.ArcadePhase;
import com.cax.cax_backend.arcade.model.ArcadeSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArcadeSessionRepository extends MongoRepository<ArcadeSession, String> {

    Optional<ArcadeSession> findByGameCode(String gameCode);

    boolean existsByGameCode(String gameCode);

    /** Used to cap how many games one person can have open at once. */
    long countByHostUserIdAndPhaseNot(String hostUserId, ArcadePhase phase);

    List<ArcadeSession> findByHostUserIdOrderByCreatedAtDesc(String hostUserId);

    List<ArcadeSession> findByIdIn(Iterable<String> ids);

    /** Idle sessions eligible for reaping, so abandoned lobbies do not hold their codes forever. */
    List<ArcadeSession> findByPhaseNotAndLastActivityAtBefore(ArcadePhase phase, Instant cutoff);
}
