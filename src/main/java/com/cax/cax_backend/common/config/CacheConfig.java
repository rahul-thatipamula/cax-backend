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

        cacheManager.setCaches(List.of(clubsCache, carouselsCache, collegesCache, adsCache));
        return cacheManager;
    }
}
