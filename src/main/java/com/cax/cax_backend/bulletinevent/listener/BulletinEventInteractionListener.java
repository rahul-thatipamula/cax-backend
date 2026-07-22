package com.cax.cax_backend.bulletinevent.listener;

import com.cax.cax_backend.bulletinevent.event.BulletinEventInteractedEvent;
import com.cax.cax_backend.bulletinevent.service.BulletinEventAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Asynchronous Spring Event Listener that processes BulletinEventInteractedEvent notifications
 * internally in the background without affecting API request latency or requiring frontend tracking calls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BulletinEventInteractionListener {

    private final BulletinEventAnalyticsService analyticsService;

    @Async("taskExecutor")
    @EventListener
    public void handleBulletinEventInteraction(BulletinEventInteractedEvent event) {
        if (event == null) return;

        try {
            switch (event.getInteractionType()) {
                case IMPRESSION_BATCH -> {
                    if (event.getBulletinEventIds() != null && !event.getBulletinEventIds().isEmpty()) {
                        analyticsService.recordImpressions(event.getBulletinEventIds(), event.getSource());
                    }
                }
                case VIEW -> {
                    if (event.getBulletinEventId() != null) {
                        analyticsService.recordDetailView(event.getBulletinEventId(), event.getSource());
                    }
                }
                case CLICK -> {
                    if (event.getBulletinEventId() != null) {
                        analyticsService.recordExternalLinkClick(event.getBulletinEventId(), event.getSource());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing internal BulletinEventInteractedEvent: {}", e.getMessage());
        }
    }
}
