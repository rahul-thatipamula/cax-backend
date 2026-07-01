package com.cax.cax_backend.common.ratelimit;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** Singleton Mongo document (same pattern as settings.model.SystemSetting) holding every
 *  rate-limit rule plus the global "event mode" toggle admins flip on for expected traffic
 *  spikes (orientation, hackathon, etc.) so limits can be raised without a redeploy. */
@Document(collection = "rate_limit_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RateLimitConfig {

    @Id
    @Builder.Default
    private String id = "global";

    /** Ordered most-specific-first; the first rule whose path+method matches wins. */
    private List<RateLimitRule> rules;

    /** Applied when no rule matches. */
    private RateLimitRule defaultRule;

    @Builder.Default
    private boolean eventModeEnabled = false;

    /** Multiplies both capacity and refill rate for every rule (unless the rule sets its own
     *  eventMultiplierOverride) while event mode is enabled and not expired. */
    @Builder.Default
    private double eventModeMultiplier = 1.0;

    /** Safety net: if set, event mode is treated as disabled once this passes, even if
     *  eventModeEnabled is still true — so nobody has to remember to turn it back off. */
    private Instant eventModeExpiresAt;

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
