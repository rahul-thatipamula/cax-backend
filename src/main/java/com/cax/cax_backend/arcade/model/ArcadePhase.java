package com.cax.cax_backend.arcade.model;

/**
 * The phase a session is currently in. Every player action is gated on the phase
 * server-side — a client that skips a screen, replays an old request or calls the
 * API directly still cannot submit during voting or vote during submission.
 */
public enum ArcadePhase {

    /** Before the first round. Players may join and leave freely. */
    LOBBY,

    /**
     * Players are writing/choosing their private input for the round.
     * MOST_LIKELY_TO skips straight to VOTING — it has nothing to submit.
     */
    SUBMITTING,

    /** All submissions are in (or the timer expired); players are voting. */
    VOTING,

    /** The round outcome is revealed. Scores for this round are already applied. */
    REVEAL,

    /**
     * The synchronisation gate between rounds: everyone marks themselves ready and
     * the session waits so stragglers and rejoiners can catch up before the next round.
     */
    INTERMISSION,

    /** All rounds played (or an end condition tripped). Final standings are frozen. */
    FINISHED;

    /** True if players may still send a submission for the current round. */
    public boolean acceptsSubmissions() {
        return this == SUBMITTING;
    }

    /** True if players may still cast a vote for the current round. */
    public boolean acceptsVotes() {
        return this == VOTING;
    }

    /** True once the session is over and nothing may mutate it any more. */
    public boolean isTerminal() {
        return this == FINISHED;
    }
}
