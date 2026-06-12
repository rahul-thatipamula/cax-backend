package com.cax.cax_backend.studentpost.event;

import com.cax.cax_backend.studentpost.model.StudentPost;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class StudentPostLikedEvent extends ApplicationEvent {
    private final StudentPost post;
    private final String likedByUserId;

    public StudentPostLikedEvent(Object source, StudentPost post, String likedByUserId) {
        super(source);
        this.post = post;
        this.likedByUserId = likedByUserId;
    }
}
