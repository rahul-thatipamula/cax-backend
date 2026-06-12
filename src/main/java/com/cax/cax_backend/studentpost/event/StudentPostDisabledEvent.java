package com.cax.cax_backend.studentpost.event;

import com.cax.cax_backend.studentpost.model.StudentPost;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class StudentPostDisabledEvent extends ApplicationEvent {
    private final StudentPost post;

    public StudentPostDisabledEvent(Object source, StudentPost post) {
        super(source);
        this.post = post;
    }
}
