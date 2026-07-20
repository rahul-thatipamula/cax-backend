package com.cax.cax_backend.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Loads/edits the rate-limit config (rules + event mode), following the same singleton-Mongo-
 *  document pattern as settings.service.SystemSettingService. The current config is cached
 *  in memory for a few seconds so the hot path (every request) doesn't hit Mongo — admin edits
 *  take effect within that window rather than instantly, which is an acceptable tradeoff for
 *  a config that changes rarely (an admin flipping "hackathon mode" a few times a year, not
 *  something read-after-write critical). */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitConfigService {

    private static final long CACHE_TTL_MS = 10_000;

    private final RateLimitConfigRepository repository;

    private final AtomicReference<RateLimitConfig> cached = new AtomicReference<>();
    private volatile long cachedAtMs = 0;

    public RateLimitConfig getConfig() {
        long now = System.currentTimeMillis();
        RateLimitConfig current = cached.get();
        if (current != null && (now - cachedAtMs) < CACHE_TTL_MS) {
            return current;
        }
        RateLimitConfig loaded = repository.findById("global").orElseGet(this::seedDefaults);
        cached.set(loaded);
        cachedAtMs = now;
        return loaded;
    }

    /** Forces the next getConfig() call to hit Mongo — called after any admin edit so
     *  changes are visible immediately rather than waiting out the cache TTL. */
    public void invalidateCache() {
        cachedAtMs = 0;
    }

    public RateLimitConfig updateRules(List<RateLimitRule> rules, RateLimitRule defaultRule) {
        RateLimitConfig config = getConfig();
        if (rules != null) config.setRules(rules);
        if (defaultRule != null) config.setDefaultRule(defaultRule);
        config.setUpdatedAt(Instant.now());
        RateLimitConfig saved = repository.save(config);
        invalidateCache();
        log.info("Rate limit rules updated ({} rules)", saved.getRules() != null ? saved.getRules().size() : 0);
        return saved;
    }

    public RateLimitConfig setEventMode(boolean enabled, Double multiplier, Instant expiresAt) {
        RateLimitConfig config = getConfig();
        config.setEventModeEnabled(enabled);
        if (multiplier != null) config.setEventModeMultiplier(Math.max(1.0, multiplier));
        config.setEventModeExpiresAt(expiresAt);
        config.setUpdatedAt(Instant.now());
        RateLimitConfig saved = repository.save(config);
        invalidateCache();
        log.info("Rate limit event mode set: enabled={}, multiplier={}, expiresAt={}",
                enabled, saved.getEventModeMultiplier(), expiresAt);
        return saved;
    }

    /** Event mode is only "live" if enabled AND (no expiry set OR expiry hasn't passed yet) —
     *  this is the auto-revert safety net so a forgotten toggle doesn't leave limits wide
     *  open indefinitely after the event ends. */
    public boolean isEventModeLive(RateLimitConfig config) {
        if (!config.isEventModeEnabled()) return false;
        Instant expiresAt = config.getEventModeExpiresAt();
        return expiresAt == null || Instant.now().isBefore(expiresAt);
    }

    public double effectiveMultiplierFor(RateLimitConfig config, RateLimitRule rule) {
        if (!isEventModeLive(config)) return 1.0;
        if (rule.getEventMultiplierOverride() != null) {
            return Math.max(1.0, rule.getEventMultiplierOverride());
        }
        return Math.max(1.0, config.getEventModeMultiplier());
    }

    private RateLimitConfig seedDefaults() {
        log.info("Seeding default rate_limit_config document");
        RateLimitConfig config = RateLimitConfig.builder()
                .id("global")
                .rules(defaultRules())
                // AUTO keys by user for anything authenticated (home screen, profile,
                // notifications, coins, etc. — none of which have their own explicit rule
                // above) and only falls back to per-IP for genuinely anonymous callers, so
                // logged-in browsing doesn't share a bucket with pre-login/public traffic.
                .defaultRule(RateLimitRule.builder()
                        .name("default")
                        .pathPattern("/**")
                        .method("*")
                        .keyStrategy(RateLimitKeyStrategy.AUTO)
                        .capacity(120)
                        .refillPerSecond(4)
                        .build())
                .eventModeEnabled(false)
                .eventModeMultiplier(1.0)
                .updatedAt(Instant.now())
                .build();
        return repository.save(config);
    }

    /** Seed rules mapped to endpoints that actually exist in this backend. Note: this app has
     *  no password login, no OTP (SMS/email), no password reset, and no chat feature — so
     *  those categories from a generic rate-limit checklist are intentionally omitted rather
     *  than mapped to something that doesn't exist. Ordered most-specific-first. */
    private List<RateLimitRule> defaultRules() {
        return List.of(
                // ── Auth (unauthenticated — must key by IP; never auto-boosted, see
                //    autoScaleEligible javadoc on RateLimitRule) ──
                RateLimitRule.builder().name("google-login").pathPattern("/api/auth/google").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.IP).capacity(100).refillPerSecond(5)
                        .autoScaleEligible(false).build(),
                RateLimitRule.builder().name("2fa-verify").pathPattern("/api/auth/2fa/login-verify").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.IP).capacity(20).refillPerSecond(0.5)
                        .autoScaleEligible(false).build(),

                // Public, unauthenticated organizer event submission (caxone.in/postEvent) — also
                // CAPTCHA-gated (TurnstileService), but IP-keyed rate limiting is a cheap first line
                // of defense against scripted spam before a request even reaches CAPTCHA verification.
                RateLimitRule.builder().name("public-bulletin-submit")
                        .pathPattern("/api/public/bulletin-events/submit").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.IP).capacity(5).refillPerSecond(0.01)
                        .autoScaleEligible(false).build(),

                // ── Bingo gameplay — the hackathon polling hotspot ──
                RateLimitRule.builder().name("bingo-mark").pathPattern("/api/games/bingo/player/*/mark").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(20).refillPerSecond(1).build(),
                RateLimitRule.builder().name("bingo-join").pathPattern("/api/games/bingo/player/*/join").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(10).refillPerSecond(0.2).build(),
                RateLimitRule.builder().name("bingo-player-games").pathPattern("/api/games/bingo/player/games").method("GET")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(60).refillPerSecond(2).build(),
                // Public polling endpoints (game status, card, leaderboard, signer-usage) — no auth,
                // so keyed by IP, but deliberately generous since many phones share venue WiFi NAT.
                // Give this rule its own event-mode override so "hackathon mode" can boost just this
                // without touching login/creation limits.
                RateLimitRule.builder().name("bingo-public-polling").pathPattern("/api/games/bingo/public/**").method("GET")
                        .keyStrategy(RateLimitKeyStrategy.IP).capacity(600).refillPerSecond(40)
                        .eventMultiplierOverride(10.0).build(),

                // ── Feed (per user, generous — scrolled/polled often) ──
                RateLimitRule.builder().name("feed-thoughts").pathPattern("/api/thoughts/feed").method("GET")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(120).refillPerSecond(4).build(),
                RateLimitRule.builder().name("feed-org-posts").pathPattern("/api/organizations/posts/feed").method("GET")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(120).refillPerSecond(4).build(),

                // ── Like (per user, moderate — allow rapid use, block spam) ──
                RateLimitRule.builder().name("like-thought").pathPattern("/api/thoughts/*/like").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(60).refillPerSecond(2).build(),
                RateLimitRule.builder().name("like-org-post").pathPattern("/api/organizations/posts/*/like").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(60).refillPerSecond(2).build(),

                // ── Create (per user, strict — rarer and more expensive actions) ──
                RateLimitRule.builder().name("create-thought").pathPattern("/api/thoughts").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(20).refillPerSecond(0.3).build(),
                RateLimitRule.builder().name("create-org-post").pathPattern("/api/organizations/*/posts").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(20).refillPerSecond(0.3).build(),
                RateLimitRule.builder().name("create-organization").pathPattern("/api/organizations").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(10).refillPerSecond(0.1).build(),
                RateLimitRule.builder().name("create-event").pathPattern("/api/organizations/*/events").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(10).refillPerSecond(0.1).build(),
                RateLimitRule.builder().name("create-ripple").pathPattern("/api/organizations/*/ripples").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(20).refillPerSecond(0.3).build(),
                RateLimitRule.builder().name("create-bingo-game").pathPattern("/api/games/bingo").method("POST")
                        .keyStrategy(RateLimitKeyStrategy.USER).capacity(10).refillPerSecond(0.1).build()
        );
    }
}
