package com.cax.cax_backend.newsletter.event;

import com.cax.cax_backend.newsletter.model.NewsletterSubscription;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NewsletterSubscribeEvent extends ApplicationEvent {
    private final NewsletterSubscription subscription;

    public NewsletterSubscribeEvent(Object source, NewsletterSubscription subscription) {
        super(source);
        this.subscription = subscription;
    }
}
