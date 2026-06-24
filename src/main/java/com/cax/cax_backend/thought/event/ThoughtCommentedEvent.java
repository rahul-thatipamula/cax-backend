package com.cax.cax_backend.thought.event;

import com.cax.cax_backend.thought.model.Thought;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ThoughtCommentedEvent extends ApplicationEvent {
    private final Thought thought;
    private final Thought.Comment comment;

    public ThoughtCommentedEvent(Object source, Thought thought, Thought.Comment comment) {
        super(source);
        this.thought = thought;
        this.comment = comment;
    }
}
