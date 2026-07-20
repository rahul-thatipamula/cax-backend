package com.cax.cax_backend.arcade.engine;

import com.cax.cax_backend.arcade.dto.ArcadeStateResponse;
import com.cax.cax_backend.arcade.model.ArcadeParticipant;
import com.cax.cax_backend.arcade.model.ArcadeVote;

import java.util.*;

/**
 * Vote-counting and name-resolution helpers shared by all three engines.
 *
 * <p>Names come from the participant rows the caller already loaded rather than from a user
 * lookup, so building a state payload for a room of thirty stays free of per-player queries.
 */
public abstract class AbstractArcadeEngine implements ArcadeGameEngine {

    /** caxId to display name, for the players in this session. */
    protected Map<String, String> namesOf(List<ArcadeParticipant> players) {
        Map<String, String> names = new HashMap<>();
        for (ArcadeParticipant p : players) {
            names.put(p.getCaxId(), p.getDisplayName());
        }
        return names;
    }

    protected String nameOf(Map<String, String> names, String caxId) {
        String name = names.get(caxId);
        return name != null ? name : "Someone";
    }

    /** How many votes each target received. */
    protected Map<String, Integer> countVotes(List<ArcadeVote> votes) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ArcadeVote v : votes) {
            if (v.getTargetCaxId() != null) {
                counts.merge(v.getTargetCaxId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * The target(s) with the most votes. Returns every tied leader rather than picking one,
     * because a tie is a real and common outcome in a small room and silently breaking it
     * would make the reveal look arbitrary.
     */
    protected List<String> topVoted(Map<String, Integer> counts) {
        int best = 0;
        for (int c : counts.values()) best = Math.max(best, c);
        if (best == 0) return List.of();

        List<String> leaders = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() == best) leaders.add(e.getKey());
        }
        return leaders;
    }

    /**
     * Builds the tally list for the reveal screen, including who voted for whom. Callers must
     * only use this once the round has revealed — releasing live tallies would let late voters
     * see the running count and swing the result.
     */
    protected List<ArcadeStateResponse.VoteTally> buildTallies(List<ArcadeVote> votes,
                                                               Map<String, String> names) {
        Map<String, List<String>> votersByTarget = new LinkedHashMap<>();
        for (ArcadeVote v : votes) {
            if (v.getTargetCaxId() == null) continue;
            votersByTarget.computeIfAbsent(v.getTargetCaxId(), k -> new ArrayList<>())
                    .add(v.getVoterCaxId());
        }

        List<ArcadeStateResponse.VoteTally> tallies = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : votersByTarget.entrySet()) {
            tallies.add(ArcadeStateResponse.VoteTally.builder()
                    .caxId(e.getKey())
                    .displayName(nameOf(names, e.getKey()))
                    .votes(e.getValue().size())
                    .voterCaxIds(e.getValue())
                    .build());
        }
        tallies.sort(Comparator.comparingInt(ArcadeStateResponse.VoteTally::getVotes).reversed());
        return tallies;
    }

    /** Formats a winner list into readable copy: "A", "A and B", "A, B and C". */
    protected String joinNames(List<String> caxIds, Map<String, String> names) {
        List<String> display = caxIds.stream().map(id -> nameOf(names, id)).toList();
        if (display.isEmpty()) return "Nobody";
        if (display.size() == 1) return display.get(0);
        if (display.size() == 2) return display.get(0) + " and " + display.get(1);
        return String.join(", ", display.subList(0, display.size() - 1))
                + " and " + display.get(display.size() - 1);
    }
}
