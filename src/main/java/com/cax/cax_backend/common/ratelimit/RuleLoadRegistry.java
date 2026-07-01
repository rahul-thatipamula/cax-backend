package com.cax.cax_backend.common.ratelimit;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Holds one RuleLoadTracker per rule name. Written on every request (AdaptiveRateLimiter)
 *  and read/rotated periodically (RateLimitAutoScaler) — the seam between the hot request
 *  path and the background evaluation loop. */
@Component
class RuleLoadRegistry {

    private final Map<String, RuleLoadTracker> trackers = new ConcurrentHashMap<>();

    RuleLoadTracker forRule(String ruleName) {
        return trackers.computeIfAbsent(ruleName, k -> new RuleLoadTracker());
    }

    Map<String, RuleLoadTracker> all() {
        return trackers;
    }
}
