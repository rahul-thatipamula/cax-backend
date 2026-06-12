package com.cax.cax_backend.event.listener;

import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.event.event.EventRegistrationReviewedEvent;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventRegistrationReviewedListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private EventRegistrationReviewedListener listener;

    @Test
    void handleEventRegistrationReviewedEvent_SendsEmailAndNotification() {
        // Arrange
        Event event = Event.builder()
                .id("event123")
                .name("Hackathon")
                .build();

        EventParticipant participant = EventParticipant.builder()
                .id("part123")
                .userId("user456")
                .email("student@cax.edu")
                .name("Alex")
                .status("VERIFIED")
                .ticketCode("CAX-TICKET")
                .build();

        EventRegistrationReviewedEvent reviewedEvent = new EventRegistrationReviewedEvent(this, participant, event);

        // Act
        listener.handleEventRegistrationReviewedEvent(reviewedEvent);

        // Assert
        verify(emailService, times(1)).sendEventRegistrationStatusEmail(participant, event);
        verify(notificationService, times(1)).createNotification(
                eq("user456"),
                eq("Registration Approved"),
                eq("Your registration for the event 'Hackathon' has been approved!"),
                eq(NotificationType.EVENT),
                any(Map.class)
        );
    }
}
