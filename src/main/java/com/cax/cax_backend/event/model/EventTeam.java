package com.cax.cax_backend.event.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A team registered for an event. Members are EventParticipant records carrying
 * this team's id — the team itself only holds grouping state, so all per-person
 * machinery (payment proof, tickets, check-in) keeps working unchanged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "event_teams")
@CompoundIndexes({
    @CompoundIndex(name = "event_team_code_idx", def = "{'eventId': 1, 'teamCode': 1}", unique = true)
})
public class EventTeam {
    @Id
    private String id;

    // Optimistic lock: two concurrent joins racing to fill the last slot must not
    // both succeed past maxTeamSize.
    @Version
    private Long version;

    @Indexed
    private String eventId;

    private String teamName;

    private String leaderUserId;

    /** Short invite code members use to join (e.g. "TX7-4KQ9"). Unique per event. */
    private String teamCode;

    /** FORMING until member count reaches the event's minTeamSize, then COMPLETE. */
    @Builder.Default
    private String status = "FORMING";

    /**
     * Team-level payment state, used only when the event's teamFeeType is PER_TEAM.
     * PENDING until the leader's payment is verified, then VERIFIED.
     */
    @Builder.Default
    private String paymentStatus = "PENDING";

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;
}
