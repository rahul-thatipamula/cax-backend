package com.cax.cax_backend.arcade.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an engine decided happened in a round: who won it, what everyone earned, and the
 * line of copy describing it.
 *
 * <p>Scores are returned as deltas rather than totals. The session service is the only thing
 * that writes to a participant's running score, and it applies these with an atomic
 * increment — so a round that somehow gets scored twice, or two rounds resolving concurrently,
 * cannot clobber each other's totals the way a read-modify-write of an absolute score would.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundOutcome {

    /** caxIds that won the round. Empty if nobody did; more than one on a tie. */
    @Builder.Default
    private List<String> winnerCaxIds = new ArrayList<>();

    /** caxId to points earned this round. Insertion-ordered so the reveal list is stable. */
    @Builder.Default
    private Map<String, Integer> deltas = new LinkedHashMap<>();

    /** caxId to the short reason shown next to their delta on the reveal screen. */
    @Builder.Default
    private Map<String, String> reasons = new LinkedHashMap<>();

    /** One-line summary of the round, e.g. "Aditi was the imposter — and got away with it." */
    private String summary;

    /** Records a delta, merging with anything already awarded to the same player. */
    public void award(String caxId, int points, String reason) {
        if (caxId == null || points == 0) return;
        deltas.merge(caxId, points, Integer::sum);
        reasons.put(caxId, reason);
    }
}
