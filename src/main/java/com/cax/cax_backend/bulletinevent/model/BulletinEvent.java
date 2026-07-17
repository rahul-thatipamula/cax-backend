package com.cax.cax_backend.bulletinevent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bulletin_events")
public class BulletinEvent {
    @Id
    private String id;

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 150, message = "Title must be between 3 and 150 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 3000, message = "Description must be between 10 and 3000 characters")
    private String description;

    private String coverImage; // Cloudflare R2 Storage URL

    @NotBlank(message = "External link is required")
    private String externalLink; // External registration/info URL

    private Instant eventStartDate;
    private Instant eventEndDate;
    private Instant registrationEndDate; // Registration/application deadline

    private String conductedBy; // Organiser / host name

    @Builder.Default
    private boolean global = false;

    private List<String> collegeIds; // Targeted colleges if global is false

    @Builder.Default
    private boolean active = true;   // When false, hidden from students

    @Builder.Default
    private boolean deleted = false; // Soft delete flag

    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;       // Timestamp of soft delete
}
