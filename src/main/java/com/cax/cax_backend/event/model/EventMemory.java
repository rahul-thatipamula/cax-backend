package com.cax.cax_backend.event.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "event_memories")
public class EventMemory {
    @Id
    private String id;
    
    @Indexed
    private String eventId;
    
    private String imageUrl;
    
    @Builder.Default
    private Instant uploadedAt = Instant.now();
    
    @Builder.Default
    private boolean hidden = false;

    @Builder.Default
    private boolean deleted = false;

    private Instant deletedAt;
}
