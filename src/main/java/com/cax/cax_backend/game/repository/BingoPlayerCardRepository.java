package com.cax.cax_backend.game.repository;

import com.cax.cax_backend.game.model.BingoPlayerCard;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BingoPlayerCardRepository extends MongoRepository<BingoPlayerCard, String> {
    Optional<BingoPlayerCard> findByGameCodeAndCaxId(String gameCode, String caxId);
    List<BingoPlayerCard> findByGameCodeOrderByMarkedCountDescCompletedLinesDesc(String gameCode);
    boolean existsByGameCodeAndCaxId(String gameCode, String caxId);
    List<BingoPlayerCard> findByCaxId(String caxId);
    long countByGameCode(String gameCode);
    long countByGameCodeAndMarkedCells_SignerCaxId(String gameCode, String signerCaxId);
    List<BingoPlayerCard> findByGameCode(String gameCode);
}
