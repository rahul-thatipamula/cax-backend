package com.cax.cax_backend.event.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.cax.cax_backend.club.model.Club;
import com.cax.cax_backend.club.model.ClubMember;
import com.cax.cax_backend.club.service.ClubService;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.repository.EventParticipantRepository;
import com.cax.cax_backend.event.repository.EventRepository;
import com.cax.cax_backend.idcard.model.IDCard;
import com.cax.cax_backend.idcard.repository.IDCardRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;

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

    // ========================================================================
    // EVENT CRUD
    // ========================================================================

    public Event createEvent(String userId, String clubId, Event eventData) {
        Club club = clubService.getClubById(clubId);
        verifyClubLeader(userId, club);

        if (eventData.getEventImages() != null && eventData.getEventImages().size() > 9) {
            throw new BusinessException.BadRequestException("An event can have at most 9 event-related images.");
        }

        eventData.setClubId(clubId);
        eventData.setCreatedByUserId(userId);
        eventData.setStatus("ACTIVE");
        eventData.setCreatedAt(Instant.now());
        eventData.setUpdatedAt(Instant.now());

        Event saved = eventRepository.save(eventData);

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
        event.setFee(eventData.getFee());
        if (eventData.getUpiId() != null) event.setUpiId(eventData.getUpiId());
        if (eventData.getUpiQrCode() != null) event.setUpiQrCode(eventData.getUpiQrCode());
        if (eventData.getEventImages() != null) {
            if (eventData.getEventImages().size() > 9) {
                throw new BusinessException.BadRequestException("An event can have at most 9 event-related images.");
            }
            event.setEventImages(eventData.getEventImages());
        }

        event.setUpdatedAt(Instant.now());
        return eventRepository.save(event);
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
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Event", eventId));
    }

    public Map<String, Object> getEventDetailForUser(String userId, String eventId) {
        Event event = getEventById(eventId);
        Club club = clubService.getClubById(event.getClubId());
        User user = userService.getUserByUserId(userId);
        
        boolean isSystemAdmin = user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN;
        if (!isSystemAdmin && user.getCollegeDetails() != null && !club.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
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

    public List<Event> discoverEvents() {
        List<Event> activeEvents = eventRepository.findByStatus("ACTIVE");
        Instant now = Instant.now();

        // Filter out events past registration deadline
        return activeEvents.stream()
                .filter(e -> e.getRegistrationEndDate() != null && e.getRegistrationEndDate().isAfter(now))
                .collect(Collectors.toList());
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

        return eventParticipantRepository.save(participant);
    }

    public List<EventParticipant> getParticipants(String userId, String eventId) {
        // Access control removed - all users can view participants
        List<EventParticipant> participants = eventParticipantRepository.findByEventId(eventId);
        for (EventParticipant p : participants) {
            idCardRepository.findByUserId(p.getUserId()).ifPresent(card -> {
                p.setIdCardNumber(card.getIdCardNumber());
            });
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
}
