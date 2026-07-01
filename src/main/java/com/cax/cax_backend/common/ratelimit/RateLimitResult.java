package com.cax.cax_backend.common.ratelimit;

public record RateLimitResult(boolean allowed, String ruleName, long remaining, long retryAfterSeconds) {
}
