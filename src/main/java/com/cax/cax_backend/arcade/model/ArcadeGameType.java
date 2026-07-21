package com.cax.cax_backend.arcade.model;

/**
 * The games available in the Arcade. Each type is backed by its own
 * {@code ArcadeGameEngine} implementation, but they all share the same session,
 * participant, round, presence and scoring machinery.
 */
public enum ArcadeGameType {

    /** A prompt is shown to everyone; each player votes for a person. Highest vote wins the round. */
    MOST_LIKELY_TO(2, 20),

    /** Everyone secretly answers a prompt; answers show anonymously; players guess the author. */
    WHO_SAID_IT(2, 25),

    /** Everyone gets a secret word except one imposter; each gives a one-word clue; the group votes. */
    IMPOSTER(2, 25);

    /** Minimum players required before the host may start this game. */
    private final int minPlayers;

    /** Maximum players allowed in one session — bounds the fan-out of every state payload. */
    private final int maxPlayers;

    ArcadeGameType(int minPlayers, int maxPlayers) {
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }
}
