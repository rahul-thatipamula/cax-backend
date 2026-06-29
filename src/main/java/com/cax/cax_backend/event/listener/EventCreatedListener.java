package com.cax.cax_backend.event.listener;

import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.organization.model.OrganizationMember;
import com.cax.cax_backend.organization.repository.OrganizationMemberRepository;
import com.cax.cax_backend.organization.service.OrganizationService;
import com.cax.cax_backend.event.event.EventCreatedEvent;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventCreatedListener {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationService organizationService;
    private final NotificationService notificationService;

    @Async("taskExecutor")
    @EventListener
    public void handleEventCreatedEvent(EventCreatedEvent event) {
        Event eventDetails = event.getEvent();
        log.info("Received EventCreatedEvent for event: {} in organization: {}", eventDetails.getId(), eventDetails.getOrganizationId());

        try {
            Organization organization = organizationService.getOrganizationById(eventDetails.getOrganizationId());
            List<OrganizationMember> members = organizationMemberRepository.findByOrganizationId(organization.getId());

            String title = "New Event in " + organization.getName() + "!";
            String body = eventDetails.getName() + " has been announced! Check it out.";

            Map<String, String> data = new HashMap<>();
            data.put("type", "EVENT_CREATED");
            data.put("eventId", eventDetails.getId());
            data.put("organizationId", organization.getId());

            // TEMPORARILY DISABLED: Temporarily stop sending notifications to organization members when an event is created.
            /*
            for (OrganizationMember member : members) {
                // Skip notifying the event creator themselves to avoid redundancy
                if (member.getUserId().equals(eventDetails.getCreatedByUserId())) {
                    continue;
                }

                try {
                    notificationService.createNotification(
                            member.getUserId(),
                            title,
                            body,
                            NotificationType.EVENT,
                            data
                    );
                } catch (Exception e) {
                    log.error("Failed to send event created notification to user: {}, error: {}", member.getUserId(), e.getMessage());
                }
            }
            */
            log.info("Sending event created notification to organization members is temporarily disabled.");
        } catch (Exception e) {
            log.error("Error creating event created notifications in listener: ", e);
        }
    }
}
