package com.cax.cax_backend.thought.event;

import com.cax.cax_backend.thought.model.Thought;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ThoughtLikedEvent extends ApplicationEvent {
    private final Thought thought;
    private final String likedByUserId;

    public ThoughtLikedEvent(Object source, Thought thought, String likedByUserId) {
        super(source);
        this.thought = thought;
        this.likedByUserId = likedByUserId;
    }
}
