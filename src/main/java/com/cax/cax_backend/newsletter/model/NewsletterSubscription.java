package com.cax.cax_backend.newsletter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "newsletterSubscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NewsletterSubscription {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
