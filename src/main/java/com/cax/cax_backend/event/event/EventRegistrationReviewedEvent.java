package com.cax.cax_backend.event.event;

import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventParticipant;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class EventRegistrationReviewedEvent extends ApplicationEvent {
    private final EventParticipant participant;
    private final Event event;

    public EventRegistrationReviewedEvent(Object source, EventParticipant participant, Event event) {
        super(source);
        this.participant = participant;
        this.event = event;
    }
}
