package com.cax.cax_backend.thought.service;

import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.model.ThoughtEngagementScore;
import com.cax.cax_backend.thought.repository.ThoughtEngagementScoreRepository;
import com.cax.cax_backend.thought.repository.ThoughtRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThoughtEngagementService {

    private static final double LIKE_WEIGHT    = 1.0;
    private static final double COMMENT_WEIGHT = 3.0;
    private static final double VIEW_WEIGHT    = 0.1;
    private static final double GRAVITY        = 1.5;
    // Author who posts 50+ thoughts in 30 days gets the max 20% bonus
    private static final double AUTHOR_BONUS_DIVISOR = 50.0;
    private static final double AUTHOR_BONUS_MAX      = 0.20;

    /**
     * Minimum engagement score for a thought to be considered "trending".
     * Score formula: (likes + comments×3 + views×0.1) / (ageHours+2)^1.5
     * A score of 1.0 roughly equals ~10 likes within 2 hours of posting.
     */
    private static final double TRENDING_THRESHOLD = 1.0;

    private final ThoughtEngagementScoreRepository scoreRepository;
    private final ThoughtRepository thoughtRepository;
    private final NotificationService notificationService;

    /** Called after a like is toggled — updates count and recomputes score. */
    @Async("taskExecutor")
    public void onLikeChanged(Thought thought) {
        ThoughtEngagementScore score = getOrCreate(thought);
        score.setLikeCount(thought.getLikes() == null ? 0 : thought.getLikes().size());
        score.setEngagementScore(compute(score, thought));
        score.setLastComputedAt(Instant.now());
        scoreRepository.save(score);
    }

    /** Called after a comment is added or deleted — updates count and recomputes score. */
    @Async("taskExecutor")
    public void onCommentChanged(Thought thought) {
        ThoughtEngagementScore score = getOrCreate(thought);
        score.setCommentCount(thought.getComments() == null ? 0 : thought.getComments().size());
        score.setEngagementScore(compute(score, thought));
        score.setLastComputedAt(Instant.now());
        scoreRepository.save(score);
    }

    /** Called on view — increments counter by 1 and recomputes. Callers should debounce. */
    @Async("taskExecutor")
    public void onView(String thoughtId) {
        thoughtRepository.findById(thoughtId).ifPresent(thought -> {
            ThoughtEngagementScore score = getOrCreate(thought);
            score.setViewCount(score.getViewCount() + 1);
            score.setEngagementScore(compute(score, thought));
            score.setLastComputedAt(Instant.now());
            scoreRepository.save(score);
        });
    }

    /** Removes the score doc when a thought is deleted. */
    @Async("taskExecutor")
    public void onThoughtDeleted(String thoughtId) {
        scoreRepository.deleteByThoughtId(thoughtId);
    }

    /** Returns global top-10 by score. */
    public List<ThoughtEngagementScore> getTopTrending(int limit) {
        // limit is advisory — repository method is fixed at 10 for now
        return scoreRepository.findTop10ByOrderByEngagementScoreDesc();
    }

    /** Returns college-scoped top-10 by score. */
    public List<ThoughtEngagementScore> getTopTrendingByCollege(String collegeId) {
        return scoreRepository.findTop10ByCollegeIdOrderByEngagementScoreDesc(collegeId);
    }

    /**
     * Runs every 15 hours to recompute all engagement scores for time-decay,
     * then checks if any newly-trending thoughts should notify their author.
     *
     * Two jobs in one pass keeps DB reads minimal — we already have every
     * score loaded, so the trending check is free.
     */
    @Scheduled(cron = "0 0 */15 * * *")
    public void recomputeAllScores() {
        log.info("[ThoughtEngagement] Starting scheduled score recomputation (15h cycle)...");
        List<ThoughtEngagementScore> all = scoreRepository.findAll();

        for (ThoughtEngagementScore score : all) {
            try {
                thoughtRepository.findById(score.getThoughtId()).ifPresent(thought -> {
                    // Refresh counts from the live document
                    score.setLikeCount(thought.getLikes() == null ? 0 : thought.getLikes().size());
                    score.setCommentCount(thought.getComments() == null ? 0 : thought.getComments().size());
                    score.setEngagementScore(compute(score, thought));
                    score.setLastComputedAt(Instant.now());
                    scoreRepository.save(score);
                });
            } catch (Exception e) {
                log.error("[ThoughtEngagement] Failed to recompute score for thoughtId={}: {}", score.getThoughtId(), e.getMessage());
            }
        }
        log.info("[ThoughtEngagement] Recomputed {} scores.", all.size());

        // After recompute, notify authors of newly-trending thoughts (once per thought)
        notifyTrendingAuthors();
    }

    /**
     * Finds all thoughts that have crossed the trending threshold but whose
     * author has not yet been notified. Marks each as notified immediately
     * before sending the push so a retry can't double-fire.
     */
    private void notifyTrendingAuthors() {
        List<ThoughtEngagementScore> newlyTrending = scoreRepository
                .findByEngagementScoreGreaterThanEqualAndTrendingNotifiedAtIsNull(TRENDING_THRESHOLD);

        if (newlyTrending.isEmpty()) {
            log.info("[ThoughtEngagement] No newly trending thoughts to notify.");
            return;
        }

        log.info("[ThoughtEngagement] Found {} newly trending thought(s) to notify.", newlyTrending.size());

        for (ThoughtEngagementScore score : newlyTrending) {
            try {
                // Mark notified first — prevents double-fire if the loop crashes halfway
                score.setTrendingNotifiedAt(Instant.now());
                scoreRepository.save(score);

                thoughtRepository.findById(score.getThoughtId()).ifPresent(thought -> {
                    Map<String, String> data = new HashMap<>();
                    data.put("type", "THOUGHT_TRENDING");
                    data.put("postId", thought.getId());
                    data.put("deepLink", "app://feed/post/" + thought.getId());

                    String title = "🔥 Your thought is trending!";
                    String body = "\"" + thought.getHeading() + "\" is getting a lot of attention right now.";

                    notificationService.createNotification(
                            thought.getUserId(),
                            title,
                            body,
                            NotificationType.FEED,
                            data
                    );
                    log.info("[ThoughtEngagement] Trending notification sent to author {} for thought {}",
                            thought.getUserId(), thought.getId());
                });
            } catch (Exception e) {
                log.error("[ThoughtEngagement] Failed to send trending notification for thoughtId={}: {}",
                        score.getThoughtId(), e.getMessage());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private ThoughtEngagementScore getOrCreate(Thought thought) {
        return scoreRepository.findByThoughtId(thought.getId()).orElseGet(() ->
            ThoughtEngagementScore.builder()
                .thoughtId(thought.getId())
                .collegeId(thought.getCollegeId())
                .authorUserId(thought.getUserId())
                .thoughtCreatedAt(thought.getCreatedAt())
                .likeCount(thought.getLikes() == null ? 0 : thought.getLikes().size())
                .commentCount(thought.getComments() == null ? 0 : thought.getComments().size())
                .viewCount(0)
                .build()
        );
    }

    /**
     * Score = (likes×1 + comments×3 + views×0.1) / (ageHours+2)^1.5 × (1 + authorBonus)
     *
     * Age decay ensures newer posts with similar engagement outrank old ones.
     * Author bonus (up to +20%) rewards users who actively create thoughts.
     */
    private double compute(ThoughtEngagementScore score, Thought thought) {
        double rawEngagement =
            (score.getLikeCount()    * LIKE_WEIGHT)
          + (score.getCommentCount() * COMMENT_WEIGHT)
          + (score.getViewCount()    * VIEW_WEIGHT);

        Instant createdAt = thought.getCreatedAt() != null ? thought.getCreatedAt() : Instant.now();
        double ageHours = Math.max(1.0, ChronoUnit.MINUTES.between(createdAt, Instant.now()) / 60.0);
        double decayed  = rawEngagement / Math.pow(ageHours + 2.0, GRAVITY);

        // Author bonus: how many thoughts has this author published in the last 30 days?
        double authorBonus = 0.0;
        try {
            Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
            long authorCount = thoughtRepository.countActiveByUserSince(thought.getUserId(), thirtyDaysAgo);
            authorBonus = Math.min(AUTHOR_BONUS_MAX, authorCount / AUTHOR_BONUS_DIVISOR);
        } catch (Exception ignored) {}

        return decayed * (1.0 + authorBonus);
    }
}
