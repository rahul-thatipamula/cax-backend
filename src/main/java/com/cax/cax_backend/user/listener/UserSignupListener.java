package com.cax.cax_backend.user.listener;

import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.user.event.UserSignupEvent;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSignupListener {

    private final EmailService emailService;
    private final NotificationService notificationService;

    @Async("taskExecutor")
    @EventListener
    public void handleUserSignupEvent(UserSignupEvent event) {
        log.info("Received UserSignupEvent for user: {}", event.getUser().getUserId());
        try {
            emailService.sendGreetingEmail(event.getUser());
        } catch (Exception e) {
            log.error("Error sending greeting email in event listener: ", e);
        }

        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "welcome");
            notificationService.createNotification(
                event.getUser().getUserId(),
                "Welcome to CAX!",
                "Welcome to the CAX app, " + event.getUser().getName() + "! Discover campus clubs, events, and buy or sell items easily.",
                NotificationType.SYSTEM,
                data
            );
        } catch (Exception e) {
            log.error("Error creating welcome notification in event listener: ", e);
        }
    }
}
