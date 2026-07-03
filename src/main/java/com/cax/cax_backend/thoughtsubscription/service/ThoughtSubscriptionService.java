package com.cax.cax_backend.thoughtsubscription.service;

import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.thoughtsubscription.event.ThoughtSubscribedEvent;
import com.cax.cax_backend.thoughtsubscription.model.ThoughtSubscription;
import com.cax.cax_backend.thoughtsubscription.repository.ThoughtSubscriptionRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ThoughtSubscriptionService {

    // Anti-spam guard: re-subscribing within this window after a recent
    // "new subscriber" notification won't re-notify the author, so rapid
    // subscribe/unsubscribe toggling can't be used to spam someone.
    private static final Duration NOTIFY_COOLDOWN = Duration.ofMinutes(30);

    private final ThoughtSubscriptionRepository subscriptionRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    public ThoughtSubscription subscribe(String subscriberId, String authorId) {
        if (subscriberId.equals(authorId))
            throw new BusinessException.BadRequestException("You cannot subscribe to yourself");

        ThoughtSubscription existing = subscriptionRepository
                .findBySubscriberIdAndAuthorId(subscriberId, authorId)
                .orElse(null);

        if (existing != null && existing.isActive()) {
            // Already subscribed — idempotent no-op, no duplicate notification.
            return existing;
        }

        Instant now = Instant.now();
        boolean shouldNotify = existing == null
                || existing.getLastNotifiedAt() == null
                || Duration.between(existing.getLastNotifiedAt(), now).compareTo(NOTIFY_COOLDOWN) >= 0;

        ThoughtSubscription toSave;
        if (existing != null) {
            existing.setActive(true);
            existing.setSubscribedAt(now);
            existing.setUnsubscribedAt(null);
            if (shouldNotify) existing.setLastNotifiedAt(now);
            toSave = existing;
        } else {
            User author = userService.getUserByUserId(authorId);
            toSave = ThoughtSubscription.builder()
                    .subscriberId(subscriberId)
                    .authorId(authorId)
                    .authorName(author.getThoughtsDisplayName())
                    .authorPicture(author.getPicture())
                    .subscribedAt(now)
                    .lastNotifiedAt(shouldNotify ? now : null)
                    .build();
        }

        ThoughtSubscription saved = subscriptionRepository.save(toSave);

        if (shouldNotify) {
            User subscriber = userService.getUserByUserId(subscriberId);
            eventPublisher.publishEvent(new ThoughtSubscribedEvent(
                    this, subscriberId, subscriber.getThoughtsDisplayName(),
                    subscriber.getPicture(), authorId));
        }

        return saved;
    }

    public void unsubscribe(String subscriberId, String authorId) {
        subscriptionRepository.findBySubscriberIdAndAuthorId(subscriberId, authorId).ifPresent(sub -> {
            if (!sub.isActive()) return;
            sub.setActive(false);
            sub.setUnsubscribedAt(Instant.now());
            subscriptionRepository.save(sub);
        });
    }

    public boolean isSubscribed(String subscriberId, String authorId) {
        return subscriptionRepository.existsBySubscriberIdAndAuthorIdAndActiveTrue(subscriberId, authorId);
    }

    public List<ThoughtSubscription> listMine(String subscriberId) {
        return subscriptionRepository.findBySubscriberIdAndActiveTrueOrderBySubscribedAtDesc(subscriberId);
    }

    public List<ThoughtSubscription> listSubscribersOf(String authorId) {
        return subscriptionRepository.findByAuthorIdAndActiveTrue(authorId);
    }

    public long countSubscribers(String authorId) {
        return subscriptionRepository.countByAuthorIdAndActiveTrue(authorId);
    }
}
