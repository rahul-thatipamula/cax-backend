package com.cax.cax_backend.bulletinevent.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Custom Spring ApplicationEvent published internally whenever a BulletinEvent is interacted with.
 */
@Getter
public class BulletinEventInteractedEvent extends ApplicationEvent {

    public enum InteractionType {
        IMPRESSION_BATCH,
        VIEW,
        CLICK
    }

    private final String bulletinEventId;
    private final List<String> bulletinEventIds;
    private final InteractionType interactionType;
    private final String source;

    public BulletinEventInteractedEvent(Object sourceObj, String bulletinEventId, InteractionType interactionType, String source) {
        super(sourceObj);
        this.bulletinEventId = bulletinEventId;
        this.bulletinEventIds = null;
        this.interactionType = interactionType;
        this.source = source != null ? source : "INTERNAL";
    }

    public BulletinEventInteractedEvent(Object sourceObj, List<String> bulletinEventIds, InteractionType interactionType, String source) {
        super(sourceObj);
        this.bulletinEventId = null;
        this.bulletinEventIds = bulletinEventIds;
        this.interactionType = interactionType;
        this.source = source != null ? source : "INTERNAL";
    }
}
