package com.cax.cax_backend.idcard.event;

import com.cax.cax_backend.idcard.model.IDCard;
import com.cax.cax_backend.user.model.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class IDCardStatusChangedEvent extends ApplicationEvent {
    private final User user;
    private final IDCard idCard;

    public IDCardStatusChangedEvent(Object source, User user, IDCard idCard) {
        super(source);
        this.user = user;
        this.idCard = idCard;
    }
}
