package com.cax.cax_backend.user.listener;

import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.user.event.UserSignupEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSignupListener {

    private final EmailService emailService;

    @Async("taskExecutor")
    @EventListener
    public void handleUserSignupEvent(UserSignupEvent event) {
        log.info("Received UserSignupEvent for user: {}", event.getUser().getUserId());
        try {
            emailService.sendGreetingEmail(event.getUser());
        } catch (Exception e) {
            log.error("Error sending greeting email in event listener: ", e);
        }
    }
}
