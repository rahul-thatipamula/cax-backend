package com.cax.cax_backend.club.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "club_members")
@CompoundIndexes({
    @CompoundIndex(name = "club_user_idx", def = "{'clubId': 1, 'userId': 1}", unique = true)
})
public class ClubMember {
    @Id
    private String id;

    @Indexed
    private String clubId;

    @Indexed
    private String userId;

    private String name;
    private String email;
    private String picture;

    @Builder.Default
    private String role = "Member"; // "President", "Vice President", "Lead", "Core Member", "Volunteer", "Member"

    @Builder.Default
    private List<String> accessControls = List.of();

    @Builder.Default
    private Instant joinedAt = Instant.now();

    @Builder.Default
    private boolean isMuted = false;
}
