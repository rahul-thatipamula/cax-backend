package com.cax.cax_backend.event.listener;

import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.event.event.EventRegistrationReviewedEvent;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventParticipant;
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
public class EventRegistrationReviewedListener {

    private final EmailService emailService;
    private final NotificationService notificationService;

    @Async("taskExecutor")
    @EventListener
    public void handleEventRegistrationReviewedEvent(EventRegistrationReviewedEvent event) {
        EventParticipant participant = event.getParticipant();
        Event eventDetails = event.getEvent();
        
        log.info("Received EventRegistrationReviewedEvent for participant: {} with status: {}", 
                participant.getId(), participant.getStatus());

        try {
            emailService.sendEventRegistrationStatusEmail(participant, eventDetails);
        } catch (Exception e) {
            log.error("Error sending event registration status update email in listener: ", e);
        }

        try {
            boolean isApproved = "VERIFIED".equals(participant.getStatus());
            String title = isApproved ? "Registration Approved" : "Registration Declined";
            String body = isApproved
                    ? "Your registration for the event '" + eventDetails.getName() + "' has been approved."
                    : "Your registration request for the event '" + eventDetails.getName() + "' was declined by the organizer.";

            Map<String, String> data = new HashMap<>();
            data.put("type", "event_registration");
            data.put("status", participant.getStatus());
            data.put("eventId", eventDetails.getId());
            data.put("participantId", participant.getId());

            notificationService.createNotification(
                    participant.getUserId(),
                    title,
                    body,
                    NotificationType.EVENT,
                    data
            );
        } catch (Exception e) {
            log.error("Error creating event registration status notification in listener: ", e);
        }
    }
}
