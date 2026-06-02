package com.cax.cax_backend.user.event;

import com.cax.cax_backend.user.model.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserSignupEvent extends ApplicationEvent {
    private final User user;

    public UserSignupEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
