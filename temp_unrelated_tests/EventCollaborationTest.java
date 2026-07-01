package com.cax.cax_backend.event.service;

import com.cax.cax_backend.college.repository.CollegeRepository;
import com.cax.cax_backend.common.enums.UserRole;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventCollaborator;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.repository.EventMemoryRepository;
import com.cax.cax_backend.event.repository.EventParticipantRepository;
import com.cax.cax_backend.event.repository.EventRepository;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.organization.service.OrganizationService;
import com.cax.cax_backend.user.model.CollegeDetails;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import com.cax.cax_backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the event collaboration feature.
 *
 * Security model:
 *  - Only the primary org leader can invite / remove collaborators.
 *  - Only the invited org's leader can accept / decline the invitation.
 *  - Collaborating org leaders gain manage access (verify, check-in) but cannot
 *    edit event settings or cancel it.
 *  - An org cannot be invited twice (duplicate guard).
 *  - The primary org cannot invite itself.
 *  - Participant verifier/checker identity is captured on every action.
 */
@ExtendWith(MockitoExtension.class)
class EventCollaborationTest {

    @Mock EventRepository eventRepository;
    @Mock EventParticipantRepository eventParticipantRepository;
    @Mock EventMemoryRepository eventMemoryRepository;
    @Mock OrganizationService organizationService;
    @Mock UserService userService;
    @Mock CollegeRepository collegeRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock NotificationService notificationService;
    @Mock UserRepository userRepository;
    @Mock Executor taskExecutor;
    @Mock com.cax.cax_backend.settings.service.SystemSettingService systemSettingService;

    @InjectMocks EventService eventService;

    // ── shared fixture IDs ────────────────────────────────────────────────────
    private static final String PRIMARY_ORG_ID    = "primary-org";
    private static final String PRIMARY_ORG_NAME  = "Tech Club";
    private static final String COLLAB_ORG_ID     = "collab-org";
    private static final String COLLAB_ORG_NAME   = "Design Club";
    private static final String COLLEGE_A_ID      = "college-a";
    private static final String COLLEGE_B_ID      = "college-b";
    private static final String EVENT_ID          = "evt-001";
    private static final String PRIMARY_LEADER_ID = "leader-primary";
    private static final String COLLAB_LEADER_ID  = "leader-collab";
    private static final String STUDENT_ID        = "student-001";
    private static final String PARTICIPANT_ID    = "part-001";

    private Organization primaryOrg;
    private Organization collabOrg;
    private Event activeEvent;
    private User primaryLeader;
    private User collabLeader;
    private User regularStudent;

    @BeforeEach
    void setUp() {
        primaryOrg = Organization.builder()
                .id(PRIMARY_ORG_ID)
                .name(PRIMARY_ORG_NAME)
                .logo("https://r2.example.com/primary.png")
                .collegeId(COLLEGE_A_ID)
                .build();

        collabOrg = Organization.builder()
                .id(COLLAB_ORG_ID)
                .name(COLLAB_ORG_NAME)
                .logo("https://r2.example.com/collab.png")
                .collegeId(COLLEGE_A_ID)
                .build();

        activeEvent = Event.builder()
                .id(EVENT_ID)
                .organizationId(PRIMARY_ORG_ID)
                .createdByUserId(PRIMARY_LEADER_ID)
                .name("Hackathon 2026")
                .description("Annual hackathon open to all colleges")
                .collegeId(COLLEGE_A_ID)
                .status("ACTIVE")
                .global(false)
                .registrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .eventStartDate(Instant.now().plus(8, ChronoUnit.DAYS))
                .eventEndDate(Instant.now().plus(9, ChronoUnit.DAYS))
                .collaborators(new ArrayList<>())
                .build();

        primaryLeader = buildUser(PRIMARY_LEADER_ID, UserRole.STUDENT, COLLEGE_A_ID);
        collabLeader  = buildUser(COLLAB_LEADER_ID,  UserRole.STUDENT, COLLEGE_A_ID);
        regularStudent = buildUser(STUDENT_ID,       UserRole.STUDENT, COLLEGE_A_ID);
    }

    // =========================================================================
    // ADD COLLABORATOR
    // =========================================================================

    @Nested
    class AddCollaborator {

        @Test
        void primaryOrgLeaderCanInviteAnotherOrg() {
            givenEventExists();
            givenPrimaryOrgLeader();
            when(organizationService.getOrganizationById(COLLAB_ORG_ID)).thenReturn(collabOrg);
            when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Event result = eventService.addCollaborator(PRIMARY_LEADER_ID, EVENT_ID, COLLAB_ORG_ID);

            assertThat(result.getCollaborators()).hasSize(1);
            EventCollaborator added = result.getCollaborators().get(0);
            assertThat(added.getOrganizationId()).isEqualTo(COLLAB_ORG_ID);
            assertThat(added.getOrganizationName()).isEqualTo(COLLAB_ORG_NAME);
            assertThat(added.getStatus()).isEqualTo("PENDING");
            assertThat(added.getAddedByUserId()).isEqualTo(PRIMARY_LEADER_ID);
        }

        @Test
        void nonLeaderCannotAddCollaborator() {
            givenEventExists();
            when(organizationService.isOrganizationLeaderOrManager(STUDENT_ID, PRIMARY_ORG_ID)).thenReturn(false);
            when(userService.getUserByUserId(STUDENT_ID)).thenReturn(regularStudent);

            assertThatThrownBy(() -> eventService.addCollaborator(STUDENT_ID, EVENT_ID, COLLAB_ORG_ID))
                    .isInstanceOf(BusinessException.BadRequestException.class)
                    .hasMessageContaining("Only the primary organization leader");
        }

        @Test
        void cannotInvitePrimaryOrgAsCollaborator() {
            givenEventExists();
            givenPrimaryOrgLeader();

            assertThatThrownBy(() -> eventService.addCollaborator(PRIMARY_LEADER_ID, EVENT_ID, PRIMARY_ORG_ID))
                    .isInstanceOf(BusinessException.BadRequestException.class)
                    .hasMessageContaining("cannot collaborate with itself");
        }

        @Test
        void cannotInviteSameOrgTwice() {
            activeEvent.getCollaborators().add(EventCollaborator.builder()
                    .organizationId(COLLAB_ORG_ID)
                    .status("PENDING")
                    .build());
            givenEventExists();
            givenPrimaryOrgLeader();
            when(organizationService.getOrganizationById(COLLAB_ORG_ID)).thenReturn(collabOrg);

            assertThatThrownBy(() -> eventService.addCollaborator(PRIMARY_LEADER_ID, EVENT_ID, COLLAB_ORG_ID))
                    .isInstanceOf(BusinessException.BadRequestException.class)
                    .hasMessageContaining("already");
        }

        @Test
        void manyCollaboratorsAreAllowed() {
            // Events like college fests can have many collaborating orgs — no hard cap.
            for (int i = 0; i < 20; i++) {
                activeEvent.getCollaborators().add(EventCollaborator.builder()
                        .organizationId("org-" + i).status("ACCEPTED").build());
            }
            givenEventExists();
            givenPrimaryOrgLeader();
            when(organizationService.getOrganizationById(COLLAB_ORG_ID)).thenReturn(collabOrg);
            when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Event result = eventService.addCollaborator(PRIMARY_LEADER_ID, EVENT_ID, COLLAB_ORG_ID);

            assertThat(result.getCollaborators()).hasSize(21);
        }
    }

    // =========================================================================
    // RESPOND TO COLLABORATION INVITE
    // =========================================================================

    @Nested
    class RespondToCollaboration {

        @BeforeEach
        void addPendingCollaborator() {
            activeEvent.getCollaborators().add(EventCollaborator.builder()
                    .organizationId(COLLAB_ORG_ID)
                    .organizationName(COLLAB_ORG_NAME)
                    .status("PENDING")
                    .build());
        }

        @Test
        void collabOrgLeaderCanAccept() {
            givenEventExists();
            when(organizationService.isOrganizationLeaderOrManager(COLLAB_LEADER_ID, COLLAB_ORG_ID)).thenReturn(true);
            when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Event result = eventService.respondToCollaboration(COLLAB_LEADER_ID, EVENT_ID, COLLAB_ORG_ID, true);

            EventCollaborator updated = result.getCollaborators().stream()
                    .filter(c -> c.getOrganizationId().equals(COLLAB_ORG_ID))
                    .findFirst().orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("ACCEPTED");
        }

        @Test
        void collabOrgLeaderCanDecline() {
            givenEventExists();
            when(organizationService.isOrganizationLeaderOrManager(COLLAB_LEADER_ID, COLLAB_ORG_ID)).thenReturn(true);
            when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Event result = eventService.respondToCollaboration(COLLAB_LEADER_ID, EVENT_ID, COLLAB_ORG_ID, false);

            EventCollaborator updated = result.getCollaborators().stream()
                    .filter(c -> c.getOrganizationId().equals(COLLAB_ORG_ID))
                    .findFirst().orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("DECLINED");
        }

        @Test
        void nonCollabLeaderCannotRespond() {
            givenEventExists();
            when(organizationService.isOrganizationLeaderOrManager(STUDENT_ID, COLLAB_ORG_ID)).thenReturn(false);

            assertThatThrownBy(() -> eventService.respondToCollaboration(STUDENT_ID, EVENT_ID, COLLAB_ORG_ID, true))
                    .isInstanceOf(BusinessException.BadRequestException.class)
                    .hasMessageContaining("Only the leader");
        }

        @Test
        void cannotRespondToInviteNotInCollaborators() {
            givenEventExists();
            when(organizationService.isOrganizationLeaderOrManager(COLLAB_LEADER_ID, "unknown-org")).thenReturn(true);

            assertThatThrownBy(() -> eventService.respondToCollaboration(COLLAB_LEADER_ID, EVENT_ID, "unknown-org", true))
                    .isInstanceOf(BusinessException.ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // REMOVE COLLABORATOR
    // =========================================================================

    @Nested
    class RemoveCollaborator {

        @BeforeEach
        void addAcceptedCollaborator() {
            activeEvent.getCollaborators().add(EventCollaborator.builder()
                    .organizationId(COLLAB_ORG_ID)
                    .organizationName(COLLAB_ORG_NAME)
                    .status("ACCEPTED")
                    .build());
        }

        @Test
        void primaryLeaderCanRemoveCollaborator() {
            givenEventExists();
            givenPrimaryOrgLeader();
            when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Event result = eventService.removeCollaborator(PRIMARY_LEADER_ID, EVENT_ID, COLLAB_ORG_ID);

            assertThat(result.getCollaborators()).isEmpty();
        }

        @Test
        void nonLeaderCannotRemoveCollaborator() {
            givenEventExists();
            when(organizationService.isOrganizationLeaderOrManager(STUDENT_ID, PRIMARY_ORG_ID)).thenReturn(false);
            when(userService.getUserByUserId(STUDENT_ID)).thenReturn(regularStudent);

            assertThatThrownBy(() -> eventService.removeCollaborator(STUDENT_ID, EVENT_ID, COLLAB_ORG_ID))
                    .isInstanceOf(BusinessException.BadRequestException.class)
                    .hasMessageContaining("Only the primary organization leader");
        }
    }

    // =========================================================================
    // COLLABORATION ACCESS — verifyEventManager
    // =========================================================================

    @Nested
    class CollaboratorManageAccess {

        @BeforeEach
        void addAcceptedCollaborator() {
            activeEvent.getCollaborators().add(EventCollaborator.builder()
                    .organizationId(COLLAB_ORG_ID)
                    .status("ACCEPTED")
                    .build());
        }

        @Test
        void acceptedCollabOrgLeaderCanVerifyParticipant() {
            givenEventExists();
            when(organizationService.isOrganizationLeaderOrManager(COLLAB_LEADER_ID, PRIMARY_ORG_ID)).thenReturn(false);
            when(organizationService.isOrganizationLeaderOrManager(COLLAB_LEADER_ID, COLLAB_ORG_ID)).thenReturn(true);

            EventParticipant participant = buildParticipant("PAYMENT_SUBMITTED");
            when(eventParticipantRepository.findById(PARTICIPANT_ID)).thenReturn(Optional.of(participant));
            when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userService.getUserByUserId(COLLAB_LEADER_ID)).thenReturn(collabLeader);
            when(organizationService.getOrganizationById(PRIMARY_ORG_ID)).thenReturn(primaryOrg);

            EventParticipant result = eventService.verifyPayment(COLLAB_LEADER_ID, EVENT_ID, PARTICIPANT_ID, true);

            assertThat(result.getStatus()).isEqualTo("VERIFIED");
            assertThat(result.getVerifiedByUserId()).isEqualTo(COLLAB_LEADER_ID);
            assertThat(result.getVerifiedByOrganizationId()).isEqualTo(COLLAB_ORG_ID);
            assertThat(result.getVerifiedByName()).isEqualTo(collabLeader.getName());
        }

        @Test
        void declinedCollabOrgLeaderCannotManageEvent() {
            activeEvent.getCollaborators().clear();
            activeEvent.getCollaborators().add(EventCollaborator.builder()
                    .organizationId(COLLAB_ORG_ID)
                    .status("DECLINED")
                    .build());
            givenEventExists();
            when(organizationService.isOrganizationLeaderOrManager(COLLAB_LEADER_ID, PRIMARY_ORG_ID)).thenReturn(false);
            when(userService.getUserByUserId(COLLAB_LEADER_ID)).thenReturn(collabLeader);

            assertThatThrownBy(() -> eventService.verifyEventManager(COLLAB_LEADER_ID, activeEvent))
                    .isInstanceOf(BusinessException.BadRequestException.class);
        }

        @Test
        void pendingCollabOrgLeaderCannotManageEvent() {
            activeEvent.getCollaborators().clear();
            activeEvent.getCollaborators().add(EventCollaborator.builder()
                    .organizationId(COLLAB_ORG_ID)
                    .status("PENDING")
                    .build());
            givenEventExists();
            when(organizationService.isOrganizationLeaderOrManager(COLLAB_LEADER_ID, PRIMARY_ORG_ID)).thenReturn(false);
            when(userService.getUserByUserId(COLLAB_LEADER_ID)).thenReturn(collabLeader);

            assertThatThrownBy(() -> eventService.verifyEventManager(COLLAB_LEADER_ID, activeEvent))
                    .isInstanceOf(BusinessException.BadRequestException.class);
        }
    }

    // =========================================================================
    // CHECK-IN — verifier identity captured
    // =========================================================================

    @Nested
    class CheckInIdentityCapture {

        @BeforeEach
        void addAcceptedCollaborator() {
            activeEvent.getCollaborators().add(EventCollaborator.builder()
                    .organizationId(COLLAB_ORG_ID)
                    .status("ACCEPTED")
                    .build());
        }

        @Test
        void checkInCapturesCheckerIdentityFromCollabOrg() {
            givenEventExists();
            when(organizationService.isOrganizationLeaderOrManager(COLLAB_LEADER_ID, PRIMARY_ORG_ID)).thenReturn(false);
            when(organizationService.isOrganizationLeaderOrManager(COLLAB_LEADER_ID, COLLAB_ORG_ID)).thenReturn(true);
            when(userService.getUserByUserId(COLLAB_LEADER_ID)).thenReturn(collabLeader);

            EventParticipant participant = buildParticipant("VERIFIED");
            participant.setTicketCode("CAX-TESTCODE");
            when(eventParticipantRepository.findByEventIdAndTicketCode(EVENT_ID, "CAX-TESTCODE"))
                    .thenReturn(Optional.of(participant));
            when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(organizationService.getOrganizationById(PRIMARY_ORG_ID)).thenReturn(primaryOrg);

            EventParticipant result = eventService.checkInParticipant(COLLAB_LEADER_ID, EVENT_ID, "CAX-TESTCODE");

            assertThat(result.isCheckedIn()).isTrue();
            assertThat(result.getCheckedInByUserId()).isEqualTo(COLLAB_LEADER_ID);
            assertThat(result.getCheckedInByOrganizationId()).isEqualTo(COLLAB_ORG_ID);
            assertThat(result.getCheckedInByName()).isEqualTo(collabLeader.getName());
        }

        @Test
        void checkInCapturesPrimaryOrgCheckerIdentity() {
            givenEventExists();
            when(organizationService.isOrganizationLeaderOrManager(PRIMARY_LEADER_ID, PRIMARY_ORG_ID)).thenReturn(true);
            when(userService.getUserByUserId(PRIMARY_LEADER_ID)).thenReturn(primaryLeader);

            EventParticipant participant = buildParticipant("VERIFIED");
            participant.setTicketCode("CAX-TESTCODE");
            when(eventParticipantRepository.findByEventIdAndTicketCode(EVENT_ID, "CAX-TESTCODE"))
                    .thenReturn(Optional.of(participant));
            when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(organizationService.getOrganizationById(PRIMARY_ORG_ID)).thenReturn(primaryOrg);

            EventParticipant result = eventService.checkInParticipant(PRIMARY_LEADER_ID, EVENT_ID, "CAX-TESTCODE");

            assertThat(result.getCheckedInByUserId()).isEqualTo(PRIMARY_LEADER_ID);
            assertThat(result.getCheckedInByOrganizationId()).isEqualTo(PRIMARY_ORG_ID);
        }
    }

    // =========================================================================
    // DUPLICATE PREVENTION — getOrganizationEvents
    // =========================================================================

    @Nested
    class NoDuplicateEventsInOrgProfile {

        @Test
        void collaborativeEventAppearsOnceWhenOrgIsPrimaryAndCollaborator() {
            // Pathological case: same org as both primary and collaborator (should not
            // happen due to the "cannot invite itself" guard, but test the dedup logic).
            Event evt1 = activeEvent;
            when(eventRepository.findByOrganizationId(PRIMARY_ORG_ID)).thenReturn(List.of(evt1));
            when(eventRepository.findByCollaboratingOrganizationIdAndStatus(PRIMARY_ORG_ID, "ACCEPTED"))
                    .thenReturn(List.of(evt1));

            User user = buildUser(PRIMARY_LEADER_ID, UserRole.STUDENT, COLLEGE_A_ID);
            when(userService.getUserByUserId(PRIMARY_LEADER_ID)).thenReturn(user);
            when(organizationService.getOrganizationById(PRIMARY_ORG_ID)).thenReturn(primaryOrg);

            List<Event> events = eventService.getOrganizationEvents(PRIMARY_LEADER_ID, PRIMARY_ORG_ID);

            assertThat(events).hasSize(1);
        }

        @Test
        void acceptedCollaborativeEventAppearsOnCollabOrgProfile() {
            activeEvent.getCollaborators().add(EventCollaborator.builder()
                    .organizationId(COLLAB_ORG_ID)
                    .status("ACCEPTED")
                    .build());
            when(eventRepository.findByOrganizationId(COLLAB_ORG_ID)).thenReturn(List.of());
            when(eventRepository.findByCollaboratingOrganizationIdAndStatus(COLLAB_ORG_ID, "ACCEPTED"))
                    .thenReturn(List.of(activeEvent));

            User user = buildUser(COLLAB_LEADER_ID, UserRole.STUDENT, COLLEGE_A_ID);
            when(userService.getUserByUserId(COLLAB_LEADER_ID)).thenReturn(user);
            when(organizationService.getOrganizationById(COLLAB_ORG_ID)).thenReturn(collabOrg);

            List<Event> events = eventService.getOrganizationEvents(COLLAB_LEADER_ID, COLLAB_ORG_ID);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).getId()).isEqualTo(EVENT_ID);
        }

        @Test
        void declinedCollaborativeEventDoesNotAppearOnCollabOrgProfile() {
            activeEvent.getCollaborators().add(EventCollaborator.builder()
                    .organizationId(COLLAB_ORG_ID)
                    .status("DECLINED")
                    .build());
            when(eventRepository.findByOrganizationId(COLLAB_ORG_ID)).thenReturn(List.of());
            when(eventRepository.findByCollaboratingOrganizationIdAndStatus(COLLAB_ORG_ID, "ACCEPTED"))
                    .thenReturn(List.of());

            User user = buildUser(COLLAB_LEADER_ID, UserRole.STUDENT, COLLEGE_A_ID);
            when(userService.getUserByUserId(COLLAB_LEADER_ID)).thenReturn(user);
            when(organizationService.getOrganizationById(COLLAB_ORG_ID)).thenReturn(collabOrg);

            List<Event> events = eventService.getOrganizationEvents(COLLAB_LEADER_ID, COLLAB_ORG_ID);

            assertThat(events).isEmpty();
        }
    }

    // =========================================================================
    // SECURITY — only primary org can change settings, cancel, etc.
    // =========================================================================

    @Nested
    class CollaboratorSettingsRestriction {

        @BeforeEach
        void addAcceptedCollaborator() {
            activeEvent.getCollaborators().add(EventCollaborator.builder()
                    .organizationId(COLLAB_ORG_ID)
                    .status("ACCEPTED")
                    .build());
        }

        @Test
        void collaboratorLeaderCannotCancelEvent() {
            givenEventExists();
            // Collab leader is a manager of collab org, not primary org
            when(organizationService.isOrganizationLeaderOrManager(COLLAB_LEADER_ID, PRIMARY_ORG_ID)).thenReturn(false);
            when(userService.getUserByUserId(COLLAB_LEADER_ID)).thenReturn(collabLeader);

            assertThatThrownBy(() -> eventService.cancelEvent(COLLAB_LEADER_ID, EVENT_ID))
                    .isInstanceOf(BusinessException.BadRequestException.class);
        }

        @Test
        void collaboratorLeaderCannotUpdateEventDetails() {
            givenEventExists();
            when(organizationService.isOrganizationLeaderOrManager(COLLAB_LEADER_ID, PRIMARY_ORG_ID)).thenReturn(false);
            when(userService.getUserByUserId(COLLAB_LEADER_ID)).thenReturn(collabLeader);

            Event updated = Event.builder()
                    .name("New Name")
                    .description("New description for update")
                    .registrationEndDate(Instant.now().plus(5, ChronoUnit.DAYS))
                    .eventStartDate(Instant.now().plus(6, ChronoUnit.DAYS))
                    .eventEndDate(Instant.now().plus(7, ChronoUnit.DAYS))
                    .build();

            assertThatThrownBy(() -> eventService.updateEvent(COLLAB_LEADER_ID, EVENT_ID, updated))
                    .isInstanceOf(BusinessException.BadRequestException.class);
        }
    }

    // =========================================================================
    // GLOBAL EVENT — org profile navigation access
    // =========================================================================

    @Nested
    class GlobalEventOrgAccess {

        @Test
        void globalEventCollaboratorsAreIncludedInDetailResponse() {
            activeEvent = Event.builder()
                    .id(EVENT_ID)
                    .organizationId(PRIMARY_ORG_ID)
                    .createdByUserId(PRIMARY_LEADER_ID)
                    .name("Global Hackathon")
                    .description("Cross-college hackathon open to everyone")
                    .collegeId(COLLEGE_A_ID)
                    .collegeName("Alpha University")
                    .status("ACTIVE")
                    .global(true)
                    .registrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS))
                    .eventStartDate(Instant.now().plus(8, ChronoUnit.DAYS))
                    .eventEndDate(Instant.now().plus(9, ChronoUnit.DAYS))
                    .collaborators(new ArrayList<>(List.of(
                            EventCollaborator.builder()
                                    .organizationId(COLLAB_ORG_ID)
                                    .organizationName(COLLAB_ORG_NAME)
                                    .organizationLogo("https://r2.example.com/collab.png")
                                    .collegeId(COLLEGE_B_ID)
                                    .status("ACCEPTED")
                                    .build()
                    )))
                    .build();

            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(activeEvent));
            when(organizationService.getOrganizationById(PRIMARY_ORG_ID)).thenReturn(primaryOrg);
            // Student from College B can view global event
            User outsideStudent = buildUser("outside-student", UserRole.STUDENT, COLLEGE_B_ID);
            when(userService.getUserByUserId("outside-student")).thenReturn(outsideStudent);
            when(eventParticipantRepository.findByEventIdAndUserId(EVENT_ID, "outside-student"))
                    .thenReturn(Optional.empty());

            var detail = eventService.getEventDetailForUser("outside-student", EVENT_ID);

            @SuppressWarnings("unchecked")
            Event eventInResponse = (Event) detail.get("event");
            assertThat(eventInResponse.getCollaborators()).hasSize(1);
            assertThat(eventInResponse.getCollaborators().get(0).getOrganizationId()).isEqualTo(COLLAB_ORG_ID);
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void givenEventExists() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(activeEvent));
        when(organizationService.getOrganizationById(PRIMARY_ORG_ID)).thenReturn(primaryOrg);
    }

    private void givenPrimaryOrgLeader() {
        when(organizationService.isOrganizationLeaderOrManager(PRIMARY_LEADER_ID, PRIMARY_ORG_ID)).thenReturn(true);
    }

    private User buildUser(String userId, UserRole role, String collegeId) {
        CollegeDetails cd = new CollegeDetails();
        cd.setCollegeId(collegeId);
        cd.setCollegeName(collegeId.equals(COLLEGE_A_ID) ? "Alpha University" : "Beta College");
        User u = new User();
        u.setUserId(userId);
        u.setName("User " + userId);
        u.setEmail(userId + "@test.com");
        u.setRole(role);
        u.setCollegeDetails(cd);
        return u;
    }

    private EventParticipant buildParticipant(String status) {
        return EventParticipant.builder()
                .id(PARTICIPANT_ID)
                .eventId(EVENT_ID)
                .userId(STUDENT_ID)
                .name("Test Student")
                .email("student@test.com")
                .college("Alpha University")
                .collegeId(COLLEGE_A_ID)
                .status(status)
                .registeredAt(Instant.now())
                .build();
    }
}
