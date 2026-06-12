package com.cax.cax_backend.event.listener;

import com.cax.cax_backend.club.model.Club;
import com.cax.cax_backend.club.model.ClubMember;
import com.cax.cax_backend.club.repository.ClubMemberRepository;
import com.cax.cax_backend.club.service.ClubService;
import com.cax.cax_backend.event.event.EventCreatedEvent;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventCreatedListenerTest {

    @Mock
    private ClubMemberRepository clubMemberRepository;

    @Mock
    private ClubService clubService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private EventCreatedListener listener;

    @Test
    void handleEventCreatedEvent_SendsNotificationsToClubMembers() {
        // Arrange
        String clubId = "club123";
        String creatorId = "creator456";
        String memberId = "member789";

        Event event = Event.builder()
                .id("event123")
                .name("Hackathon")
                .clubId(clubId)
                .createdByUserId(creatorId)
                .build();

        Club club = Club.builder()
                .id(clubId)
                .name("Tech Club")
                .build();

        ClubMember creatorMember = ClubMember.builder()
                .userId(creatorId)
                .build();

        ClubMember regularMember = ClubMember.builder()
                .userId(memberId)
                .build();

        EventCreatedEvent createdEvent = new EventCreatedEvent(this, event);

        when(clubService.getClubById(clubId)).thenReturn(club);
        when(clubMemberRepository.findByClubId(clubId)).thenReturn(Arrays.asList(creatorMember, regularMember));

        // Act
        listener.handleEventCreatedEvent(createdEvent);

        // Assert
        verify(notificationService, times(1)).createNotification(
                eq(memberId),
                eq("New Event in Tech Club!"),
                eq("Hackathon has been announced! Check it out."),
                eq(NotificationType.EVENT),
                any(Map.class)
        );
        // Verify creator did not get notified
        verify(notificationService, never()).createNotification(
                eq(creatorId),
                any(),
                any(),
                any(),
                any()
        );
    }
}
