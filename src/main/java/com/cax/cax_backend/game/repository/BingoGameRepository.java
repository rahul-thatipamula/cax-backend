package com.cax.cax_backend.game.repository;

import com.cax.cax_backend.game.model.BingoGame;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BingoGameRepository extends MongoRepository<BingoGame, String> {
    Optional<BingoGame> findByGameCode(String gameCode);
    List<BingoGame> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);
    List<BingoGame> findByGameCodeIn(Collection<String> gameCodes);
}
