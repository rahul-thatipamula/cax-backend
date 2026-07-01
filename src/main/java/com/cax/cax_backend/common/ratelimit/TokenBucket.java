package com.cax.cax_backend.common.ratelimit;

/** A single per-key token bucket. Capacity/refill can be resynced in place when the
 *  matching rule's effective values change (e.g. an admin edits a rule, or event mode
 *  toggles), so live traffic picks up new limits without needing to drop buckets. */
class TokenBucket {

    private volatile long capacity;
    private volatile double refillPerSecond;
    private double tokens;
    private long lastRefillTimeMs;

    TokenBucket(long capacity, double refillPerSecond) {
        this.capacity = Math.max(1, capacity);
        this.refillPerSecond = Math.max(0.001, refillPerSecond);
        this.tokens = this.capacity;
        this.lastRefillTimeMs = System.currentTimeMillis();
    }

    /** Updates the bucket's limits in place. If capacity grew, the extra headroom is
     *  granted immediately (so raising a limit takes effect right away, not after a
     *  full refill cycle); if it shrank, current tokens are clamped down to fit. */
    synchronized void resync(long newCapacity, double newRefillPerSecond) {
        long clampedCapacity = Math.max(1, newCapacity);
        if (clampedCapacity != capacity) {
            double delta = clampedCapacity - capacity;
            tokens = Math.max(0, Math.min(clampedCapacity, tokens + Math.max(0, delta)));
            capacity = clampedCapacity;
        }
        refillPerSecond = Math.max(0.001, newRefillPerSecond);
    }

    synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /** Seconds until at least one token will be available, for the Retry-After header. */
    synchronized long secondsUntilNextToken() {
        refill();
        if (tokens >= 1.0) return 0;
        double needed = 1.0 - tokens;
        return Math.max(1, (long) Math.ceil(needed / refillPerSecond));
    }

    synchronized long remainingTokens() {
        refill();
        return (long) tokens;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        double elapsedSeconds = (now - lastRefillTimeMs) / 1000.0;
        if (elapsedSeconds > 0) {
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefillTimeMs = now;
        }
    }
}
