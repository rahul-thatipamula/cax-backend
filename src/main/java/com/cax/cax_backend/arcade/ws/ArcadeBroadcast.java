package com.cax.cax_backend.arcade.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * A message pushed to {@code /topic/arcade/{gameCode}}. Deliberately tiny: it is a nudge, not
 * the state itself. On receiving one the client fetches {@code /state}, which stays the single
 * per-viewer code path that decides what each player is allowed to see — so the socket never
 * has to reason about redaction.
 */
@Data
@Builder
@AllArgsConstructor
public class ArcadeBroadcast {

    public enum Type { STATE, JOINED }

    private Type type;
    private String gameCode;

    /** JOINED only: the display name of the player who just joined, for the lobby activity line. */
    private String joinedName;

    public static ArcadeBroadcast state(String gameCode) {
        return ArcadeBroadcast.builder().type(Type.STATE).gameCode(gameCode).build();
    }

    public static ArcadeBroadcast joined(String gameCode, String joinedName) {
        return ArcadeBroadcast.builder().type(Type.JOINED).gameCode(gameCode).joinedName(joinedName).build();
    }
}
