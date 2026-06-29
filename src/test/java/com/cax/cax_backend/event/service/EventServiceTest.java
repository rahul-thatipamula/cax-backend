package com.cax.cax_backend.event.service;

import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.organization.service.OrganizationService;
import com.cax.cax_backend.college.repository.CollegeRepository;
import com.cax.cax_backend.common.enums.UserRole;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.event.event.EventCreatedEvent;
import com.cax.cax_backend.event.event.EventRegistrationReviewedEvent;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.repository.EventMemoryRepository;
import com.cax.cax_backend.event.repository.EventParticipantRepository;
import com.cax.cax_backend.event.repository.EventRepository;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.user.model.CollegeDetails;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import com.cax.cax_backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

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

    @InjectMocks
    EventService eventService;

    // ── shared fixtures ──────────────────────────────────────────────────────

    private static final String CLUB_ID    = "club-001";
    private static final String COLLEGE_ID = "college-001";
    private static final String USER_ID    = "user-001";
    private static final String EVENT_ID   = "event-001";

    private Organization defaultOrganization;
    private User defaultUser;

    @BeforeEach
    void setUp() {
        lenient().when(systemSettingService.isRazorpayEnabled()).thenReturn(true);

        defaultOrganization = Organization.builder()
                .id(CLUB_ID)
                .collegeId(COLLEGE_ID)
                .name("Tech Organization")
                .build();

        defaultUser = User.builder()
                .userId(USER_ID)
                .name("Alice")
                .email("alice@college.edu")
                .role(UserRole.STUDENT)
                .collegeDetails(CollegeDetails.builder()
                        .collegeId(COLLEGE_ID)
                        .collegeName("MIT College")
                        .build())
                .build();
    }

    // ========================================================================
    // createEvent
    // ========================================================================

    @Test
    void createEvent_HappyPath_SavesEventAndPublishesCreatedEvent() {
        // Alice is a club leader who creates a free college event
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(organizationService.isOrganizationLeaderOrManager(USER_ID, CLUB_ID)).thenReturn(true);
        when(collegeRepository.findById(COLLEGE_ID)).thenReturn(Optional.empty());

        Event incoming = buildActiveEvent(false, false);
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
            Event e = inv.getArgument(0);
            e.setId(EVENT_ID);
            return e;
        });

        Event result = eventService.createEvent(USER_ID, CLUB_ID, incoming);

        assertThat(result.getId()).isEqualTo(EVENT_ID);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getCreatedByUserId()).isEqualTo(USER_ID);
        verify(eventPublisher).publishEvent(any(EventCreatedEvent.class));
    }

    @Test
    void createEvent_IdempotencyKeyExists_ReturnsExistingEvent() {
        Event incoming = buildActiveEvent(false, false);
        incoming.setIdempotencyKey("test-idempotency-key");

        Event existing = buildActiveEvent(false, false);
        existing.setId(EVENT_ID);
        existing.setIdempotencyKey("test-idempotency-key");

        when(eventRepository.findByIdempotencyKey("test-idempotency-key")).thenReturn(Optional.of(existing));

        Event result = eventService.createEvent(USER_ID, CLUB_ID, incoming);

        assertThat(result.getId()).isEqualTo(EVENT_ID);
        assertThat(result.getIdempotencyKey()).isEqualTo("test-idempotency-key");
        // Ensure eventRepository.save was not called
        verify(eventRepository, never()).save(any(Event.class));
        verify(eventPublisher, never()).publishEvent(any(EventCreatedEvent.class));
    }

    @Test
    void createEvent_ConcurrentDuplicateKeyException_ReturnsExistingEvent() {
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(organizationService.isOrganizationLeaderOrManager(USER_ID, CLUB_ID)).thenReturn(true);
        when(collegeRepository.findById(COLLEGE_ID)).thenReturn(Optional.empty());

        Event incoming = buildActiveEvent(false, false);
        incoming.setIdempotencyKey("test-idempotency-key");

        Event existing = buildActiveEvent(false, false);
        existing.setId(EVENT_ID);
        existing.setIdempotencyKey("test-idempotency-key");

        // First findByIdempotencyKey call returns empty (concurrency check)
        // Second findByIdempotencyKey call inside catch returns the existing saved event
        when(eventRepository.findByIdempotencyKey("test-idempotency-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));

        when(eventRepository.save(any(Event.class))).thenThrow(new DuplicateKeyException("Duplicate key error"));

        Event result = eventService.createEvent(USER_ID, CLUB_ID, incoming);

        assertThat(result.getId()).isEqualTo(EVENT_ID);
        assertThat(result.getIdempotencyKey()).isEqualTo("test-idempotency-key");
        verify(eventRepository).save(any(Event.class));
        verify(eventPublisher, never()).publishEvent(any(EventCreatedEvent.class));
    }

    @Test
    void createEvent_UserIsNotClubLeader_Throws() {
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(organizationService.isOrganizationLeaderOrManager(USER_ID, CLUB_ID)).thenReturn(false);

        assertThatThrownBy(() -> eventService.createEvent(USER_ID, CLUB_ID, buildActiveEvent(false, false)))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("President");
    }

    @Test
    void createEvent_ExternallyManaged_MissingUrl_Throws() {
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(organizationService.isOrganizationLeaderOrManager(USER_ID, CLUB_ID)).thenReturn(true);

        Event incoming = Event.builder()
                .name("Hackathon")
                .description("A big hackathon event for all engineers")
                .isExternallyManaged(true)
                // externalRegistrationUrl intentionally omitted
                .registrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .eventStartDate(Instant.now().plus(10, ChronoUnit.DAYS))
                .eventEndDate(Instant.now().plus(11, ChronoUnit.DAYS))
                .build();

        assertThatThrownBy(() -> eventService.createEvent(USER_ID, CLUB_ID, incoming))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("externalRegistrationUrl");
    }

    @Test
    void createEvent_ExternallyManaged_PaymentFieldsAreWiped() {
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(organizationService.isOrganizationLeaderOrManager(USER_ID, CLUB_ID)).thenReturn(true);
        when(collegeRepository.findById(COLLEGE_ID)).thenReturn(Optional.empty());
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Event incoming = Event.builder()
                .name("External Event")
                .description("Register on our external platform for this amazing event")
                .isExternallyManaged(true)
                .externalRegistrationUrl("https://example.com/register")
                .isPaid(true)
                .fee(500)
                .upiId("organizer@upi")
                .idCardRequired(true)
                .registrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .eventStartDate(Instant.now().plus(10, ChronoUnit.DAYS))
                .eventEndDate(Instant.now().plus(11, ChronoUnit.DAYS))
                .build();

        Event result = eventService.createEvent(USER_ID, CLUB_ID, incoming);

        // Server must have wiped all CAX-payment fields regardless of what client sent
        assertThat(result.isPaid()).isFalse();
        assertThat(result.getFee()).isEqualTo(0);
        assertThat(result.getUpiId()).isNull();
        assertThat(result.isIdCardRequired()).isFalse();
    }

    @Test
    void createEvent_TooManyCoordinators_Throws() {
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(organizationService.isOrganizationLeaderOrManager(USER_ID, CLUB_ID)).thenReturn(true);

        Event incoming = buildActiveEvent(false, false);
        incoming.setCoordinators(List.of(
                buildCoordinator("c1"), buildCoordinator("c2"),
                buildCoordinator("c3"), buildCoordinator("c4"),
                buildCoordinator("c5")   // 5 coordinators — limit is 4
        ));

        assertThatThrownBy(() -> eventService.createEvent(USER_ID, CLUB_ID, incoming))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("4 coordinators");
    }

    // ========================================================================
    // updateEvent
    // ========================================================================

    @Test
    void updateEvent_CannotChangeGlobalFlag_Throws() {
        Event existing = buildActiveEvent(false, false);
        existing.setId(EVENT_ID);
        existing.setGlobal(false);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existing));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(organizationService.isOrganizationLeaderOrManager(USER_ID, CLUB_ID)).thenReturn(true);

        Event update = buildActiveEvent(false, false);
        update.setGlobal(true); // attempt to flip global flag

        assertThatThrownBy(() -> eventService.updateEvent(USER_ID, EVENT_ID, update))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("global");
    }

    @Test
    void updateEvent_CannotChangeExternallyManagedFlag_Throws() {
        Event existing = buildActiveEvent(false, false);
        existing.setId(EVENT_ID);
        existing.setExternallyManaged(false);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existing));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(organizationService.isOrganizationLeaderOrManager(USER_ID, CLUB_ID)).thenReturn(true);

        Event update = buildActiveEvent(false, false);
        update.setExternallyManaged(true); // attempt to flip externally-managed flag

        assertThatThrownBy(() -> eventService.updateEvent(USER_ID, EVENT_ID, update))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("Externally Managed");
    }

    @Test
    void updateEvent_CannotChangePaidFlag_Throws() {
        Event existing = buildActiveEvent(false, false);
        existing.setId(EVENT_ID);
        existing.setPaid(false);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existing));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(organizationService.isOrganizationLeaderOrManager(USER_ID, CLUB_ID)).thenReturn(true);

        Event update = buildActiveEvent(false, false);
        update.setPaid(true); // attempt to change from free to paid

        assertThatThrownBy(() -> eventService.updateEvent(USER_ID, EVENT_ID, update))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("paid");
    }

    // ========================================================================
    // cancelEvent
    // ========================================================================

    @Test
    void cancelEvent_EventCreator_CanCancelSuccessfully() {
        Event existing = buildActiveEvent(false, false);
        existing.setId(EVENT_ID);
        existing.setCreatedByUserId(USER_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existing));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        eventService.cancelEvent(USER_ID, EVENT_ID);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelEvent_RandomUser_CannotCancel() {
        Event existing = buildActiveEvent(false, false);
        existing.setId(EVENT_ID);
        existing.setCreatedByUserId("other-user");
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existing));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(organizationService.isOrganizationLeaderOrManager("random-user", CLUB_ID)).thenReturn(false);
        when(userService.getUserByUserId("random-user")).thenReturn(
                User.builder().userId("random-user").role(UserRole.STUDENT).build());

        assertThatThrownBy(() -> eventService.cancelEvent("random-user", EVENT_ID))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("organizer");
    }

    // ========================================================================
    // registerForEvent
    // ========================================================================

    @Test
    void registerForEvent_FreeCollegeEvent_CreatesParticipantWithPendingApproval() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setOrganizationId(CLUB_ID);
        event.setRegistrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS));

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);
        when(userService.getUserByUserId(USER_ID)).thenReturn(defaultUser);
        when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventParticipant result = eventService.registerForEvent(USER_ID, EVENT_ID, null);

        assertThat(result.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    void registerForEvent_PaidEvent_CreatesParticipantWithPendingPayment() {
        Event event = buildActiveEvent(true, false);
        event.setId(EVENT_ID);
        event.setOrganizationId(CLUB_ID);
        event.setRegistrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS));
        event.setFee(299);

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);
        when(userService.getUserByUserId(USER_ID)).thenReturn(defaultUser);
        when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventParticipant result = eventService.registerForEvent(USER_ID, EVENT_ID, null);

        assertThat(result.getStatus()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void registerForEvent_AlreadyRegistered_Throws() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> eventService.registerForEvent(USER_ID, EVENT_ID, null))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void registerForEvent_RegistrationDeadlinePassed_Throws() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setRegistrationEndDate(Instant.now().minus(1, ChronoUnit.DAYS)); // deadline passed yesterday
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> eventService.registerForEvent(USER_ID, EVENT_ID, null))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("deadline");
    }

    @Test
    void registerForEvent_EventIsCancelled_Throws() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setStatus("CANCELLED");
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> eventService.registerForEvent(USER_ID, EVENT_ID, null))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("no longer accepting");
    }

    @Test
    void registerForEvent_ExternallyManagedEvent_Throws() {
        Event event = buildActiveEvent(false, true);
        event.setId(EVENT_ID);
        event.setExternalRegistrationUrl("https://external.com/signup");
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);

        assertThatThrownBy(() -> eventService.registerForEvent(USER_ID, EVENT_ID, null))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("externally");
    }

    @Test
    void registerForEvent_CollegeEventFromDifferentCollege_Throws() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setOrganizationId(CLUB_ID);
        event.setGlobal(false);
        event.setRegistrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS));

        Organization otherCollegeClub = Organization.builder().id(CLUB_ID).collegeId("other-college").build();
        User outsiderUser = User.builder()
                .userId("outsider")
                .role(UserRole.STUDENT)
                .collegeDetails(CollegeDetails.builder().collegeId("their-college").build())
                .build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(otherCollegeClub);
        when(eventParticipantRepository.existsByEventIdAndUserId(EVENT_ID, "outsider")).thenReturn(false);
        when(userService.getUserByUserId("outsider")).thenReturn(outsiderUser);

        assertThatThrownBy(() -> eventService.registerForEvent("outsider", EVENT_ID, null))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("own college");
    }

    @Test
    void registerForEvent_GlobalEvent_AllowsStudentFromAnyCollege() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setOrganizationId(CLUB_ID);
        event.setGlobal(true);
        event.setRegistrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS));

        User outsider = User.builder()
                .userId("outsider")
                .name("Bob")
                .email("bob@other.edu")
                .role(UserRole.STUDENT)
                .collegeDetails(CollegeDetails.builder().collegeId("another-college").build())
                .build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.existsByEventIdAndUserId(EVENT_ID, "outsider")).thenReturn(false);
        when(userService.getUserByUserId("outsider")).thenReturn(outsider);
        when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventParticipant result = eventService.registerForEvent("outsider", EVENT_ID, null);

        assertThat(result.getUserId()).isEqualTo("outsider");
        assertThat(result.getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void registerForEvent_IdCardRequired_MissingDetails_Throws() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setOrganizationId(CLUB_ID);
        event.setIdCardRequired(true);
        event.setRegistrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS));

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);
        when(userService.getUserByUserId(USER_ID)).thenReturn(defaultUser);

        // Passing null body — no ID card details
        assertThatThrownBy(() -> eventService.registerForEvent(USER_ID, EVENT_ID, null))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("ID card");
    }

    @Test
    void registerForEvent_IdCardRequired_WithDetails_Succeeds() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setOrganizationId(CLUB_ID);
        event.setIdCardRequired(true);
        event.setRegistrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS));

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);
        when(userService.getUserByUserId(USER_ID)).thenReturn(defaultUser);
        when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> idCardDetails = Map.of(
                "idCardNumber", "CS2021001",
                "idCardName", "Alice Smith",
                "idCardDepartment", "Computer Science"
        );

        EventParticipant result = eventService.registerForEvent(USER_ID, EVENT_ID, idCardDetails);

        assertThat(result.getIdCardName()).isEqualTo("Alice Smith");
        assertThat(result.getIdCardDepartment()).isEqualTo("Computer Science");
        // idCardNumber is encrypted, so we just verify it's not null
        assertThat(result.getIdCardNumber()).isNotNull();
    }

    @Test
    void registerForEvent_RequiredFields_MissingDetails_Throws() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setOrganizationId(CLUB_ID);
        event.setRequiredFields(List.of("gender", "phoneNumber"));
        event.setRegistrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS));

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);
        when(userService.getUserByUserId(USER_ID)).thenReturn(defaultUser);

        // Map has gender, but lacks phoneNumber
        Map<String, Object> details = Map.of("gender", "Male");

        assertThatThrownBy(() -> eventService.registerForEvent(USER_ID, EVENT_ID, details))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("phoneNumber is required");
    }

    @Test
    void registerForEvent_RequiredFields_WithDetails_Succeeds() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setOrganizationId(CLUB_ID);
        event.setRequiredFields(List.of("gender", "phoneNumber"));
        event.setRegistrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS));

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.existsByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(false);
        when(userService.getUserByUserId(USER_ID)).thenReturn(defaultUser);
        when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> details = Map.of(
                "gender", "Female",
                "phoneNumber", "+919876543210"
        );

        EventParticipant result = eventService.registerForEvent(USER_ID, EVENT_ID, details);

        assertThat(result.getGender()).isEqualTo("Female");
        assertThat(result.getPhoneNumber()).isEqualTo("+919876543210");
    }

    // ========================================================================
    // submitPayment
    // ========================================================================

    @Test
    void submitPayment_HappyPath_UpdatesStatusToPaymentSubmitted() {
        Event event = buildActiveEvent(true, false);
        event.setId(EVENT_ID);
        EventParticipant participant = EventParticipant.builder()
                .id("part-001")
                .eventId(EVENT_ID)
                .userId(USER_ID)
                .status("PENDING_PAYMENT")
                .build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.findFirstByEventIdAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(participant));
        when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventParticipant result = eventService.submitPayment(USER_ID, EVENT_ID, "UTR123456", "https://r2.cax.in/screenshot.jpg", 299.0);

        assertThat(result.getStatus()).isEqualTo("PAYMENT_SUBMITTED");
        assertThat(result.getAmountPaid()).isEqualTo(299.0);
        assertThat(result.getPaymentScreenshot()).isEqualTo("https://r2.cax.in/screenshot.jpg");
        // UTR is encrypted on save
        assertThat(result.getUtrNumber()).isNotNull();
    }

    @Test
    void submitPayment_FreeEvent_Throws() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);

        assertThatThrownBy(() -> eventService.submitPayment(USER_ID, EVENT_ID, "UTR123", "https://r2.cax.in/ss.jpg", 0))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("free event");
    }

    @Test
    void submitPayment_WrongStatus_Throws() {
        Event event = buildActiveEvent(true, false);
        event.setId(EVENT_ID);
        EventParticipant participant = EventParticipant.builder()
                .eventId(EVENT_ID).userId(USER_ID).status("VERIFIED").build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.findFirstByEventIdAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> eventService.submitPayment(USER_ID, EVENT_ID, "UTR123", "https://r2.cax.in/ss.jpg", 299))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void submitPayment_NotRegistered_Throws() {
        Event event = buildActiveEvent(true, false);
        event.setId(EVENT_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.findFirstByEventIdAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.submitPayment(USER_ID, EVENT_ID, "UTR123", "https://r2.cax.in/ss.jpg", 299))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("not registered");
    }

    // ========================================================================
    // verifyPayment
    // ========================================================================

    @Test
    void verifyPayment_Approve_GeneratesTicketCodeAndSetsVerified() {
        Event event = buildActiveEvent(true, false);
        event.setId(EVENT_ID);
        event.setCreatedByUserId(USER_ID);
        EventParticipant participant = EventParticipant.builder()
                .id("part-001")
                .eventId(EVENT_ID)
                .userId("student-001")
                .status("PAYMENT_SUBMITTED")
                .build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.findById("part-001")).thenReturn(Optional.of(participant));
        when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventParticipant result = eventService.verifyPayment(USER_ID, EVENT_ID, "part-001", true);

        assertThat(result.getStatus()).isEqualTo("VERIFIED");
        assertThat(result.getTicketCode()).startsWith("CAX-");
        assertThat(result.getVerifiedByUserId()).isEqualTo(USER_ID);
        verify(eventPublisher).publishEvent(any(EventRegistrationReviewedEvent.class));
    }

    @Test
    void verifyPayment_Reject_SetsStatusToRejected() {
        Event event = buildActiveEvent(true, false);
        event.setId(EVENT_ID);
        event.setCreatedByUserId(USER_ID);
        EventParticipant participant = EventParticipant.builder()
                .id("part-001")
                .eventId(EVENT_ID)
                .userId("student-001")
                .status("PAYMENT_SUBMITTED")
                .build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.findById("part-001")).thenReturn(Optional.of(participant));
        when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventParticipant result = eventService.verifyPayment(USER_ID, EVENT_ID, "part-001", false);

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getTicketCode()).isNull(); // no ticket on rejection
    }

    @Test
    void verifyPayment_ApproveAlreadyVerified_PreservesTicketCode() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setCreatedByUserId(USER_ID);
        EventParticipant participant = EventParticipant.builder()
                .id("part-001")
                .eventId(EVENT_ID)
                .userId("student-001")
                .status("VERIFIED")
                .ticketCode("CAX-EXISTING1")
                .build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.findById("part-001")).thenReturn(Optional.of(participant));
        when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventParticipant result = eventService.verifyPayment(USER_ID, EVENT_ID, "part-001", true);

        // existing ticket code must not be overwritten
        assertThat(result.getTicketCode()).isEqualTo("CAX-EXISTING1");
    }

    @Test
    void verifyPayment_ExternallyManagedEvent_Throws() {
        Event event = buildActiveEvent(false, true);
        event.setId(EVENT_ID);
        event.setCreatedByUserId(USER_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);

        assertThatThrownBy(() -> eventService.verifyPayment(USER_ID, EVENT_ID, "part-001", true))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("externally managed");
    }

    // ========================================================================
    // checkInParticipant
    // ========================================================================

    @Test
    void checkInParticipant_ValidTicket_MarksCheckedIn() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setCreatedByUserId(USER_ID);
        EventParticipant participant = EventParticipant.builder()
                .id("part-001")
                .eventId(EVENT_ID)
                .userId("student-001")
                .status("VERIFIED")
                .ticketCode("CAX-TICKET1")
                .checkedIn(false)
                .build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.findByEventIdAndTicketCode(EVENT_ID, "CAX-TICKET1"))
                .thenReturn(Optional.of(participant));
        when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventParticipant result = eventService.checkInParticipant(USER_ID, EVENT_ID, "CAX-TICKET1");

        assertThat(result.isCheckedIn()).isTrue();
        assertThat(result.getCheckedInAt()).isNotNull();
    }

    @Test
    void checkInParticipant_AlreadyCheckedIn_Throws() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setCreatedByUserId(USER_ID);
        EventParticipant participant = EventParticipant.builder()
                .id("part-001")
                .eventId(EVENT_ID)
                .status("VERIFIED")
                .ticketCode("CAX-TICKET1")
                .checkedIn(true)
                .checkedInAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.findByEventIdAndTicketCode(EVENT_ID, "CAX-TICKET1"))
                .thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> eventService.checkInParticipant(USER_ID, EVENT_ID, "CAX-TICKET1"))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("already checked in");
    }

    @Test
    void checkInParticipant_TicketNotVerified_Throws() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setCreatedByUserId(USER_ID);
        EventParticipant participant = EventParticipant.builder()
                .id("part-001")
                .eventId(EVENT_ID)
                .status("PENDING_APPROVAL") // not verified yet
                .ticketCode("CAX-TICKET1")
                .build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.findByEventIdAndTicketCode(EVENT_ID, "CAX-TICKET1"))
                .thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> eventService.checkInParticipant(USER_ID, EVENT_ID, "CAX-TICKET1"))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("status");
    }

    @Test
    void checkInParticipant_InvalidTicketCode_Throws() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setCreatedByUserId(USER_ID);

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.findByEventIdAndTicketCode(EVENT_ID, "INVALID-CODE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.checkInParticipant(USER_ID, EVENT_ID, "INVALID-CODE"))
                .isInstanceOf(BusinessException.ResourceNotFoundException.class);
    }

    @Test
    void checkInParticipant_ExternallyManagedEvent_Throws() {
        Event event = buildActiveEvent(false, true);
        event.setId(EVENT_ID);
        event.setCreatedByUserId(USER_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);

        assertThatThrownBy(() -> eventService.checkInParticipant(USER_ID, EVENT_ID, "CAX-TICKET1"))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("externally managed");
    }

    // ========================================================================
    // discoverEvents
    // ========================================================================

    @Test
    void discoverEvents_FiltersOutExpiredRegistrationDeadlines() {
        Event activeEvent = buildActiveEvent(false, false);
        activeEvent.setId("active-001");
        activeEvent.setRegistrationEndDate(Instant.now().plus(5, ChronoUnit.DAYS));

        Event expiredEvent = buildActiveEvent(false, false);
        expiredEvent.setId("expired-001");
        expiredEvent.setRegistrationEndDate(Instant.now().minus(1, ChronoUnit.DAYS));

        when(userService.getUserByUserId(USER_ID)).thenReturn(defaultUser);
        when(eventRepository.findByStatusAndGlobalTrue("ACTIVE")).thenReturn(List.of());
        when(eventRepository.findByCollegeIdAndStatus(COLLEGE_ID, "ACTIVE"))
                .thenReturn(List.of(activeEvent, expiredEvent));

        List<Event> result = eventService.discoverEvents(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("active-001");
    }

    @Test
    void discoverEvents_GlobalAndCollegeEventsAreCombined() {
        Event globalEvent = buildActiveEvent(false, false);
        globalEvent.setId("global-001");
        globalEvent.setGlobal(true);
        globalEvent.setRegistrationEndDate(Instant.now().plus(3, ChronoUnit.DAYS));

        Event collegeEvent = buildActiveEvent(false, false);
        collegeEvent.setId("college-001");
        collegeEvent.setRegistrationEndDate(Instant.now().plus(3, ChronoUnit.DAYS));

        when(userService.getUserByUserId(USER_ID)).thenReturn(defaultUser);
        when(eventRepository.findByStatusAndGlobalTrue("ACTIVE")).thenReturn(List.of(globalEvent));
        when(eventRepository.findByCollegeIdAndStatus(COLLEGE_ID, "ACTIVE")).thenReturn(List.of(collegeEvent));

        List<Event> result = eventService.discoverEvents(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(Event::getId).toList()).contains("global-001", "college-001");
    }

    @Test
    void discoverEvents_NullUserId_ReturnsOnlyGlobalEvents() {
        Event globalEvent = buildActiveEvent(false, false);
        globalEvent.setId("global-001");
        globalEvent.setGlobal(true);
        globalEvent.setRegistrationEndDate(Instant.now().plus(3, ChronoUnit.DAYS));

        when(eventRepository.findByStatusAndGlobalTrue("ACTIVE")).thenReturn(List.of(globalEvent));

        List<Event> result = eventService.discoverEvents(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("global-001");
        // should never call userService for anonymous requests
        verify(userService, never()).getUserByUserId(any());
    }

    // ========================================================================
    // getEventDetailForUser
    // ========================================================================

    @Test
    void getEventDetailForUser_EventCreator_GetsOrganizerRole() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setOrganizationId(CLUB_ID);
        event.setCreatedByUserId(USER_ID);

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(userService.getUserByUserId(USER_ID)).thenReturn(defaultUser);
        when(eventParticipantRepository.findFirstByEventIdAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(collegeRepository.findById(COLLEGE_ID)).thenReturn(Optional.empty());

        Map<String, Object> result = eventService.getEventDetailForUser(USER_ID, EVENT_ID);

        assertThat(result.get("organizerRole")).isEqualTo("organizer");
        assertThat(result.get("participantStatus")).isNull();
    }

    @Test
    void getEventDetailForUser_RegularStudent_HasNullOrganizerRole() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setOrganizationId(CLUB_ID);
        event.setCreatedByUserId("someone-else");

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(userService.getUserByUserId(USER_ID)).thenReturn(defaultUser);
        when(organizationService.isOrganizationLeaderOrManager(USER_ID, CLUB_ID)).thenReturn(false);
        when(eventParticipantRepository.findFirstByEventIdAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(collegeRepository.findById(COLLEGE_ID)).thenReturn(Optional.empty());

        Map<String, Object> result = eventService.getEventDetailForUser(USER_ID, EVENT_ID);

        assertThat(result.get("organizerRole")).isNull();
    }

    @Test
    void getEventDetailForUser_StudentFromDifferentCollege_Throws() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setOrganizationId(CLUB_ID);
        event.setGlobal(false);

        Organization organization = Organization.builder().id(CLUB_ID).collegeId("college-abc").build();
        User outsider = User.builder()
                .userId("outsider")
                .role(UserRole.STUDENT)
                .collegeDetails(CollegeDetails.builder().collegeId("college-xyz").build())
                .build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(organization);
        when(userService.getUserByUserId("outsider")).thenReturn(outsider);
        when(collegeRepository.findById("college-abc")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEventDetailForUser("outsider", EVENT_ID))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("another college");
    }

    // ========================================================================
    // toggleSuspicious
    // ========================================================================

    @Test
    void toggleSuspicious_OrganizerMarksSuspicious_SavesNote() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setCreatedByUserId(USER_ID);
        EventParticipant participant = EventParticipant.builder()
                .id("part-001")
                .eventId(EVENT_ID)
                .suspicious(false)
                .build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventParticipantRepository.findById("part-001")).thenReturn(Optional.of(participant));
        when(eventParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventParticipant result = eventService.toggleSuspicious(USER_ID, EVENT_ID, "part-001", true, "Duplicate payment screenshot");

        assertThat(result.isSuspicious()).isTrue();
        assertThat(result.getSuspiciousNote()).isEqualTo("Duplicate payment screenshot");
    }

    // ========================================================================
    // sendEventAnnouncementNotification
    // ========================================================================

    @Test
    void sendEventAnnouncement_GlobalEvent_LimitIsOne_ThrowsOnSecondCall() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setCreatedByUserId(USER_ID);
        event.setGlobal(true);
        event.setNotificationsSentCount(1); // already sent once (limit is 1 for global events)

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);

        assertThatThrownBy(() -> eventService.sendEventAnnouncementNotification(USER_ID, EVENT_ID))
                .isInstanceOf(BusinessException.BadRequestException.class)
                .hasMessageContaining("Maximum notifications");
    }

    @Test
    void sendEventAnnouncement_CollegeEvent_LimitIsTwo_AllowsSecondCall() {
        Event event = buildActiveEvent(false, false);
        event.setId(EVENT_ID);
        event.setCreatedByUserId(USER_ID);
        event.setGlobal(false);
        event.setCollegeId(COLLEGE_ID);
        event.setNotificationsSentCount(1); // already sent once (limit is 2 for college events)

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizationService.getOrganizationById(CLUB_ID)).thenReturn(defaultOrganization);
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findNotificationEligibleUsersByCollegeId(COLLEGE_ID)).thenReturn(List.of());
        // Execute the async task inline in tests
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(taskExecutor).execute(any(Runnable.class));

        // Should not throw — second announcement for college event is within the limit
        eventService.sendEventAnnouncementNotification(USER_ID, EVENT_ID);

        verify(eventRepository).save(argThat(e -> e.getNotificationsSentCount() == 2));
    }

    // ========================================================================
    // helpers
    // ========================================================================

    private Event buildActiveEvent(boolean isPaid, boolean isExternallyManaged) {
        return Event.builder()
                .organizationId(CLUB_ID)
                .name("Test Event")
                .description("A detailed description of this test event for everyone")
                .status("ACTIVE")
                .isPaid(isPaid)
                .isExternallyManaged(isExternallyManaged)
                .global(false)
                .registrationEndDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .eventStartDate(Instant.now().plus(10, ChronoUnit.DAYS))
                .eventEndDate(Instant.now().plus(11, ChronoUnit.DAYS))
                .build();
    }

    private com.cax.cax_backend.event.model.EventCoordinator buildCoordinator(String name) {
        return com.cax.cax_backend.event.model.EventCoordinator.builder()
                .name(name)
                .phone("9999999999")
                .email(name + "@college.edu")
                .build();
    }
}
