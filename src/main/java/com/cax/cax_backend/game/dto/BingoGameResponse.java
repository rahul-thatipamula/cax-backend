package com.cax.cax_backend.game.dto;

import com.cax.cax_backend.game.model.BingoGame;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BingoGameResponse {

    private final String id;
    private final String gameCode;
    private final String title;
    private final List<String> prompts;
    private final int promptCount;
    private final String status;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant endedAt;
    private final long playerCount;
    private final Integer maxSignerUsesPerGame;

    private BingoGameResponse(BingoGame game, long playerCount, boolean includeInternals) {
        this.id = game.getId();
        this.gameCode = game.getGameCode();
        this.title = game.getTitle();
        this.prompts = includeInternals ? game.getPrompts() : null;
        this.promptCount = game.getPrompts() != null ? game.getPrompts().size() : 0;
        this.status = game.getStatus().name();
        this.createdBy = includeInternals ? game.getCreatedBy() : null;
        this.createdAt = game.getCreatedAt();
        this.startedAt = game.getStartedAt();
        this.endedAt = game.getEndedAt();
        this.playerCount = playerCount;
        this.maxSignerUsesPerGame = game.getMaxSignerUsesPerGame();
    }

    /** Full view for org leaders (includes the prompt pool and creator id). */
    public static BingoGameResponse of(BingoGame game, long playerCount) {
        return new BingoGameResponse(game, playerCount, true);
    }

    /** Player/public view: omits the full prompt pool (players only ever see their own
     *  25-cell grid) and the creator's internal userId. */
    public static BingoGameResponse publicOf(BingoGame game, long playerCount) {
        return new BingoGameResponse(game, playerCount, false);
    }
}
