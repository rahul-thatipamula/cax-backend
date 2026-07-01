package com.cax.cax_backend.game.dto;

import com.cax.cax_backend.game.model.BingoPlayerCard;
import lombok.Getter;

import java.time.Instant;

/** Leaderboard row: player identity + stats + the live, dynamically computed score.
 *  Score is never persisted — it's recomputed on every fetch from current game-wide
 *  signer usage, so one player's new mark can change everyone else's score. */
@Getter
public class BingoLeaderboardEntry {

    private final String id;
    private final String gameCode;
    private final String caxId;
    private final String playerName;
    private final String collegeName;
    private final int markedCount;
    private final int completedLines;
    private final boolean bingo;
    private final Instant joinedAt;
    private final double score;

    public BingoLeaderboardEntry(BingoPlayerCard card, double score) {
        this.id = card.getId();
        this.gameCode = card.getGameCode();
        this.caxId = card.getCaxId();
        this.playerName = card.getPlayerName();
        this.collegeName = card.getCollegeName();
        this.markedCount = card.getMarkedCount();
        this.completedLines = card.getCompletedLines();
        this.bingo = card.isBingo();
        this.joinedAt = card.getJoinedAt();
        this.score = score;
    }
}
