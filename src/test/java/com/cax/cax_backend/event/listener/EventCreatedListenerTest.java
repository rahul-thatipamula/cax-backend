package com.cax.cax_backend.event.listener;

import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.organization.model.OrganizationMember;
import com.cax.cax_backend.organization.repository.OrganizationMemberRepository;
import com.cax.cax_backend.organization.service.OrganizationService;
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
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private EventCreatedListener listener;

    @Test
    void handleEventCreatedEvent_SendsNotificationsToClubMembers_TemporarilyDisabled() {
        // Arrange
        String organizationId = "org123";
        String creatorId = "creator456";
        String memberId = "member789";

        Event event = Event.builder()
                .id("event123")
                .name("Hackathon")
                .organizationId(organizationId)
                .createdByUserId(creatorId)
                .build();

        Organization organization = Organization.builder()
                .id(organizationId)
                .name("Tech Organization")
                .build();

        OrganizationMember creatorMember = OrganizationMember.builder()
                .userId(creatorId)
                .build();

        OrganizationMember regularMember = OrganizationMember.builder()
                .userId(memberId)
                .build();

        EventCreatedEvent createdEvent = new EventCreatedEvent(this, event);

        when(organizationService.getOrganizationById(organizationId)).thenReturn(organization);
        when(organizationMemberRepository.findByOrganizationId(organizationId)).thenReturn(Arrays.asList(creatorMember, regularMember));

        // Act
        listener.handleEventCreatedEvent(createdEvent);

        // Assert
        // Verify that no notification was sent because sending notifications is temporarily disabled
        verify(notificationService, never()).createNotification(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }
}
