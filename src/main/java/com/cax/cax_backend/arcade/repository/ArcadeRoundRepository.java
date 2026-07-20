package com.cax.cax_backend.arcade.repository;

import com.cax.cax_backend.arcade.model.ArcadeRound;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ArcadeRoundRepository extends MongoRepository<ArcadeRound, String> {

    Optional<ArcadeRound> findBySessionIdAndRoundNo(String sessionId, int roundNo);

    List<ArcadeRound> findBySessionIdOrderByRoundNoAsc(String sessionId);

    void deleteBySessionId(String sessionId);
}
