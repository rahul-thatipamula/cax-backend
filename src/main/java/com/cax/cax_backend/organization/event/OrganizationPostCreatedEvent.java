package com.cax.cax_backend.organization.event;

import com.cax.cax_backend.organization.model.OrganizationPost;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrganizationPostCreatedEvent extends ApplicationEvent {
    private final OrganizationPost post;

    public OrganizationPostCreatedEvent(Object source, OrganizationPost post) {
        super(source);
        this.post = post;
    }
}
