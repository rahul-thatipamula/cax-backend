package com.cax.cax_backend.user.service;

import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
    private final MongoTemplate mongoTemplate;

    // Cache to throttle database writes (UserId -> Last updated time)
    private final Map<String, Instant> lastUpdatedCache = new ConcurrentHashMap<>();

    /**
     * Dynamically update a user's last seen time.
     * Throttled to at most once per minute to avoid MongoDB write amplification.
     */
    @Async("taskExecutor")
    public void updateLastSeen(String userId, String appVersion, int buildNumber) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        Instant now = Instant.now();
        Instant lastUpdated = lastUpdatedCache.get(userId);

        if (lastUpdated == null || lastUpdated.plus(Duration.ofMinutes(1)).isBefore(now)) {
            lastUpdatedCache.put(userId, now);
            try {
                Query query = new Query(Criteria.where("userId").is(userId));
                Update update = new Update()
                        .set("lastSeenAt", now)
                        .set("isOnline", true);
                
                if (appVersion != null && !appVersion.isBlank()) {
                    update.set("appVersion", appVersion);
                }
                if (buildNumber > 0) {
                    update.set("buildNumber", buildNumber);
                }
                
                mongoTemplate.updateFirst(query, update, User.class);
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
            
            // First fetch only the userIds of stale online users to evict them from the throttle cache
            Query findQuery = new Query(Criteria.where("isOnline").is(true).orOperator(
                    Criteria.where("lastSeenAt").lt(threshold),
                    Criteria.where("lastSeenAt").isNull()
            ));
            findQuery.fields().include("userId");
            List<User> staleUsers = mongoTemplate.find(findQuery, User.class);

            if (!staleUsers.isEmpty()) {
                log.info("Marking {} inactive users as offline", staleUsers.size());
                
                Query updateQuery = new Query(Criteria.where("isOnline").is(true).orOperator(
                        Criteria.where("lastSeenAt").lt(threshold),
                        Criteria.where("lastSeenAt").isNull()
                ));
                Update update = new Update().set("isOnline", false);
                mongoTemplate.updateMulti(updateQuery, update, User.class);

                for (User user : staleUsers) {
                    lastUpdatedCache.remove(user.getUserId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to clean up offline users in scheduler", e);
        }
    }
}
