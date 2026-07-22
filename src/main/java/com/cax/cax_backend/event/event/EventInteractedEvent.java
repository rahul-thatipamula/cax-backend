package com.cax.cax_backend.event.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Custom Spring ApplicationEvent published internally whenever a standard Event is interacted with.
 */
@Getter
public class EventInteractedEvent extends ApplicationEvent {

    public enum InteractionType {
        IMPRESSION,
        VIEW,
        JOIN,
        SHARE,
        CLICK
    }

    private final String eventId;
    private final InteractionType interactionType;
    private final String collegeId;
    private final String source;

    public EventInteractedEvent(Object sourceObj, String eventId, InteractionType interactionType, String collegeId, String source) {
        super(sourceObj);
        this.eventId = eventId;
        this.interactionType = interactionType;
        this.collegeId = collegeId;
        this.source = source != null ? source : "INTERNAL";
    }
}
