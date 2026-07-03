package com.cax.cax_backend.thoughtsubscription.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ThoughtSubscribedEvent extends ApplicationEvent {
    private final String subscriberId;
    private final String subscriberName;
    private final String subscriberPicture;
    private final String authorId;

    public ThoughtSubscribedEvent(Object source, String subscriberId, String subscriberName,
                                   String subscriberPicture, String authorId) {
        super(source);
        this.subscriberId = subscriberId;
        this.subscriberName = subscriberName;
        this.subscriberPicture = subscriberPicture;
        this.authorId = authorId;
    }
}
