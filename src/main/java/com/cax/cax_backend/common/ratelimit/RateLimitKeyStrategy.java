package com.cax.cax_backend.common.ratelimit;

/** How a rate limit bucket is keyed for a given rule. */
public enum RateLimitKeyStrategy {
    /** One bucket per client IP — the only option before a caller is authenticated
     *  (e.g. the Google login endpoint itself, or public read-only endpoints). */
    IP,
    /** One bucket per authenticated userId — used once a request carries a valid JWT,
     *  so one busy shared WiFi network can't exhaust another user's quota. */
    USER,
    /** Per-user if the request is authenticated, per-IP otherwise. Intended for catch-all
     *  rules (like the default fallback) that cover a mix of authenticated browsing and
     *  genuinely anonymous public traffic — without this, an authenticated but uncovered
     *  endpoint (e.g. home screen, profile, notifications) would get bucketed by IP just
     *  like pre-login traffic, recreating the shared-WiFi bottleneck for logged-in users. */
    AUTO
}
