package com.cax.cax_backend.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        CaffeineCache clubsCache = new CaffeineCache("clubs", Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build());

        CaffeineCache carouselsCache = new CaffeineCache("carousels", Caffeine.newBuilder()
                .initialCapacity(50)
                .maximumSize(200)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build());

        // Colleges changes very rarely and has > 550 entries. Increase size and expiration to 1 hour.
        CaffeineCache collegesCache = new CaffeineCache("colleges", Caffeine.newBuilder()
                .initialCapacity(600)
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats()
                .build());

        CaffeineCache adsCache = new CaffeineCache("ads", Caffeine.newBuilder()
                .initialCapacity(50)
                .maximumSize(200)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build());

        // Bingo live-game caches: every player in a game polls game status, leaderboard
        // and signer usage every 5–8s, so a short TTL absorbs almost all of that read
        // load (one Mongo query per game per interval instead of one per player).
        // Writes (mark/join/start/end) evict, so players still see changes immediately.
        CaffeineCache bingoGameCache = new CaffeineCache("bingoGame", Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .recordStats()
                .build());

        CaffeineCache bingoPlayerCountCache = new CaffeineCache("bingoPlayerCount", Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .recordStats()
                .build());

        CaffeineCache bingoLeaderboardCache = new CaffeineCache("bingoLeaderboard", Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .recordStats()
                .build());

        CaffeineCache bingoSignerUsageCache = new CaffeineCache("bingoSignerUsage", Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .recordStats()
                .build());

        // Arcade prompt bank: read on every round start but effectively static, so it is held
        // far longer than the live-game caches. Nothing evicts it — a seeded or admin-edited
        // prompt becomes visible on the next expiry, which is fine for content this static.
        CaffeineCache arcadePromptsCache = new CaffeineCache("arcadePrompts", Caffeine.newBuilder()
                .maximumSize(20)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build());

        cacheManager.setCaches(List.of(clubsCache, carouselsCache, collegesCache, adsCache,
                bingoGameCache, bingoPlayerCountCache, bingoLeaderboardCache, bingoSignerUsageCache,
                arcadePromptsCache));
        return cacheManager;
    }
}
