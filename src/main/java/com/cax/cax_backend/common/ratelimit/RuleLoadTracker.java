package com.cax.cax_backend.common.ratelimit;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Rolling-window traffic counters for one rule, used by RateLimitAutoScaler to detect a
 *  real demand surge — a high rejection rate combined with a large number of *distinct*
 *  identities is a crowd of real users outgrowing the limit, not one abusive client retrying.
 *
 *  Sybil resistance: only "trusted" identities (established accounts, or IP-keyed callers —
 *  see AdaptiveRateLimiter) count toward the threshold that actually triggers a boost. A pile
 *  of freshly created accounts coordinated to fake a crowd still shows up in the raw
 *  distinctIdentities count (for visibility/logging) but can't cross the trusted threshold
 *  on its own. */
class RuleLoadTracker {

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong rejectedRequests = new AtomicLong();
    private final Set<String> distinctIdentities = ConcurrentHashMap.newKeySet();
    private final Set<String> trustedDistinctIdentities = ConcurrentHashMap.newKeySet();

    /** Current auto-detected multiplier for this rule, applied on top of (via max() with)
     *  any manual event-mode multiplier. Starts at 1.0 (no boost) and only this field is
     *  read on the hot request path — everything else here is scheduler-only. */
    private volatile double autoMultiplier = 1.0;

    void record(String identity, boolean allowed, boolean trusted) {
        totalRequests.incrementAndGet();
        if (!allowed) rejectedRequests.incrementAndGet();
        distinctIdentities.add(identity);
        if (trusted) trustedDistinctIdentities.add(identity);
    }

    double getAutoMultiplier() {
        return autoMultiplier;
    }

    void setAutoMultiplier(double multiplier) {
        this.autoMultiplier = multiplier;
    }

    /** Snapshots and resets the window's counters for the next evaluation cycle. */
    WindowSnapshot rotate() {
        long total = totalRequests.getAndSet(0);
        long rejected = rejectedRequests.getAndSet(0);
        int distinct = distinctIdentities.size();
        int trustedDistinct = trustedDistinctIdentities.size();
        distinctIdentities.clear();
        trustedDistinctIdentities.clear();
        return new WindowSnapshot(total, rejected, distinct, trustedDistinct);
    }

    record WindowSnapshot(long total, long rejected, int distinctIdentities, int trustedDistinctIdentities) {
        double rejectionRate() {
            return total == 0 ? 0.0 : (double) rejected / total;
        }

        /** Fraction of distinct callers in this window that were NOT trusted (i.e. freshly
         *  created accounts) — high values alongside a triggered boost are worth an admin's
         *  attention even though the boost itself only fires off the trusted count. */
        double untrustedRatio() {
            return distinctIdentities == 0 ? 0.0 : 1.0 - ((double) trustedDistinctIdentities / distinctIdentities);
        }
    }
}
