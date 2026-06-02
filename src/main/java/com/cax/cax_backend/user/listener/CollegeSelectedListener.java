package com.cax.cax_backend.user.listener;

import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.user.event.CollegeSelectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollegeSelectedListener {

    private final EmailService emailService;

    @Async("taskExecutor")
    @EventListener
    public void handleCollegeSelectedEvent(CollegeSelectedEvent event) {
        log.info("Received CollegeSelectedEvent for user: {}", event.getUser().getUserId());
        try {
            emailService.sendIdVerificationRequestEmail(event.getUser());
        } catch (Exception e) {
            log.error("Error sending ID verification email in listener: ", e);
        }
    }
}
