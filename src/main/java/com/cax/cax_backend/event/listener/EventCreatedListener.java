package com.cax.cax_backend.event.listener;

import com.cax.cax_backend.club.model.Club;
import com.cax.cax_backend.club.model.ClubMember;
import com.cax.cax_backend.club.repository.ClubMemberRepository;
import com.cax.cax_backend.club.service.ClubService;
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

    private final ClubMemberRepository clubMemberRepository;
    private final ClubService clubService;
    private final NotificationService notificationService;

    @Async("taskExecutor")
    @EventListener
    public void handleEventCreatedEvent(EventCreatedEvent event) {
        Event eventDetails = event.getEvent();
        log.info("Received EventCreatedEvent for event: {} in club: {}", eventDetails.getId(), eventDetails.getClubId());

        try {
            Club club = clubService.getClubById(eventDetails.getClubId());
            List<ClubMember> members = clubMemberRepository.findByClubId(club.getId());

            String title = "New Event in " + club.getName() + "!";
            String body = eventDetails.getName() + " has been announced! Check it out.";

            Map<String, String> data = new HashMap<>();
            data.put("type", "EVENT_CREATED");
            data.put("eventId", eventDetails.getId());
            data.put("clubId", club.getId());

            for (ClubMember member : members) {
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
        } catch (Exception e) {
            log.error("Error creating event created notifications in listener: ", e);
        }
    }
}
