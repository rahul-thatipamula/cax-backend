package com.cax.cax_backend.game.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bingo_game_prompts")
public class BingoGamePrompt {

    @Id
    private String id;

    private String text;

    /** easy | medium | hard */
    private String difficulty;

    private String category;

    private boolean isActive;

    private Instant createdAt;
    private Instant updatedAt;
}
