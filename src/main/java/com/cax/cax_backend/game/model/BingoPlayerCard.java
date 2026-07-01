package com.cax.cax_backend.game.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bingo_player_cards")
@CompoundIndexes({
    @CompoundIndex(name = "game_player_idx", def = "{'gameCode': 1, 'caxId': 1}", unique = true)
})
public class BingoPlayerCard {

    @Id
    private String id;

    private String gameCode;
    private String caxId;

    private String playerName;
    private String collegeName;

    // 25 prompts in random order for this player's grid
    private List<String> grid;

    @Builder.Default
    private List<CellMark> markedCells = new ArrayList<>();

    @Builder.Default
    private int completedLines = 0;

    @Builder.Default
    private int markedCount = 0;

    @Builder.Default
    private boolean bingo = false;

    @Builder.Default
    private Instant joinedAt = Instant.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CellMark {
        private int cellIndex;
        private String signerCaxId;
        private String signerName;
        private Instant markedAt;
    }
}
