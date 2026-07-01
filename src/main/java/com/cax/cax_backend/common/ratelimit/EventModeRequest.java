package com.cax.cax_backend.common.ratelimit;

import lombok.Data;

import java.time.Instant;

@Data
public class EventModeRequest {
    private boolean enabled;
    /** Multiplier applied to capacity + refill for every rule (unless a rule has its own
     *  eventMultiplierOverride). Ignored if null — existing value is kept. */
    private Double multiplier;
    /** Optional auto-revert time. Null = stays on until an admin explicitly disables it. */
    private Instant expiresAt;
}
