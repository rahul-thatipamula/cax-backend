package com.cax.cax_backend.game.dto;

import com.cax.cax_backend.game.model.BingoGame;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
public class BingoGameResponse {

    private final String id;
    private final String gameCode;
    private final String title;
    private final List<String> prompts;
    private final String status;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant endedAt;
    private final long playerCount;
    private final Integer maxSignerUsesPerGame;

    public BingoGameResponse(BingoGame game, long playerCount) {
        this.id = game.getId();
        this.gameCode = game.getGameCode();
        this.title = game.getTitle();
        this.prompts = game.getPrompts();
        this.status = game.getStatus().name();
        this.createdBy = game.getCreatedBy();
        this.createdAt = game.getCreatedAt();
        this.startedAt = game.getStartedAt();
        this.endedAt = game.getEndedAt();
        this.playerCount = playerCount;
        this.maxSignerUsesPerGame = game.getMaxSignerUsesPerGame();
    }

    public static BingoGameResponse of(BingoGame game, long playerCount) {
        return new BingoGameResponse(game, playerCount);
    }
}
