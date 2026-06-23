package com.cax.cax_backend.event.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.Executor;

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
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import com.cax.cax_backend.user.repository.UserRepository;
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
    private final com.cax.cax_backend.college.repository.CollegeRepository collegeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EventMemoryRepository eventMemoryRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final Executor taskExecutor;

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
        if (eventData.getWebsiteUrl() != null) {
            event.setWebsiteUrl(eventData.getWebsiteUrl());
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
        
        String status = participant.map(EventParticipant::getStatus).orElse(null);
        if (status != null && "REJECTED".equals(status) && !event.isPaid()) {
            status = "REGISTRATION_REJECTED";
        }
        result.put("participantStatus", status);
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

    public EventParticipant registerForEvent(String userId, String eventId, Map<String, Object> idCardDetails) {
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

        // Collect & validate ID card details when the event requires them
        String idCardNumber = null;
        String idCardName = null;
        String idCardDepartment = null;
        if (event.isIdCardRequired()) {
            idCardNumber = trimToNull(idCardDetails != null ? idCardDetails.get("idCardNumber") : null);
            idCardName = trimToNull(idCardDetails != null ? idCardDetails.get("idCardName") : null);
            idCardDepartment = trimToNull(idCardDetails != null ? idCardDetails.get("idCardDepartment") : null);
            if (idCardNumber == null || idCardName == null || idCardDepartment == null) {
                throw new BusinessException.BadRequestException(
                        "ID card number, name on card, and department are required to register for this event.");
            }
        }

        User user = userService.getUserByUserId(userId);
        String collegeName = null;
        if (user.getCollegeDetails() != null && user.getCollegeDetails().getCollegeName() != null) {
            collegeName = user.getCollegeDetails().getCollegeName();
        }

        // Free events → PENDING_APPROVAL (requires organizer approval)
        // Paid events → PENDING_PAYMENT (requires payment submission)
        String initialStatus = event.isPaid() ? "PENDING_PAYMENT" : "PENDING_APPROVAL";

        EventParticipant participant = EventParticipant.builder()
                .eventId(eventId)
                .userId(userId)
                .name(user.getName())
                .email(user.getEmail())
                .picture(user.getPicture())
                .college(collegeName)
                .idCardNumber(idCardNumber)
                .idCardName(idCardName)
                .idCardDepartment(idCardDepartment)
                .status(initialStatus)
                .registeredAt(Instant.now())
                .build();

        return eventParticipantRepository.save(participant);
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public EventParticipant submitPayment(String userId, String eventId, String utrNumber, String screenshotUrl, double amount) {
        Event event = getEventById(eventId);
        if (!event.isPaid()) {
            throw new BusinessException.BadRequestException("This is a free event, payment is not required.");
        }

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

        // ID card details lookup removed from participants list
        return participants;
    }

    public byte[] exportParticipantsToCsv(String userId, String eventId) {
        List<EventParticipant> participants = getParticipants(userId, eventId);

        StringBuilder csv = new StringBuilder();
        // CSV Header
        csv.append("Registration ID,Participant Name,Email Address,College Affiliation,ID Card Number,Name On ID Card,Department,Registration Date,Ticket Status,Ticket Code,Amount Paid (INR),UTR Number,Checked In,Checked In Time,Suspicious,Suspicious Note\n");

        for (EventParticipant p : participants) {
            csv.append(escapeCsv(p.getId())).append(",")
               .append(escapeCsv(p.getName())).append(",")
               .append(escapeCsv(p.getEmail())).append(",")
               .append(escapeCsv(p.getCollege())).append(",")
               .append(escapeCsv(p.getIdCardNumber())).append(",")
               .append(escapeCsv(p.getIdCardName())).append(",")
               .append(escapeCsv(p.getIdCardDepartment())).append(",")
               .append(escapeCsv(p.getRegisteredAt() != null ? p.getRegisteredAt().toString() : "")).append(",")
               .append(escapeCsv(p.getStatus())).append(",")
               .append(escapeCsv(p.getTicketCode())).append(",")
               .append(p.getAmountPaid()).append(",")
               .append(escapeCsv(p.getUtrNumber())).append(",")
               .append(p.isCheckedIn() ? "Yes" : "No").append(",")
               .append(escapeCsv(p.getCheckedInAt() != null ? p.getCheckedInAt().toString() : "")).append(",")
               .append(p.isSuspicious() ? "Yes" : "No").append(",")
               .append(escapeCsv(p.getSuspiciousNote())).append("\n");
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
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

    public void verifyEventManager(String userId, Event event) {
        if (userId.equals(event.getCreatedByUserId())) {
            return;
        }
        Club club = clubService.getClubById(event.getClubId());
        if (clubService.isClubLeaderOrManager(userId, club.getId())) {
            return;
        }
        User user = userService.getUserByUserId(userId);
        if (user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN) {
            return;
        }
        throw new BusinessException.BadRequestException("Only the event organizer or club leaders can manage this event.");
    }

    public void sendEventAnnouncementNotification(String userId, String eventId) {
        Event event = getEventById(eventId);
        verifyEventManager(userId, event);

        int maxAllowed = event.isGlobal() ? 1 : 2;
        if (event.getNotificationsSentCount() >= maxAllowed) {
            throw new BusinessException.BadRequestException("Maximum notifications limit reached for this event.");
        }

        // Increment count and save synchronously (prevents race conditions)
        event.setNotificationsSentCount(event.getNotificationsSentCount() + 1);
        event.setUpdatedAt(Instant.now());
        eventRepository.save(event);

        // Fetch users to notify
        List<User> targetUsers;
        if (event.isGlobal()) {
            targetUsers = userRepository.findGlobalNotificationEligibleUsers();
        } else {
            String collegeId = event.getCollegeId();
            if (collegeId == null || collegeId.isBlank()) {
                throw new BusinessException.BadRequestException("Event college details are missing.");
            }
            targetUsers = userRepository.findNotificationEligibleUsersByCollegeId(collegeId);
        }

        // Asynchronous broadcast execution
        taskExecutor.execute(() -> {
            log.info("Broadcasting event announcement for event {} to {} users...", event.getId(), targetUsers.size());
            String title = "📢 Announcement: " + event.getName();
            String body = "New announcement for event: " + event.getName() + ". Check it out!";
            
            Map<String, String> data = new HashMap<>();
            data.put("type", "EVENT_ANNOUNCEMENT");
            data.put("eventId", event.getId());
            data.put("deepLink", "cax://events/" + event.getId());

            int successCount = 0;
            for (User user : targetUsers) {
                // Skip the sender
                if (user.getUserId().equals(userId)) {
                    continue;
                }
                try {
                    notificationService.createNotification(
                            user.getUserId(),
                            title,
                            body,
                            NotificationType.EVENT,
                            data
                    );
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to send announcement notification to user: {}", user.getUserId(), e);
                }
            }
            log.info("Finished broadcasting event announcement. Successfully sent: {}/{}", successCount, targetUsers.size());
        });
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

        Map<String, Object> details = new HashMap<>();
        details.put("participant", participant);
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
