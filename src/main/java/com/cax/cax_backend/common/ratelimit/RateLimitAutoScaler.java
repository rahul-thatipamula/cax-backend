package com.cax.cax_backend.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The actual "adaptive" part: every 30s, looks at each rule's traffic over the last window
 *  and decides whether real demand has outgrown its limit — no admin has to notice a spike
 *  is happening for this to kick in, and no domain-specific signal (like "is a bingo game
 *  active") is needed either, so it works for feed spikes, event-day traffic, or anything
 *  else, not just bingo.
 *
 *  Surge signal: high rejection rate AND a large number of *trusted, distinct* identities
 *  being rejected — "trusted" meaning established accounts (48h+ old) or IP-keyed callers,
 *  see AdaptiveRateLimiter.isTrustedIdentity. That combination means a crowd of different
 *  real users are hitting the ceiling together — a single abusive client retrying fast would
 *  show a high rejection rate but a tiny distinct-identity count, so it won't trigger a boost.
 *  A Sybil attack (many freshly created accounts coordinated together) shows up in the raw
 *  distinct-identity count but not the *trusted* one, so it can't trigger a boost either —
 *  see the untrustedRatio() logging below for the case where it's close but not quite gaming it.
 *  Auth-sensitive rules opt out entirely via RateLimitRule.autoScaleEligible, since
 *  self-loosening under heavy rejected login/2FA traffic is the wrong response regardless. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitAutoScaler {

    private static final double REJECTION_RATE_THRESHOLD = 0.15;
    private static final int MIN_DISTINCT_IDENTITIES = 15;
    private static final double BOOST_FACTOR = 1.5;
    private static final double DECAY_FACTOR = 0.8;
    // If a boost fires and this much of the (raw) distinct traffic was untrusted/new accounts,
    // it's still allowed (the boost was earned on trusted identities alone) but worth a WARN
    // for an admin to glance at — e.g. a real event plus some new signups piling on at once.
    private static final double SUSPICIOUS_UNTRUSTED_RATIO = 0.5;

    private final RuleLoadRegistry loadRegistry;
    private final RateLimitConfigService configService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void evaluate() {
        RateLimitConfig config = configService.getConfig();
        List<RateLimitRule> rules = new ArrayList<>();
        if (config.getRules() != null) rules.addAll(config.getRules());
        if (config.getDefaultRule() != null) rules.add(config.getDefaultRule());

        for (RateLimitRule rule : rules) {
            if (rule.getName() == null) continue;
            RuleLoadTracker tracker = loadRegistry.forRule(rule.getName());
            RuleLoadTracker.WindowSnapshot snapshot = tracker.rotate();

            if (!rule.isAutoScaleEligible()) {
                continue; // multiplier stays at its default 1.0 forever for these rules
            }

            boolean surge = snapshot.total() > 0
                    && snapshot.rejectionRate() >= REJECTION_RATE_THRESHOLD
                    && snapshot.trustedDistinctIdentities() >= MIN_DISTINCT_IDENTITIES;

            double current = tracker.getAutoMultiplier();
            double next;
            if (surge) {
                next = Math.min(rule.getMaxAutoBoostMultiplier(), Math.max(current, 1.0) * BOOST_FACTOR);
                if (next > current + 0.01) {
                    log.info("Rate limit auto-scale UP: rule={}, rejectionRate={}, trustedDistinctIdentities={}, multiplier {} -> {}",
                            rule.getName(), String.format("%.2f", snapshot.rejectionRate()),
                            snapshot.trustedDistinctIdentities(), round(current), round(next));
                    if (snapshot.untrustedRatio() >= SUSPICIOUS_UNTRUSTED_RATIO) {
                        log.warn("Rate limit auto-scale boost for rule={} coincides with a high share of "
                                        + "newly-created accounts in the traffic ({}% of {} distinct callers) — "
                                        + "worth a manual look even though the boost was earned on trusted identities alone.",
                                rule.getName(), Math.round(snapshot.untrustedRatio() * 100), snapshot.distinctIdentities());
                    }
                }
            } else {
                next = Math.max(1.0, current * DECAY_FACTOR);
                if (current > 1.01 && next <= 1.01) {
                    log.info("Rate limit auto-scale reverted to normal: rule={}", rule.getName());
                }
            }
            tracker.setAutoMultiplier(next);
        }
    }

    /** Current auto-detected multiplier per rule, for the admin dashboard to display —
     *  1.0 means "no surge detected, nothing boosted." */
    public Map<String, Double> currentMultipliers() {
        Map<String, Double> result = new LinkedHashMap<>();
        loadRegistry.all().forEach((name, tracker) -> result.put(name, round(tracker.getAutoMultiplier())));
        return result;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
