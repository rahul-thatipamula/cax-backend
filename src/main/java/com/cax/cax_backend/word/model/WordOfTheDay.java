package com.cax.cax_backend.word.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "wordsOfTheDay")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WordOfTheDay {
    @Id private String id;
    private String word;
    @Builder.Default private boolean active = false;
    @Builder.Default private Instant createdAt = Instant.now();
    private Instant updatedAt;
}
