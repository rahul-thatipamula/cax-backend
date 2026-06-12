package com.cax.cax_backend.event.event;

import com.cax.cax_backend.event.model.Event;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class EventCreatedEvent extends ApplicationEvent {
    private final Event event;

    public EventCreatedEvent(Object source, Event event) {
        super(source);
        this.event = event;
    }
}
