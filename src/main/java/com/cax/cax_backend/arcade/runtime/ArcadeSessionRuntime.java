package com.cax.cax_backend.arcade.runtime;

import com.cax.cax_backend.arcade.model.ArcadeSubmission;
import com.cax.cax_backend.arcade.model.ArcadeVote;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The live, in-memory state for one arcade session while it is being played.
 *
 * <p>During active play (SUBMITTING / VOTING) submissions, votes and presence live here instead
 * of hitting Mongo on every action — the poll-driven presence heartbeat and per-answer writes
 * were the hottest write path in the feature. Round data is flushed to Mongo once, at reveal,
 * where it becomes durable; a process restart before that point loses only the in-flight round
 * (an accepted trade-off), never a completed one.
 *
 * <p>Only one round is ever live at a time, so {@link #beginRound} clears the previous round's
 * submissions/votes — presence persists across rounds since it is a property of the player, not
 * the round.
 */
public class ArcadeSessionRuntime {

    /** caxId → last time this player was seen (via a state poll). */
    private final Map<String, Instant> presence = new ConcurrentHashMap<>();

    /** roundId → caxId → submission. Keyed by author so a double-submit is a no-op. */
    private final Map<String, Map<String, ArcadeSubmission>> submissions = new ConcurrentHashMap<>();

    /** roundId → voterCaxId → vote. Keyed by voter so a double-vote is a no-op. */
    private final Map<String, Map<String, ArcadeVote>> votes = new ConcurrentHashMap<>();

    /**
     * Bumped on every in-memory submit/vote so pollers still get "8 of 10 answered" progress
     * without a Mongo write per action. Combined with the durable session stateVersion in
     * {@link com.cax.cax_backend.arcade.service.ArcadeStateService} to form the version clients
     * poll against.
     */
    private final AtomicLong actionCounter = new AtomicLong();

    public void touch(String caxId) {
        presence.put(caxId, Instant.now());
    }

    /** In-memory last-seen for a player, or null if they have not polled since this runtime began. */
    public Instant lastSeen(String caxId) {
        return presence.get(caxId);
    }

    /** Starts tracking a round; clears any prior round's answers since only one is live at a time. */
    public void beginRound(String roundId) {
        submissions.keySet().removeIf(id -> !id.equals(roundId));
        votes.keySet().removeIf(id -> !id.equals(roundId));
        submissions.computeIfAbsent(roundId, k -> new ConcurrentHashMap<>());
        votes.computeIfAbsent(roundId, k -> new ConcurrentHashMap<>());
    }

    public boolean tracksRound(String roundId) {
        return submissions.containsKey(roundId) || votes.containsKey(roundId);
    }

    /** @return true if stored, false if this player already submitted this round. */
    public boolean addSubmission(String roundId, ArcadeSubmission submission) {
        Map<String, ArcadeSubmission> forRound =
                submissions.computeIfAbsent(roundId, k -> new ConcurrentHashMap<>());
        return forRound.putIfAbsent(submission.getCaxId(), submission) == null;
    }

    public List<ArcadeSubmission> submissions(String roundId) {
        Map<String, ArcadeSubmission> forRound = submissions.get(roundId);
        return forRound == null ? List.of() : new ArrayList<>(forRound.values());
    }

    /** @return true if stored, false if this player already voted this round. */
    public boolean addVote(String roundId, ArcadeVote vote) {
        Map<String, ArcadeVote> forRound =
                votes.computeIfAbsent(roundId, k -> new ConcurrentHashMap<>());
        return forRound.putIfAbsent(vote.getVoterCaxId(), vote) == null;
    }

    public List<ArcadeVote> votes(String roundId) {
        Map<String, ArcadeVote> forRound = votes.get(roundId);
        return forRound == null ? List.of() : new ArrayList<>(forRound.values());
    }

    public long bumpAction() {
        return actionCounter.incrementAndGet();
    }

    public long actionCounter() {
        return actionCounter.get();
    }
}
