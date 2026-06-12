package com.cax.cax_backend.user.event;

import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.common.enums.UserRole;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserRoleChangedEvent extends ApplicationEvent {
    private final User user;
    private final UserRole previousRole;
    private final UserRole newRole;

    public UserRoleChangedEvent(Object source, User user, UserRole previousRole, UserRole newRole) {
        super(source);
        this.user = user;
        this.previousRole = previousRole;
        this.newRole = newRole;
    }
}
