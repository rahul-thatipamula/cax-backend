package com.cax.cax_backend.organization.service;

import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.model.OrganizationPostMilestoneState;
import com.cax.cax_backend.notification.repository.OrganizationPostMilestoneStateRepository;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.organization.model.OrganizationMember;
import com.cax.cax_backend.organization.model.OrganizationPost;
import com.cax.cax_backend.organization.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Announces like/comment milestones for an {@link OrganizationPost} to the
 * whole organization — every current member (President down to Member) gets a
 * celebratory push when the club's post crosses a milestone.
 *
 * Reuses the thought-milestone pattern (monotonic "highest milestone" gate in a
 * separate state collection, single-highest-step-per-event, once-ever firing)
 * but differs in the recipient set: this is a 1:many fan-out to org members
 * rather than a 1:1 push to the author. Recipients are resolved server-side from
 * {@code organization_members} and each send is re-filtered for block/opt-out
 * status inside {@link NotificationService#createNotification}.
 *
 * See docs/engagement/THOUGHT_ENGAGEMENT_MILESTONES_2026-07-21.md.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationPostEngagementService {

    // Same ladders as thought milestones: steps increase, then repeat at the
    // size of the last step (likes: ...500, 1000, 2000, 3000...).
    private static final int[] LIKE_MILESTONE_STEPS = {10, 25, 50, 100, 250, 500, 1000};
    private static final int[] COMMENT_MILESTONE_STEPS = {5, 10, 25, 50, 100};

    private final OrganizationPostMilestoneStateRepository milestoneStateRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final NotificationService notificationService;
    private final MongoTemplate mongoTemplate;

    /** Called after a like is toggled on an org post — notifies the whole org
     *  once if a new like milestone was crossed. */
    @Async("taskExecutor")
    public void onLikeChanged(OrganizationPost post) {
        int likeCount = post.getLikes() == null ? 0 : post.getLikes().size();
        OrganizationPostMilestoneState state = getOrCreateMilestoneState(post.getId());
        int milestone = highestMilestoneReached(likeCount, LIKE_MILESTONE_STEPS);
        if (milestone <= state.getLastLikeMilestoneNotified()) {
            return;
        }
        state.setLastLikeMilestoneNotified(milestone);
        state.setUpdatedAt(Instant.now());
        milestoneStateRepository.save(state);
        notifyLikeMilestone(post, milestone);
    }

    /** Called after a comment is added on an org post — notifies the whole org
     *  once if a new comment milestone was crossed. */
    @Async("taskExecutor")
    public void onCommentChanged(OrganizationPost post) {
        int commentCount = post.getComments() == null ? 0 : post.getComments().size();
        OrganizationPostMilestoneState state = getOrCreateMilestoneState(post.getId());
        int milestone = highestMilestoneReached(commentCount, COMMENT_MILESTONE_STEPS);
        if (milestone <= state.getLastCommentMilestoneNotified()) {
            return;
        }
        state.setLastCommentMilestoneNotified(milestone);
        state.setUpdatedAt(Instant.now());
        milestoneStateRepository.save(state);
        notifyCommentMilestone(post, milestone);
    }

    /** Atomic upsert — likes/comments run @Async, so a plain
     *  find-then-build-then-save on concurrent events for the same post can
     *  create duplicate state docs and break the unique postId lookup. */
    private OrganizationPostMilestoneState getOrCreateMilestoneState(String postId) {
        Query query = new Query(Criteria.where("postId").is(postId));
        Update update = new Update()
                .setOnInsert("postId", postId)
                .setOnInsert("lastLikeMilestoneNotified", 0)
                .setOnInsert("lastCommentMilestoneNotified", 0);
        return mongoTemplate.findAndModify(
                query, update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                OrganizationPostMilestoneState.class);
    }

    /**
     * Returns the highest step in {@code steps} that {@code count} has reached,
     * or 0 if none. Past the last step it repeats at that step's size. Returning
     * a single highest value (not a list) means a count that jumps two steps at
     * once only ever fires one notification.
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

    private void notifyLikeMilestone(OrganizationPost post, int milestone) {
        String orgName = orgName(post);
        Map<String, String> data = milestoneData(post, "ORG_POST_LIKE_MILESTONE");
        String title = "🎉 " + milestone + " likes for " + orgName + "!";
        String body = orgName + "'s post just hit " + milestone + " likes — the campus is loving it! 🔥";
        fanOutToOrganization(post, title, body, data);
    }

    private void notifyCommentMilestone(OrganizationPost post, int milestone) {
        String orgName = orgName(post);
        Map<String, String> data = milestoneData(post, "ORG_POST_COMMENT_MILESTONE");
        String title = "💬 " + milestone + " comments for " + orgName + "!";
        String body = orgName + "'s post is sparking conversation — " + milestone + " comments and counting.";
        fanOutToOrganization(post, title, body, data);
    }

    /**
     * Persists + pushes the milestone notification to every current member of
     * the organization. Per-member failures are logged, never thrown, so one bad
     * FCM token can't abort the fan-out or break the like/comment flow that
     * triggered it.
     */
    private void fanOutToOrganization(OrganizationPost post, String title, String body, Map<String, String> data) {
        try {
            List<OrganizationMember> members = organizationMemberRepository.findByOrganizationId(post.getOrganizationId());
            int sent = 0;
            for (OrganizationMember member : members) {
                if (member.getUserId() == null || member.getUserId().isBlank()) {
                    continue;
                }
                try {
                    notificationService.createNotification(
                            member.getUserId(), title, body, NotificationType.FEED, data);
                    sent++;
                } catch (Exception e) {
                    log.error("[OrgPostMilestone] Failed to notify member {} for post {}: {}",
                            member.getUserId(), post.getId(), e.getMessage());
                }
            }
            log.info("[OrgPostMilestone] Sent milestone notification for post {} (org {}) to {}/{} member(s): {}",
                    post.getId(), post.getOrganizationId(), sent, members.size(), title);
        } catch (Exception e) {
            log.error("[OrgPostMilestone] Failed milestone fan-out for post {}: {}", post.getId(), e.getMessage());
        }
    }

    private Map<String, String> milestoneData(OrganizationPost post, String type) {
        Map<String, String> data = new HashMap<>();
        data.put("type", type);
        data.put("postId", post.getId());
        data.put("organizationId", post.getOrganizationId());
        data.put("organizationName", orgName(post));
        // Reuses the same deep link as ORG_POST_CREATED so the notification opens
        // the post directly (handled by the Flutter notification handler).
        data.put("deepLink", "app://club/post/" + post.getId());
        return data;
    }

    private String orgName(OrganizationPost post) {
        return post.getOrganizationName() != null && !post.getOrganizationName().isBlank()
                ? post.getOrganizationName()
                : "Your club";
    }
}
