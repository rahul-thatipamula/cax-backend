package com.cax.cax_backend.common.ratelimit;

import com.cax.cax_backend.common.util.ClientIpUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Resolves the matching RateLimitRule for a request, picks a bucket key (IP or userId per
 *  the rule's strategy), and checks/consumes a token — following the same token-bucket
 *  algorithm as the filter this replaces, but per-rule/per-identity instead of one global
 *  per-IP bucket, and with limits editable at runtime via RateLimitConfigService. */
@Service
@RequiredArgsConstructor
public class AdaptiveRateLimiter {

    // Sybil resistance: an account has to have existed for this long before it's allowed to
    // count toward "this looks like a real crowd" in RateLimitAutoScaler. A burst of accounts
    // created minutes ago to fake a surge together won't clear this bar.
    private static final Duration MIN_ACCOUNT_AGE_FOR_TRUST = Duration.ofHours(48);

    private final RateLimitConfigService configService;
    private final RuleLoadRegistry loadRegistry;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Buckets are per (rule name + identity key). expireAfterAccess bounds memory growth —
    // the old filter's ConcurrentHashMap<IP, TokenBucket> never evicted entries at all.
    private final Cache<String, TokenBucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(200_000)
            .build();

    public RateLimitResult check(HttpServletRequest request, Authentication auth) {
        RateLimitConfig config = configService.getConfig();
        RateLimitRule rule = resolveRule(request, config);

        ResolvedIdentity resolved = resolveIdentity(rule, request, auth);

        // Effective multiplier is whichever is higher: an admin's manual event-mode setting,
        // or what RateLimitAutoScaler has detected from real traffic for this rule. Either
        // can drive the boost; neither is required for the other to work.
        RuleLoadTracker tracker = loadRegistry.forRule(rule.getName());
        double manualMultiplier = configService.effectiveMultiplierFor(config, rule);
        double autoMultiplier = rule.isAutoScaleEligible() ? tracker.getAutoMultiplier() : 1.0;
        double multiplier = Math.max(manualMultiplier, autoMultiplier);

        long effectiveCapacity = Math.round(rule.getCapacity() * multiplier);
        double effectiveRefill = rule.getRefillPerSecond() * multiplier;

        String bucketKey = rule.getName() + ":" + resolved.key();
        TokenBucket bucket = buckets.get(bucketKey, k -> new TokenBucket(effectiveCapacity, effectiveRefill));
        bucket.resync(effectiveCapacity, effectiveRefill);

        boolean allowed = bucket.tryConsume();
        tracker.record(resolved.key(), allowed, isTrustedIdentity(resolved, auth));

        return new RateLimitResult(
                allowed,
                rule.getName(),
                bucket.remainingTokens(),
                allowed ? 0 : bucket.secondsUntilNextToken()
        );
    }

    /** key is the bucket identity ("user:<id>" or "ip:<addr>"); userKeyed reflects what it
     *  actually resolved to for this request — important for AUTO rules, where the same rule
     *  can go either way depending on whether the caller is logged in. */
    private record ResolvedIdentity(String key, boolean userKeyed) {}

    private RateLimitRule resolveRule(HttpServletRequest request, RateLimitConfig config) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) path = request.getRequestURI();
        String method = request.getMethod();

        List<RateLimitRule> rules = config.getRules();
        if (rules != null) {
            for (RateLimitRule rule : rules) {
                boolean methodMatches = rule.getMethod() == null
                        || "*".equals(rule.getMethod())
                        || rule.getMethod().equalsIgnoreCase(method);
                if (methodMatches && pathMatcher.match(rule.getPathPattern(), path)) {
                    return rule;
                }
            }
        }
        return config.getDefaultRule();
    }

    private ResolvedIdentity resolveIdentity(RateLimitRule rule, HttpServletRequest request, Authentication auth) {
        boolean wantsUserKey = rule.getKeyStrategy() == RateLimitKeyStrategy.USER
                || rule.getKeyStrategy() == RateLimitKeyStrategy.AUTO;
        if (wantsUserKey) {
            String userId = extractUserId(auth);
            if (userId != null) return new ResolvedIdentity("user:" + userId, true);
            // USER falls back to IP defensively if somehow reached unauthenticated (shouldn't
            // happen — SecurityConfig requires auth on those routes). AUTO falls back to IP by
            // design, for genuinely anonymous callers on a mixed authenticated/public rule.
        }
        return new ResolvedIdentity("ip:" + ClientIpUtil.getClientIp(request), false);
    }

    private String extractUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getCredentials() == null) return null;
        if (!(auth.getCredentials() instanceof Claims claims)) return null;
        String userId = claims.get("userId", String.class);
        if (userId == null) userId = claims.getSubject();
        return userId;
    }

    /** IP-keyed callers are always "trusted" for the purposes of the crowd-surge signal — the
     *  Sybil concern there is different (see google-login's own IP-based limit) and doesn't
     *  map to "account age." For callers that resolved to a user-keyed identity (whether the
     *  rule is USER or AUTO), an account only counts toward a real surge once it's existed for
     *  MIN_ACCOUNT_AGE_FOR_TRUST. A token issued before this claim existed (no
     *  "accountCreatedAt") is treated as trusted, so behavior doesn't regress for already-
     *  logged-in users until they next refresh their token. */
    private boolean isTrustedIdentity(ResolvedIdentity resolved, Authentication auth) {
        if (!resolved.userKeyed()) return true;
        if (auth == null || !auth.isAuthenticated() || !(auth.getCredentials() instanceof Claims claims)) {
            return true;
        }
        Long createdAtMillis = claims.get("accountCreatedAt", Long.class);
        if (createdAtMillis == null) return true;
        Instant createdAt = Instant.ofEpochMilli(createdAtMillis);
        return Duration.between(createdAt, Instant.now()).compareTo(MIN_ACCOUNT_AGE_FOR_TRUST) >= 0;
    }
}
