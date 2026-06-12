package com.cax.cax_backend.chat.model;

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
@Document(collection = "club_messages")
public class ClubMessage {
    @Id
    private String id;

    @Indexed
    private String clubId;

    @Indexed
    private String senderId;

    private String senderName;
    private String senderPicture;

    private String content; // Stored AES-128 encrypted

    private String replyToId;
    private String replyToName;
    private String replyToContent;

    @Indexed
    private Instant createdAt;
}
