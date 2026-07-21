package com.cax.cax_backend.user.event;

import com.cax.cax_backend.user.model.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WaterReminderToggledEvent extends ApplicationEvent {
    private final User user;
    private final boolean subscribed;

    public WaterReminderToggledEvent(Object source, User user, boolean subscribed) {
        super(source);
        this.user = user;
        this.subscribed = subscribed;
    }
}
