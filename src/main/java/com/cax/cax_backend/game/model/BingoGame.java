package com.cax.cax_backend.game.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bingo_games")
public class BingoGame {

    @Id
    private String id;

    @Indexed(unique = true)
    private String gameCode;

    private String title;

    private List<String> prompts;

    /** Max number of different cards one person (caxId) may sign in this game. Null = unlimited. */
    private Integer maxSignerUsesPerGame;

    @Builder.Default
    private GameStatus status = GameStatus.LOBBY;

    private String createdBy;

    /** The organization this game belongs to. Only the org's President/VP can manage it. */
    private String organizationId;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant startedAt;
    private Instant endedAt;

    public enum GameStatus {
        LOBBY, ACTIVE, ENDED
    }
}
