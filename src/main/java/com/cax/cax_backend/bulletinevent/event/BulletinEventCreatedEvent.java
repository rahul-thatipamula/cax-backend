package com.cax.cax_backend.bulletinevent.event;

import com.cax.cax_backend.bulletinevent.model.BulletinEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class BulletinEventCreatedEvent extends ApplicationEvent {

    private final BulletinEvent bulletinEvent;

    public BulletinEventCreatedEvent(Object source, BulletinEvent bulletinEvent) {
        super(source);
        this.bulletinEvent = bulletinEvent;
    }
}
