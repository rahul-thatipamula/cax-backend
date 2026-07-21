package com.cax.cax_backend.thought.service;

import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.model.ThoughtEngagementMilestoneState;
import com.cax.cax_backend.notification.repository.ThoughtEngagementMilestoneStateRepository;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.model.ThoughtEngagementScore;
import com.cax.cax_backend.thought.repository.ThoughtEngagementScoreRepository;
import com.cax.cax_backend.thought.repository.ThoughtRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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

    // Engagement milestone ladders (docs/engagement). Steps increase, then
    // repeat at the size of the last step (e.g. likes: ...500, 1000, 2000, 3000...).
    private static final int[] LIKE_MILESTONE_STEPS = {10, 25, 50, 100, 250, 500, 1000};
    private static final int[] COMMENT_MILESTONE_STEPS = {5, 10, 25, 50, 100};

    private final ThoughtEngagementScoreRepository scoreRepository;
    private final ThoughtEngagementMilestoneStateRepository milestoneStateRepository;
    private final ThoughtRepository thoughtRepository;
    private final NotificationService notificationService;
    private final MongoTemplate mongoTemplate;

    /** Called after a like is toggled — updates count, recomputes score, and
     *  notifies the author once if a new like milestone was crossed. */
    @Async("taskExecutor")
    public void onLikeChanged(Thought thought) {
        ThoughtEngagementScore score = getOrCreate(thought);
        score.setLikeCount(thought.getLikes() == null ? 0 : thought.getLikes().size());
        score.setEngagementScore(compute(score, thought));
        score.setLastComputedAt(Instant.now());
        scoreRepository.save(score);

        checkLikeMilestone(thought, score.getLikeCount());
    }

    /** Called after a comment is added or deleted — updates count, recomputes
     *  score, and notifies the author once if a new comment milestone was crossed. */
    @Async("taskExecutor")
    public void onCommentChanged(Thought thought) {
        ThoughtEngagementScore score = getOrCreate(thought);
        score.setCommentCount(thought.getComments() == null ? 0 : thought.getComments().size());
        score.setEngagementScore(compute(score, thought));
        score.setLastComputedAt(Instant.now());
        scoreRepository.save(score);

        checkCommentMilestone(thought, score.getCommentCount());
    }

    private void checkLikeMilestone(Thought thought, int likeCount) {
        ThoughtEngagementMilestoneState state = getOrCreateMilestoneState(thought.getId());
        int milestone = highestMilestoneReached(likeCount, LIKE_MILESTONE_STEPS);
        if (milestone <= state.getLastLikeMilestoneNotified()) {
            return;
        }
        state.setLastLikeMilestoneNotified(milestone);
        state.setUpdatedAt(Instant.now());
        milestoneStateRepository.save(state);
        notifyLikeMilestone(thought, milestone);
    }

    private void checkCommentMilestone(Thought thought, int commentCount) {
        ThoughtEngagementMilestoneState state = getOrCreateMilestoneState(thought.getId());
        int milestone = highestMilestoneReached(commentCount, COMMENT_MILESTONE_STEPS);
        if (milestone <= state.getLastCommentMilestoneNotified()) {
            return;
        }
        state.setLastCommentMilestoneNotified(milestone);
        state.setUpdatedAt(Instant.now());
        milestoneStateRepository.save(state);
        notifyCommentMilestone(thought, milestone);
    }

    /** Atomic upsert — plain find-then-build-then-save races under concurrent
     *  likes/comments on the same thought and creates duplicate state docs,
     *  which then makes every future findByThoughtId() throw
     *  IncorrectResultSizeDataAccessException ("returned non unique result"). */
    private ThoughtEngagementMilestoneState getOrCreateMilestoneState(String thoughtId) {
        Query query = new Query(Criteria.where("thoughtId").is(thoughtId));
        Update update = new Update()
                .setOnInsert("thoughtId", thoughtId)
                .setOnInsert("lastLikeMilestoneNotified", 0)
                .setOnInsert("lastCommentMilestoneNotified", 0);
        return mongoTemplate.findAndModify(
                query, update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                ThoughtEngagementMilestoneState.class);
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

                    String title = "Your Thought is Trending";
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
    // Engagement milestone helpers (docs/engagement)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the highest step in {@code steps} that {@code count} has reached,
     * or 0 if none. Once past the last step, repeats at that step's size
     * (e.g. steps ending in 100 -> 200, 300...). Returning a single highest
     * value (not a list) ensures a count that jumps two steps at once
     * (e.g. a batch of likes) only ever fires one notification.
     */
    private int highestMilestoneReached(long count, int[] steps) {
        int highest = 0;
        for (int step : steps) {
            if (count >= step) {
                highest = step;
            }
        }
        int lastStep = steps[steps.length - 1];
        if (count > lastStep) {
            highest = lastStep + (int) (((count - lastStep) / lastStep) * lastStep);
        }
        return highest;
    }

    private void notifyLikeMilestone(Thought thought, int milestone) {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "THOUGHT_LIKE_MILESTONE");
            data.put("postId", thought.getId());
            data.put("deepLink", "app://feed/post/" + thought.getId());

            notificationService.createNotification(
                    thought.getUserId(),
                    "🔥 " + milestone + " Likes!",
                    "Your thought \"" + thought.getHeading() + "\" just hit " + milestone + " likes. Keep it up!",
                    NotificationType.FEED,
                    data
            );
        } catch (Exception e) {
            log.error("[ThoughtEngagement] Failed to send like milestone notification for thought {}: {}",
                    thought.getId(), e.getMessage());
        }
    }

    private void notifyCommentMilestone(Thought thought, int milestone) {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "THOUGHT_COMMENT_MILESTONE");
            data.put("postId", thought.getId());
            data.put("deepLink", "app://feed/post/" + thought.getId());

            notificationService.createNotification(
                    thought.getUserId(),
                    "💬 " + milestone + " Comments!",
                    "Your thought \"" + thought.getHeading() + "\" is sparking conversation — " + milestone + " comments and counting.",
                    NotificationType.FEED,
                    data
            );
        } catch (Exception e) {
            log.error("[ThoughtEngagement] Failed to send comment milestone notification for thought {}: {}",
                    thought.getId(), e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Atomic upsert for the same reason as getOrCreateMilestoneState() above —
     *  onLikeChanged/onCommentChanged/onView run @Async, so a plain
     *  find-then-build-then-save on concurrent events for the same thought
     *  can create duplicate score docs and break the unique thoughtId lookup. */
    private ThoughtEngagementScore getOrCreate(Thought thought) {
        Query query = new Query(Criteria.where("thoughtId").is(thought.getId()));
        Update update = new Update()
                .setOnInsert("thoughtId", thought.getId())
                .setOnInsert("collegeId", thought.getCollegeId())
                .setOnInsert("authorUserId", thought.getUserId())
                .setOnInsert("thoughtCreatedAt", thought.getCreatedAt())
                .setOnInsert("likeCount", thought.getLikes() == null ? 0 : thought.getLikes().size())
                .setOnInsert("commentCount", thought.getComments() == null ? 0 : thought.getComments().size())
                .setOnInsert("viewCount", 0)
                .setOnInsert("engagementScore", 0.0)
                .setOnInsert("lastComputedAt", Instant.now());
        return mongoTemplate.findAndModify(
                query, update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                ThoughtEngagementScore.class);
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
