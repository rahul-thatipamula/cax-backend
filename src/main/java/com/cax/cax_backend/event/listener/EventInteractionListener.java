package com.cax.cax_backend.event.listener;

import com.cax.cax_backend.event.event.EventInteractedEvent;
import com.cax.cax_backend.event.service.EventAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Asynchronous Spring Event Listener that processes EventInteractedEvent notifications
 * internally in the background without affecting API request latency or requiring frontend tracking calls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventInteractionListener {

    private final EventAnalyticsService eventAnalyticsService;

    @Async("taskExecutor")
    @EventListener
    public void handleEventInteraction(EventInteractedEvent event) {
        if (event == null || event.getEventId() == null) return;

        try {
            switch (event.getInteractionType()) {
                case VIEW -> eventAnalyticsService.recordEventView(event.getEventId());
                case JOIN -> eventAnalyticsService.recordEventJoin(event.getEventId());
                case SHARE -> eventAnalyticsService.recordEventShare(event.getEventId());
                case CLICK -> eventAnalyticsService.recordEventClick(event.getEventId());
                case IMPRESSION -> eventAnalyticsService.recordEventView(event.getEventId());
            }
        } catch (Exception e) {
            log.error("Error processing internal EventInteractedEvent for event {}: {}", event.getEventId(), e.getMessage());
        }
    }
}
