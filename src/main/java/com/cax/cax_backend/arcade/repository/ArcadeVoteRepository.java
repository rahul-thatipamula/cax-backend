package com.cax.cax_backend.arcade.repository;

import com.cax.cax_backend.arcade.model.ArcadeVote;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ArcadeVoteRepository extends MongoRepository<ArcadeVote, String> {

    List<ArcadeVote> findByRoundId(String roundId);

    Optional<ArcadeVote> findByRoundIdAndVoterCaxId(String roundId, String voterCaxId);

    long countByRoundId(String roundId);

    void deleteBySessionId(String sessionId);
}
