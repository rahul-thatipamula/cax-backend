package com.cax.cax_backend.user.service;

import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserRepository userRepository;

    // Cache to throttle database writes (UserId -> Last updated time)
    private final Map<String, Instant> lastUpdatedCache = new ConcurrentHashMap<>();

    /**
     * Dynamically update a user's last seen time.
     * Throttled to at most once per minute to avoid MongoDB write amplification.
     */
    @Async("taskExecutor")
    public void updateLastSeen(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        Instant now = Instant.now();
        Instant lastUpdated = lastUpdatedCache.get(userId);

        if (lastUpdated == null || lastUpdated.plus(Duration.ofMinutes(1)).isBefore(now)) {
            lastUpdatedCache.put(userId, now);
            try {
                userRepository.findByUserId(userId).ifPresent(user -> {
                    user.setLastSeenAt(now);
                    // Ensure isOnline is true if they make any API requests
                    if (!user.isOnline()) {
                        user.setOnline(true);
                    }
                    userRepository.save(user);
                });
            } catch (Exception e) {
                log.error("Failed to update last seen for user: {}", userId, e);
            }
        }
    }

    /**
     * Scheduled job running every minute to clean up stale online sessions.
     * If a user hasn't made any requests in 5 minutes, they are marked offline.
     */
    @Scheduled(cron = "0 */1 * * * *")
    public void cleanupOfflineUsers() {
        try {
            Instant threshold = Instant.now().minus(Duration.ofMinutes(5));
            List<User> staleUsers = userRepository.findStaleOnlineUsers(threshold);
            if (!staleUsers.isEmpty()) {
                log.info("Marking {} inactive users as offline", staleUsers.size());
                for (User user : staleUsers) {
                    user.setOnline(false);
                    // Also clear from in-memory throttle cache so next request writes immediately
                    lastUpdatedCache.remove(user.getUserId());
                }
                userRepository.saveAll(staleUsers);
            }
        } catch (Exception e) {
            log.error("Failed to clean up offline users in scheduler", e);
        }
    }
}
