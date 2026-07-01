package com.cax.cax_backend.common.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One rate-limit policy for a group of endpoints — matched by Ant-style path pattern
 *  + HTTP method ("*" = any method). Rules are stored in Mongo so they're editable at
 *  runtime without a redeploy (see RateLimitConfigService). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRule {

    /** Human-readable identifier, also used as part of the bucket cache key. */
    private String name;

    /** Ant-style pattern, e.g. "/api/thoughts/*\/like". Matched against the request's servlet path. */
    private String pathPattern;

    /** HTTP method this rule applies to, or "*" for any method. */
    @Builder.Default
    private String method = "*";

    @Builder.Default
    private RateLimitKeyStrategy keyStrategy = RateLimitKeyStrategy.IP;

    /** Max tokens the bucket can hold (i.e. burst size) under normal (non-event) conditions. */
    private long capacity;

    /** Steady-state refill rate, in tokens per second, under normal conditions. */
    private double refillPerSecond;

    /** Optional per-rule override for the event-mode multiplier — lets an admin boost just
     *  this rule (e.g. bingo polling) during a hackathon without touching every other rule.
     *  Null means "use the global eventModeMultiplier" from RateLimitConfig. */
    private Double eventMultiplierOverride;

    /** Whether RateLimitAutoScaler is allowed to auto-boost this rule when it detects a
     *  real demand surge (many distinct identities getting rejected at once). Should be
     *  false for auth-sensitive rules like login/2FA, where self-loosening under heavy
     *  rejected traffic is exactly the wrong response — that traffic could be an attack,
     *  not legitimate demand, and there's no safe way to tell the two apart for auth. */
    @Builder.Default
    private boolean autoScaleEligible = true;

    /** Ceiling on how far RateLimitAutoScaler can multiply this rule's capacity/refill,
     *  independent of (and combined with, via max()) the manual event-mode multiplier. */
    @Builder.Default
    private double maxAutoBoostMultiplier = 5.0;
}
