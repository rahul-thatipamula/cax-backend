package com.cax.cax_backend.idcard.listener;

import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.idcard.event.IDCardStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IDCardStatusChangedListener {

    private final EmailService emailService;

    @Async("taskExecutor")
    @EventListener
    public void handleIDCardStatusChangedEvent(IDCardStatusChangedEvent event) {
        log.info("Received IDCardStatusChangedEvent for user: {} with status: {}", 
            event.getUser().getUserId(), event.getIdCard().getStatus());
        try {
            emailService.sendIdCardStatusEmail(event.getUser(), event.getIdCard());
        } catch (Exception e) {
            log.error("Error sending ID card status update email in listener: ", e);
        }
    }
}
