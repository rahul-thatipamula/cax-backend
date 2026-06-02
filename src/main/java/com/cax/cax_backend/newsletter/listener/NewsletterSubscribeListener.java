package com.cax.cax_backend.newsletter.listener;

import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.newsletter.event.NewsletterSubscribeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsletterSubscribeListener {

    private final EmailService emailService;

    @Async("taskExecutor")
    @EventListener
    public void handleNewsletterSubscribeEvent(NewsletterSubscribeEvent event) {
        log.info("Received NewsletterSubscribeEvent for email: {}", event.getSubscription().getEmail());
        try {
            emailService.sendNewsletterConfirmationEmail(event.getSubscription().getEmail());
        } catch (Exception e) {
            log.error("Error sending newsletter confirmation email in listener: ", e);
        }
    }
}
