package com.cax.cax_backend.thought.event;

import com.cax.cax_backend.thought.model.Thought;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ThoughtDisabledEvent extends ApplicationEvent {
    private final Thought thought;

    public ThoughtDisabledEvent(Object source, Thought thought) {
        super(source);
        this.thought = thought;
    }
}
