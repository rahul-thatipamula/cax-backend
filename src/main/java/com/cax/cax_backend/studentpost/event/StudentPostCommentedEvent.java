package com.cax.cax_backend.studentpost.event;

import com.cax.cax_backend.studentpost.model.StudentPost;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class StudentPostCommentedEvent extends ApplicationEvent {
    private final StudentPost post;
    private final StudentPost.Comment comment;

    public StudentPostCommentedEvent(Object source, StudentPost post, StudentPost.Comment comment) {
        super(source);
        this.post = post;
        this.comment = comment;
    }
}
