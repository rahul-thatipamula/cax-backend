package com.cax.cax_backend.arcade.engine;

import com.cax.cax_backend.arcade.dto.ArcadeRequests;
import com.cax.cax_backend.arcade.dto.ArcadeStateResponse;
import com.cax.cax_backend.arcade.model.*;

import java.util.List;

/**
 * The per-game strategy. Everything that differs between the three Arcade games lives behind
 * this interface; everything they share — codes, joining, presence, rejoin, the round gate,
 * timers, leaderboards, history — lives once in the session service.
 *
 * <p>Adding a fourth game means writing one more implementation and a prompt bank, with no
 * change to the session machinery.
 */
public interface ArcadeGameEngine {

    ArcadeGameType type();

    /**
     * Whether this game has a private writing step before voting. Most Likely To does not —
     * it goes straight from prompt to vote — so the session service skips SUBMITTING for it.
     */
    boolean hasSubmissionPhase();

    /**
     * Fills in a freshly created round with its content: a prompt, or a secret word plus a
     * randomly chosen imposter. Called once, server-side, when the round opens.
     */
    void prepareRound(ArcadeSession session, ArcadeRound round, List<ArcadeParticipant> players);

    /**
     * Validates and normalises a player's submission, returning the text to store.
     * Throws a {@code BusinessException} if the input is unusable.
     *
     * <p>Implementations must treat the input as hostile: the client enforces its own limits
     * for a good typing experience, but those limits are advisory and this is where they
     * are actually applied.
     */
    String normaliseSubmission(String raw);

    /**
     * Rejects votes that are illegal for this game — voting for yourself as the imposter,
     * attributing a submission that is not in this round, and so on.
     */
    void validateVote(ArcadeRound round,
                      List<ArcadeParticipant> players,
                      List<ArcadeSubmission> submissions,
                      String voterCaxId,
                      ArcadeRequests.Vote request);

    /** Decides the round: winners, per-player deltas and the summary line. */
    RoundOutcome scoreRound(ArcadeRound round,
                            List<ArcadeParticipant> players,
                            List<ArcadeSubmission> submissions,
                            List<ArcadeVote> votes);

    /**
     * Builds the round payload <b>as this specific viewer is allowed to see it</b>.
     *
     * <p>This is the method that keeps the games playable. An implementation that returns the
     * secret word to the imposter, or an author name before the reveal, breaks its game
     * completely — and no client-side check could compensate, because the data would already
     * be on the device.
     */
    ArcadeStateResponse.RoundView buildRoundView(ArcadeSession session,
                                                 ArcadeRound round,
                                                 List<ArcadeParticipant> players,
                                                 List<ArcadeSubmission> submissions,
                                                 List<ArcadeVote> votes,
                                                 String viewerCaxId);
}
