package com.cax.cax_backend.idcard.listener;

import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.idcard.event.IDCardStatusChangedEvent;
import com.cax.cax_backend.common.enums.CarouselEnums.VerificationStatus;
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
public class IDCardStatusChangedListener {

    private final EmailService emailService;
    private final NotificationService notificationService;

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

        try {
            VerificationStatus status = event.getIdCard().getStatus();
            if (status == VerificationStatus.APPROVED || status == VerificationStatus.REJECTED) {
                boolean isApproved = status == VerificationStatus.APPROVED;
                String title = isApproved ? "ID Card Verified" : "ID Card Rejected";
                
                String body;
                if (isApproved) {
                    body = event.getIdCard().getVerificationNotes() != null && !event.getIdCard().getVerificationNotes().isBlank()
                        ? event.getIdCard().getVerificationNotes()
                        : "Your student ID card has been successfully verified! You now have full access.";
                } else {
                    body = "Your student ID card verification was rejected. Reason: " + 
                        (event.getIdCard().getRejectionReason() != null && !event.getIdCard().getRejectionReason().isBlank()
                            ? event.getIdCard().getRejectionReason()
                            : "Invalid details");
                }

                Map<String, String> data = new HashMap<>();
                data.put("type", "id_card_verification");
                data.put("status", isApproved ? "APPROVED" : "REJECTED");

                notificationService.createNotification(
                    event.getUser().getUserId(),
                    title,
                    body,
                    NotificationType.ID_CARD,
                    data
                );
            }
        } catch (Exception e) {
            log.error("Error creating ID card status changed notification in listener: ", e);
        }
    }
}
