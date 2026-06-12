package com.cax.cax_backend.event.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.cax.cax_backend.club.model.Club;
import com.cax.cax_backend.club.model.ClubMember;
import com.cax.cax_backend.club.service.ClubService;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.event.event.EventCreatedEvent;
import com.cax.cax_backend.event.event.EventRegistrationReviewedEvent;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.repository.EventParticipantRepository;
import com.cax.cax_backend.event.repository.EventRepository;
import com.cax.cax_backend.idcard.model.IDCard;
import com.cax.cax_backend.idcard.repository.IDCardRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import com.cax.cax_backend.event.model.EventMemory;
import com.cax.cax_backend.event.repository.EventMemoryRepository;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final ClubService clubService;
    private final UserService userService;
    private final IDCardRepository idCardRepository;
    private final com.cax.cax_backend.college.repository.CollegeRepository collegeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EventMemoryRepository eventMemoryRepository;
    private final NotificationService notificationService;

    // ========================================================================
    // EVENT CRUD
    // ========================================================================

    private void populateCollegeDetails(Event event) {
        if (event == null) return;
        try {
            Club club = clubService.getClubById(event.getClubId());
            event.setCollegeId(club.getCollegeId());
            collegeRepository.findById(club.getCollegeId()).ifPresent(c -> {
                event.setCollegeName(c.getCollegeName());
            });
        } catch (Exception e) {
            log.warn("Failed to populate college details for event: {}", event.getId(), e);
        }
    }

    public Event createEvent(String userId, String clubId, Event eventData) {
        Club club = clubService.getClubById(clubId);
        verifyClubLeader(userId, club);

        if (eventData.getEventImages() != null && eventData.getEventImages().size() > 9) {
            throw new BusinessException.BadRequestException("An event can have at most 9 event-related images.");
        }

        if (eventData.getCoordinators() != null && eventData.getCoordinators().size() > 4) {
            throw new BusinessException.BadRequestException("An event can have at most 4 coordinators.");
        }

        eventData.setClubId(clubId);
        eventData.setCollegeId(club.getCollegeId());
        collegeRepository.findById(club.getCollegeId()).ifPresent(c -> {
            eventData.setCollegeName(c.getCollegeName());
        });
        eventData.setCreatedByUserId(userId);
        eventData.setStatus("ACTIVE");
        eventData.setCreatedAt(Instant.now());
        eventData.setUpdatedAt(Instant.now());

        Event saved = eventRepository.save(eventData);

        try {
            eventPublisher.publishEvent(new EventCreatedEvent(this, saved));
        } catch (Exception e) {
            log.error("Failed to publish EventCreatedEvent for event: {}", saved.getId(), e);
        }

        log.info("Event '{}' created by user {} in club {}", saved.getName(), userId, clubId);
        return saved;
    }

    public Event updateEvent(String userId, String eventId, Event eventData) {
        Event event = getEventById(eventId);
        Club club = clubService.getClubById(event.getClubId());
        verifyClubLeader(userId, club);

        // Update allowed fields
        if (eventData.getName() != null) event.setName(eventData.getName());
        if (eventData.getDescription() != null) event.setDescription(eventData.getDescription());
        if (eventData.getLogo() != null) event.setLogo(eventData.getLogo());
        if (eventData.getRegistrationEndDate() != null) event.setRegistrationEndDate(eventData.getRegistrationEndDate());
        if (eventData.getEventStartDate() != null) event.setEventStartDate(eventData.getEventStartDate());
        if (eventData.getEventEndDate() != null) event.setEventEndDate(eventData.getEventEndDate());
        event.setPaid(eventData.isPaid());
        event.setGlobal(eventData.isGlobal());
        event.setFee(eventData.getFee());
        if (eventData.getUpiId() != null) event.setUpiId(eventData.getUpiId());
        if (eventData.getUpiQrCode() != null) event.setUpiQrCode(eventData.getUpiQrCode());
        if (eventData.getEventImages() != null) {
            if (eventData.getEventImages().size() > 9) {
                throw new BusinessException.BadRequestException("An event can have at most 9 event-related images.");
            }
            event.setEventImages(eventData.getEventImages());
        }

        if (eventData.getCoordinators() != null) {
            if (eventData.getCoordinators().size() > 4) {
                throw new BusinessException.BadRequestException("An event can have at most 4 coordinators.");
            }
            event.setCoordinators(eventData.getCoordinators());
        }

        if (eventData.getGuidelines() != null) {
            event.setGuidelines(eventData.getGuidelines());
        }
        if (eventData.getJury() != null) {
            event.setJury(eventData.getJury());
        }
        if (eventData.getGuests() != null) {
            event.setGuests(eventData.getGuests());
        }

        event.setUpdatedAt(Instant.now());
        Event saved = eventRepository.save(event);
        return saved;
    }

    public void cancelEvent(String userId, String eventId) {
        Event event = getEventById(eventId);
        // Access control removed - all users can cancel events

        event.setStatus("CANCELLED");
        event.setUpdatedAt(Instant.now());
        eventRepository.save(event);
        log.info("Event '{}' cancelled by user {}", event.getName(), userId);
    }

    // ========================================================================
    // EVENT QUERIES
    // ========================================================================

    public List<Event> getClubEvents(String userId, String clubId) {
        return eventRepository.findByClubId(clubId);
    }

    public Event getEventById(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Event", eventId));
        populateCollegeDetails(event);
        return event;
    }

    public Map<String, Object> getEventDetailForUser(String userId, String eventId) {
        Event event = getEventById(eventId);
        Club club = clubService.getClubById(event.getClubId());
        User user = userService.getUserByUserId(userId);
        
        boolean isSystemAdmin = user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN;
        if (!isSystemAdmin && !event.isGlobal() && user.getCollegeDetails() != null && !club.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
            throw new BusinessException.BadRequestException("You cannot access an event from another college.");
        }

        // Check participant status
        Optional<EventParticipant> participant = eventParticipantRepository.findByEventIdAndUserId(eventId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("event", event);
        result.put("organizerRole", null);
        result.put("participantStatus", participant.map(EventParticipant::getStatus).orElse(null));
        return result;
    }

    public List<Event> discoverEvents(String userId) {
        return discoverEvents(userId, 0, 50);
    }

    public List<Event> discoverEvents(String userId, int page, int size) {
        Instant now = Instant.now();

        final String userCollegeId;
        if (userId != null && !userId.isBlank()) {
            User user = userService.getUserByUserId(userId);
            userCollegeId = (user.getCollegeDetails() != null) ? user.getCollegeDetails().getCollegeId() : null;
        } else {
            userCollegeId = null;
        }

        // Fetch active global events
        List<Event> globalEvents = eventRepository.findByStatusAndGlobalTrue("ACTIVE");

        // Fetch active events from the user's college
        List<Event> collegeEvents = new java.util.ArrayList<>();
        if (userCollegeId != null) {
            collegeEvents = eventRepository.findByCollegeIdAndStatus(userCollegeId, "ACTIVE");
        }

        // Combine events and remove potential duplicates
        List<Event> allEvents = new java.util.ArrayList<>();
        allEvents.addAll(globalEvents);
        allEvents.addAll(collegeEvents);

        // Filter by date and extract distinct
        List<Event> filtered = allEvents.stream()
                .filter(e -> e.getRegistrationEndDate() != null && e.getRegistrationEndDate().isAfter(now))
                .distinct()
                .collect(Collectors.toList());

        int fromIndex = page * size;
        if (fromIndex >= filtered.size() || size <= 0) {
            return new java.util.ArrayList<>();
        }
        int toIndex = Math.min(fromIndex + size, filtered.size());
        return filtered.subList(fromIndex, toIndex);
    }

    public List<Map<String, Object>> getJoinedEvents(String userId) {
        return getJoinedEvents(userId, 0, 50);
    }

    public List<Map<String, Object>> getJoinedEvents(String userId, int page, int size) {
        List<EventParticipant> registrations = eventParticipantRepository.findByUserId(userId);
        if (registrations.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        List<String> eventIds = registrations.stream()
                .map(EventParticipant::getEventId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());

        List<Event> events = eventRepository.findAllById(eventIds);
        Map<String, Event> eventMap = events.stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (EventParticipant participant : registrations) {
            Event event = eventMap.get(participant.getEventId());
            if (event != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("event", event);
                item.put("organizerRole", null);
                item.put("participantStatus", participant.getStatus());
                result.add(item);
            }
        }

        int fromIndex = page * size;
        if (fromIndex >= result.size() || size <= 0) {
            return new java.util.ArrayList<>();
        }
        int toIndex = Math.min(fromIndex + size, result.size());
        return result.subList(fromIndex, toIndex);
    }


    // ========================================================================
    // PARTICIPANT MANAGEMENT
    // ========================================================================

    public EventParticipant registerForEvent(String userId, String eventId) {
        Event event = getEventById(eventId);

        if (!"ACTIVE".equals(event.getStatus())) {
            throw new BusinessException.BadRequestException("This event is no longer accepting registrations.");
        }

        if (event.getRegistrationEndDate() != null && event.getRegistrationEndDate().isBefore(Instant.now())) {
            throw new BusinessException.BadRequestException("Registration deadline has passed.");
        }

        if (eventParticipantRepository.existsByEventIdAndUserId(eventId, userId)) {
            throw new BusinessException.BadRequestException("You are already registered for this event.");
        }

        User user = userService.getUserByUserId(userId);
        String collegeName = null;
        if (user.getCollegeDetails() != null && user.getCollegeDetails().getCollegeName() != null) {
            collegeName = user.getCollegeDetails().getCollegeName();
        }

        // Free events → PENDING_APPROVAL (requires organizer approval)
        // Paid events → PENDING_PAYMENT (requires payment submission)
        String initialStatus = event.isPaid() ? "PENDING_PAYMENT" : "PENDING_APPROVAL";

        String idCardNumber = idCardRepository.findByUserId(userId)
                .map(IDCard::getIdCardNumber)
                .orElse(null);

        EventParticipant participant = EventParticipant.builder()
                .eventId(eventId)
                .userId(userId)
                .name(user.getName())
                .email(user.getEmail())
                .picture(user.getPicture())
                .college(collegeName)
                .idCardNumber(idCardNumber)
                .status(initialStatus)
                .registeredAt(Instant.now())
                .build();

        return eventParticipantRepository.save(participant);
    }

    public EventParticipant submitPayment(String userId, String eventId, String utrNumber, String screenshotUrl, double amount) {
        EventParticipant participant = eventParticipantRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BusinessException.BadRequestException("You are not registered for this event."));

        if (!"PENDING_PAYMENT".equals(participant.getStatus()) && !"REJECTED".equals(participant.getStatus())) {
            throw new BusinessException.BadRequestException("Payment submission is not allowed in current status: " + participant.getStatus());
        }

        participant.setUtrNumber(utrNumber);
        participant.setPaymentScreenshot(screenshotUrl);
        participant.setAmountPaid(amount);
        participant.setStatus("PAYMENT_SUBMITTED");

        return eventParticipantRepository.save(participant);
    }

    public EventParticipant verifyPayment(String organizerId, String eventId, String participantId, boolean approved) {
        // Access control removed - all users can verify payments

        EventParticipant participant = eventParticipantRepository.findById(participantId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("EventParticipant", participantId));

        if (!participant.getEventId().equals(eventId)) {
            throw new BusinessException.BadRequestException("Participant does not belong to this event.");
        }

        // Allow verification of PAYMENT_SUBMITTED (paid events) or PENDING_APPROVAL (free events), as well as VERIFIED/REJECTED for changing minds later
        String currentStatus = participant.getStatus();
        if (!"PAYMENT_SUBMITTED".equals(currentStatus) && 
            !"PENDING_APPROVAL".equals(currentStatus) && 
            !"VERIFIED".equals(currentStatus) && 
            !"REJECTED".equals(currentStatus)) {
            throw new BusinessException.BadRequestException("Participant is not in a verifiable status: " + currentStatus);
        }

        if (approved && (participant.getTicketCode() == null || participant.getTicketCode().isEmpty())) {
            participant.setTicketCode("CAX-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        participant.setStatus(approved ? "VERIFIED" : "REJECTED");
        participant.setVerifiedByUserId(organizerId);
        participant.setVerifiedAt(Instant.now());

        EventParticipant saved = eventParticipantRepository.save(participant);
        try {
            Event event = getEventById(eventId);
            eventPublisher.publishEvent(new EventRegistrationReviewedEvent(this, saved, event));
        } catch (Exception e) {
            log.error("Failed to publish EventRegistrationReviewedEvent for participant: {}", participantId, e);
        }
        return saved;
    }

    public List<EventParticipant> getParticipants(String userId, String eventId) {
        // Access control removed - all users can view participants
        List<EventParticipant> participants = eventParticipantRepository.findByEventId(eventId);
        if (participants.isEmpty()) {
            return participants;
        }

        List<String> userIds = participants.stream()
                .map(EventParticipant::getUserId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());

        List<com.cax.cax_backend.idcard.model.IDCard> cards = idCardRepository.findByUserIdIn(userIds);
        Map<String, String> userIdToCardNumberMap = cards.stream()
                .filter(card -> card.getUserId() != null && card.getIdCardNumber() != null)
                .collect(Collectors.toMap(
                        com.cax.cax_backend.idcard.model.IDCard::getUserId,
                        com.cax.cax_backend.idcard.model.IDCard::getIdCardNumber,
                        (existing, replacement) -> existing
                ));

        for (EventParticipant p : participants) {
            String cardNumber = userIdToCardNumberMap.get(p.getUserId());
            if (cardNumber != null) {
                p.setIdCardNumber(cardNumber);
            }
        }
        return participants;
    }

    // ========================================================================
    // AUTHORIZATION HELPERS
    // ========================================================================

    private void verifyClubLeader(String userId, Club club) {
        if (clubService.isClubLeaderOrManager(userId, club.getId())) {
            return;
        }
        throw new BusinessException.BadRequestException("Only the President, Vice President, or Club Managers can perform this action.");
    }

    public EventParticipant checkInParticipant(String organizerId, String eventId, String ticketCode) {
        EventParticipant participant = eventParticipantRepository.findByEventIdAndTicketCode(eventId, ticketCode)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Ticket code not found for this event: " + ticketCode));

        if (!"VERIFIED".equals(participant.getStatus())) {
            throw new BusinessException.BadRequestException("Cannot check in. Ticket status is: " + participant.getStatus());
        }

        if (participant.isCheckedIn()) {
            throw new BusinessException.BadRequestException("Ticket is already checked in at: " + participant.getCheckedInAt());
        }

        participant.setCheckedIn(true);
        participant.setCheckedInAt(Instant.now());
        return eventParticipantRepository.save(participant);
    }

    public Map<String, Object> getParticipantDetailsByCode(String eventId, String ticketCode) {
        EventParticipant participant = eventParticipantRepository.findByEventIdAndTicketCode(eventId, ticketCode)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Ticket code not found for this event: " + ticketCode));

        IDCard idCard = idCardRepository.findByUserId(participant.getUserId()).orElse(null);

        Map<String, Object> details = new HashMap<>();
        details.put("participant", participant);
        details.put("idCard", idCard);
        return details;
    }

    public EventParticipant toggleSuspicious(String organizerId, String eventId, String participantId, boolean suspicious, String note) {
        EventParticipant participant = eventParticipantRepository.findById(participantId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("EventParticipant", participantId));

        if (!participant.getEventId().equals(eventId)) {
            throw new BusinessException.BadRequestException("Participant does not belong to this event.");
        }

        participant.setSuspicious(suspicious);
        participant.setSuspiciousNote(note);

        return eventParticipantRepository.save(participant);
    }

    public boolean hasEventManagePermission(String userId, Event event) {
        if (userId.equals(event.getCreatedByUserId())) {
            return true;
        }
        return clubService.isClubLeaderOrManager(userId, event.getClubId());
    }

    public EventMemory uploadMemory(String userId, String eventId, String imageUrl) {
        Event event = getEventById(eventId);
        if (!hasEventManagePermission(userId, event)) {
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to manage this event's memories.");
        }
        
        Instant now = Instant.now();
        boolean isOngoing = now.isAfter(event.getEventStartDate()) && now.isBefore(event.getEventEndDate());
        boolean isActive = "ACTIVE".equalsIgnoreCase(event.getStatus());
        if (!isOngoing || !isActive) {
            throw new BusinessException.BadRequestException("Memories can only be uploaded while the event is ongoing and active.");
        }

        EventMemory memory = EventMemory.builder()
                .eventId(eventId)
                .imageUrl(imageUrl)
                .uploadedAt(Instant.now())
                .hidden(false)
                .build();
        EventMemory saved = eventMemoryRepository.save(memory);

        try {
            List<EventParticipant> participants = eventParticipantRepository.findByEventId(eventId);
            if (participants != null && !participants.isEmpty()) {
                String title = "New Live Memory Shared!";
                String body = "A new live photo memory has been shared for the event: " + event.getName();

                Map<String, String> data = new HashMap<>();
                data.put("type", "EVENT_MEMORY_ADDED");
                data.put("eventId", eventId);
                data.put("deepLink", "cax://events/" + eventId);

                for (EventParticipant p : participants) {
                    if (p.getUserId() != null && !p.getUserId().equals(userId) && "VERIFIED".equalsIgnoreCase(p.getStatus())) {
                        try {
                            notificationService.createNotification(
                                    p.getUserId(),
                                    title,
                                    body,
                                    NotificationType.EVENT,
                                    data
                            );
                        } catch (Exception e) {
                            log.error("Failed to send memory notification to user: {}", p.getUserId(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error setting up memories notifications: ", e);
        }

        return saved;
    }

    public void deleteMemory(String userId, String eventId, String memoryId) {
        Event event = getEventById(eventId);
        if (!hasEventManagePermission(userId, event)) {
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to manage this event's memories.");
        }

        EventMemory memory = eventMemoryRepository.findById(memoryId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("EventMemory", memoryId));
        if (!memory.getEventId().equals(eventId)) {
            throw new BusinessException.BadRequestException("Memory does not belong to this event.");
        }

        eventMemoryRepository.delete(memory);
    }

    public EventMemory toggleHideMemory(String userId, String eventId, String memoryId, boolean hidden) {
        Event event = getEventById(eventId);
        if (!hasEventManagePermission(userId, event)) {
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to manage this event's memories.");
        }

        EventMemory memory = eventMemoryRepository.findById(memoryId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("EventMemory", memoryId));
        if (!memory.getEventId().equals(eventId)) {
            throw new BusinessException.BadRequestException("Memory does not belong to this event.");
        }

        memory.setHidden(hidden);
        return eventMemoryRepository.save(memory);
    }

    public Page<EventMemory> getEventMemories(String userId, String eventId, int page, int size, String filter) {
        Event event = getEventById(eventId);
        boolean isManager = hasEventManagePermission(userId, event);
        
        if (!isManager) {
            Optional<EventParticipant> participantOpt = eventParticipantRepository.findByEventIdAndUserId(eventId, userId);
            boolean isJoined = participantOpt.isPresent() && "VERIFIED".equalsIgnoreCase(participantOpt.get().getStatus());
            if (!isJoined) {
                throw new BusinessException.BadRequestException("Unauthorized: Only registered participants can view this event's memories.");
            }
        }

        Pageable pageable = PageRequest.of(page, size);
        if (isManager) {
            if ("hidden".equalsIgnoreCase(filter)) {
                return eventMemoryRepository.findByEventIdAndHiddenOrderByUploadedAtDesc(eventId, true, pageable);
            } else if ("visible".equalsIgnoreCase(filter)) {
                return eventMemoryRepository.findByEventIdAndHiddenOrderByUploadedAtDesc(eventId, false, pageable);
            } else {
                return eventMemoryRepository.findByEventIdOrderByUploadedAtDesc(eventId, pageable);
            }
        } else {
            return eventMemoryRepository.findByEventIdAndHiddenOrderByUploadedAtDesc(eventId, false, pageable);
        }
    }
}
