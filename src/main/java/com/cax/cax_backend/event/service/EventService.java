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

import com.cax.cax_backend.event.model.EventCollaborator;
import com.cax.cax_backend.event.payment.RazorpayService;
import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.organization.service.OrganizationService;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.event.event.EventCreatedEvent;
import com.cax.cax_backend.event.event.EventRegistrationReviewedEvent;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.model.EventTeam;
import com.cax.cax_backend.event.repository.EventParticipantRepository;
import com.cax.cax_backend.event.repository.EventRepository;
import com.cax.cax_backend.event.repository.EventTeamRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import com.cax.cax_backend.user.repository.UserRepository;
import com.cax.cax_backend.event.model.EventMemory;
import com.cax.cax_backend.event.repository.EventMemoryRepository;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.settings.service.SystemSettingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final EventTeamRepository eventTeamRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final com.cax.cax_backend.college.repository.CollegeRepository collegeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EventMemoryRepository eventMemoryRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final Executor taskExecutor;
    private final RazorpayService razorpayService;
    private final SystemSettingService systemSettingService;
    private final com.cax.cax_backend.bulletinevent.service.BulletinEventService bulletinEventService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final EventScoreService eventScoreService;

    // ========================================================================
    // EVENT CRUD
    // ========================================================================

    private void populateCollegeDetails(Event event) {
        if (event == null) return;
        try {
            Organization organization = organizationService.getOrganizationById(event.getOrganizationId());
            event.setCollegeId(organization.getCollegeId());
            collegeRepository.findById(organization.getCollegeId()).ifPresent(c -> {
                event.setCollegeName(c.getCollegeName());
            });
        } catch (Exception e) {
            log.warn("Failed to populate college details for event: {}", event.getId(), e);
        }
    }

    private void enforceExternallyManagedConstraints(Event eventData) {
        if (!eventData.isExternallyManaged()) return;

        String url = eventData.getExternalRegistrationUrl();
        if (url == null || url.isBlank()) {
            throw new BusinessException.BadRequestException(
                    "externalRegistrationUrl is required for externally managed events.");
        }
        url = url.trim();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new BusinessException.BadRequestException(
                    "externalRegistrationUrl must start with http:// or https://");
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BusinessException.BadRequestException("externalRegistrationUrl is not a valid URL.");
            }
        } catch (java.net.URISyntaxException e) {
            throw new BusinessException.BadRequestException("externalRegistrationUrl is not a valid URL.");
        }
        eventData.setExternalRegistrationUrl(url);

        // Server-side sanitisation: externally managed events must never carry
        // CAX payment or ID-card settings regardless of what the client sent.
        eventData.setPaid(false);
        eventData.setFee(0);
        eventData.setUpiId(null);
        eventData.setUpiQrCode(null);
        eventData.setIdCardRequired(false);
        eventData.setRequiredFields(new java.util.ArrayList<>());
        // Registration happens off-platform, so team formation is not applicable.
        eventData.setParticipationType("INDIVIDUAL");
    }

    private void validatePaymentMode(Event eventData) {
        String mode = eventData.getPaymentMode();
        if (mode == null || mode.isBlank()) {
            eventData.setPaymentMode("RAZORPAY");
            return;
        }
        if (!"RAZORPAY".equals(mode) && !"MANUAL_UPI".equals(mode)) {
            throw new BusinessException.BadRequestException("paymentMode must be RAZORPAY or MANUAL_UPI.");
        }
    }

    private void validateTeamSettings(Event eventData) {
        String type = eventData.getParticipationType();
        if (type == null || type.isBlank()) {
            eventData.setParticipationType("INDIVIDUAL");
            return;
        }
        if (!"INDIVIDUAL".equals(type) && !"TEAM".equals(type) && !"BOTH".equals(type)) {
            throw new BusinessException.BadRequestException(
                    "participationType must be INDIVIDUAL, TEAM, or BOTH.");
        }
        if ("INDIVIDUAL".equals(type)) return;

        if (eventData.getMinTeamSize() < 2) {
            throw new BusinessException.BadRequestException("Minimum team size must be at least 2.");
        }
        if (eventData.getMaxTeamSize() < eventData.getMinTeamSize()) {
            throw new BusinessException.BadRequestException(
                    "Maximum team size cannot be smaller than the minimum team size.");
        }
        if (eventData.getMaxTeamSize() > 50) {
            throw new BusinessException.BadRequestException("Maximum team size cannot exceed 50.");
        }
        String feeType = eventData.getTeamFeeType();
        if (feeType == null || feeType.isBlank()) {
            eventData.setTeamFeeType("PER_PERSON");
        } else if (!"PER_PERSON".equals(feeType) && !"PER_TEAM".equals(feeType)) {
            throw new BusinessException.BadRequestException("teamFeeType must be PER_PERSON or PER_TEAM.");
        }
    }

    public Event createEvent(String userId, String organizationId, Event eventData) {
        if (eventData.getIdempotencyKey() != null && !eventData.getIdempotencyKey().isEmpty()) {
            Optional<Event> existing = eventRepository.findByIdempotencyKey(eventData.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Found existing event with idempotency key: {}", eventData.getIdempotencyKey());
                return existing.get();
            }
        }

        Organization organization = organizationService.getOrganizationById(organizationId);
        verifyClubLeader(userId, organization);

        if (eventData.getEventImages() != null && eventData.getEventImages().size() > 9) {
            throw new BusinessException.BadRequestException("An event can have at most 9 event-related images.");
        }

        if (eventData.getCoordinators() != null && eventData.getCoordinators().size() > 4) {
            throw new BusinessException.BadRequestException("An event can have at most 4 coordinators.");
        }

        validatePaymentMode(eventData);
        if (eventData.isPaid() && "RAZORPAY".equals(eventData.getPaymentMode())) {
            if (!systemSettingService.isRazorpayEnabled()) {
                throw new BusinessException.BadRequestException("Razorpay payment gateway is currently disabled by admin.");
            }
        }

        enforceExternallyManagedConstraints(eventData);
        validateTeamSettings(eventData);

        eventData.setOrganizationId(organizationId);
        eventData.setCollegeId(organization.getCollegeId());
        collegeRepository.findById(organization.getCollegeId()).ifPresent(c -> {
            eventData.setCollegeName(c.getCollegeName());
        });
        eventData.setCreatedByUserId(userId);
        eventData.setStatus("ACTIVE");
        eventData.setCreatedAt(Instant.now());
        eventData.setUpdatedAt(Instant.now());

        Event saved;
        try {
            saved = eventRepository.save(eventData);
        } catch (DuplicateKeyException e) {
            if (eventData.getIdempotencyKey() != null && !eventData.getIdempotencyKey().isEmpty()) {
                log.warn("Duplicate key exception caught for idempotency key: {}. Attempting to retrieve existing event.", eventData.getIdempotencyKey());
                return eventRepository.findByIdempotencyKey(eventData.getIdempotencyKey())
                        .orElseThrow(() -> e);
            }
            throw e;
        }

        // Ranking score starts at 0, or inherits a decayed share of this organization's best past events.
        eventScoreService.initializeScore(saved.getId(), organizationId, saved.getEventEndDate());

        // Resolve collaboratorIds supplied at creation time
        if (eventData.getCollaboratorIds() != null && !eventData.getCollaboratorIds().isEmpty()) {
            boolean changed = false;
            for (String collabOrgId : eventData.getCollaboratorIds()) {
                if (collabOrgId.equals(organizationId)) continue;
                boolean alreadyAdded = saved.getCollaborators().stream()
                        .anyMatch(c -> c.getOrganizationId().equals(collabOrgId));
                if (alreadyAdded) continue;
                try {
                    Organization collabOrg = organizationService.getOrganizationById(collabOrgId);
                    if (!collabOrg.getCollegeId().equals(organization.getCollegeId())) continue;
                    saved.getCollaborators().add(EventCollaborator.builder()
                            .organizationId(collabOrg.getId())
                            .organizationName(collabOrg.getName())
                            .organizationLogo(collabOrg.getLogo() != null ? collabOrg.getLogo() : "")
                            .collegeId(collabOrg.getCollegeId())
                            .build());
                    changed = true;
                } catch (Exception e) {
                    log.warn("Skipping collaborator {} during event creation: {}", collabOrgId, e.getMessage());
                }
            }
            if (changed) {
                saved = eventRepository.save(saved);
            }
        }

        try {
            eventPublisher.publishEvent(new EventCreatedEvent(this, saved));
        } catch (Exception e) {
            log.error("Failed to publish EventCreatedEvent for event: {}", saved.getId(), e);
        }

        log.info("Event '{}' created by user {} in organization {}", saved.getName(), userId, organizationId);
        return saved;
    }

    public Event updateEvent(String userId, String eventId, Event eventData) {
        Event event = getEventById(eventId);
        Organization organization = organizationService.getOrganizationById(event.getOrganizationId());
        verifyClubLeader(userId, organization);

        // Reject changes to locked fields — these cannot be changed after creation
        if (event.isGlobal() != eventData.isGlobal()) {
            throw new BusinessException.BadRequestException(
                    "Event visibility (global/college-level) cannot be changed after creation.");
        }
        if (event.isExternallyManaged() != eventData.isExternallyManaged()) {
            throw new BusinessException.BadRequestException(
                    "Externally Managed setting cannot be changed after creation.");
        }
        if (event.isPaid() != eventData.isPaid()) {
            throw new BusinessException.BadRequestException(
                    "Payment type (paid/free) cannot be changed after creation.");
        }
        // isGlobal, isExternallyManaged, and isPaid are locked (rejected above),
        // so this update never transitions between those modes — it only refreshes
        // the fields each mode allows.
        if (event.isExternallyManaged()) {
            enforceExternallyManagedConstraints(eventData);
            event.setExternalRegistrationUrl(eventData.getExternalRegistrationUrl());
            // Externally managed events must not carry CAX payment data.
            event.setPaid(false);
            event.setFee(0);
            event.setUpiId(null);
            event.setUpiQrCode(null);
            event.setIdCardRequired(false);
            event.setRequiredFields(new java.util.ArrayList<>());
        }

        // Update allowed fields
        if (eventData.getName() != null) event.setName(eventData.getName());
        if (eventData.getDescription() != null) event.setDescription(eventData.getDescription());
        if (eventData.getLogo() != null) event.setLogo(eventData.getLogo());
        if (eventData.getRegistrationEndDate() != null) event.setRegistrationEndDate(eventData.getRegistrationEndDate());
        if (eventData.getEventStartDate() != null) event.setEventStartDate(eventData.getEventStartDate());
        if (eventData.getEventEndDate() != null) event.setEventEndDate(eventData.getEventEndDate());

        if (!event.isExternallyManaged()) {
            event.setFee(eventData.getFee());
            event.setIdCardRequired(eventData.isIdCardRequired());
            if (eventData.getRequiredFields() != null) {
                event.setRequiredFields(eventData.getRequiredFields());
            }
            if (eventData.getUpiId() != null) event.setUpiId(eventData.getUpiId());
            if (eventData.getUpiQrCode() != null) event.setUpiQrCode(eventData.getUpiQrCode());
        }

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
        verifyEventManager(userId, event);

        event.setStatus("CANCELLED");
        event.setUpdatedAt(Instant.now());
        eventRepository.save(event);
        log.info("Event '{}' cancelled by user {}", event.getName(), userId);
    }

    public void deleteEvent(String userId, String eventId) {
        Event event = getEventById(eventId);
        verifyEventManager(userId, event);

        Instant createdAt = event.getCreatedAt();
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        long secondsElapsed = java.time.Duration.between(createdAt, Instant.now()).getSeconds();
        if (secondsElapsed > 22.5 * 60) {
            throw new BusinessException.BadRequestException("Event deletion is locked after 22.5 minutes of creation.");
        }

        if (eventParticipantRepository.existsByEventId(eventId)) {
            throw new BusinessException.BadRequestException("Cannot delete event because users have already registered or joined.");
        }

        Instant now = Instant.now();
        List<EventMemory> memories = eventMemoryRepository.findAllByEventId(eventId);
        for (EventMemory memory : memories) {
            memory.setDeleted(true);
            memory.setDeletedAt(now);
            eventMemoryRepository.save(memory);
        }

        event.setDeleted(true);
        event.setDeletedAt(now);
        eventRepository.save(event);
        log.info("Event '{}' ({}) deleted by user {}", event.getName(), eventId, userId);
    }

    // ========================================================================
    // EVENT QUERIES
    // ========================================================================

    public List<Event> getOrganizationEvents(String userId, String organizationId) {
        Organization organization = organizationService.getOrganizationById(organizationId);
        User user = userService.getUserByUserId(userId);
        boolean isAdmin = user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN;
        if (!isAdmin) {
            if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
                throw new BusinessException.BadRequestException("User has no college assigned.");
            }
            if (!organization.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
                throw new BusinessException.BadRequestException("You cannot access events from another college.");
            }
        }

        // Merge primary events and collaborative events; dedup by event ID.
        List<Event> primaryEvents = eventRepository.findByOrganizationId(organizationId);
        List<Event> collabEvents  = eventRepository.findByCollaboratingOrganizationId(organizationId);

        java.util.LinkedHashMap<String, Event> deduped = new java.util.LinkedHashMap<>();
        for (Event e : primaryEvents) deduped.put(e.getId(), e);
        for (Event e : collabEvents)  deduped.putIfAbsent(e.getId(), e);

        List<Event> result = new java.util.ArrayList<>(deduped.values());
        populateJoinedCount(result);
        return result;
    }

    private void populateJoinedCount(Event event) {
        if (event != null) {
            long count = eventParticipantRepository.countByEventId(event.getId());
            event.setJoinedCount(count);
        }
    }

    private void populateJoinedCount(List<Event> events) {
        if (events != null) {
            for (Event event : events) {
                populateJoinedCount(event);
            }
        }
    }

    /** Recomputes an event's ranking score from its current participant count. Call whenever registrations change. */
    public void refreshEventScore(String eventId) {
        long count = eventParticipantRepository.countByEventId(eventId);
        eventScoreService.recalculate(eventId, count);
    }

    public Event getEventById(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Event", eventId));
        if (event.isDeleted()) {
            throw new BusinessException.ResourceNotFoundException("Event", eventId);
        }
        populateCollegeDetails(event);
        populateJoinedCount(event);
        return event;
    }

    public Map<String, Object> getEventDetailForUser(String userId, String eventId) {
        Event event = getEventById(eventId);
        Organization organization = organizationService.getOrganizationById(event.getOrganizationId());
        User user = userService.getUserByUserId(userId);
        
        boolean isSystemAdmin = user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN;
        if (!isSystemAdmin && !event.isGlobal()) {
            if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
                throw new BusinessException.BadRequestException("User has no college assigned.");
            }
            if (!organization.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
                throw new BusinessException.BadRequestException("You cannot access an event from another college.");
            }
        }

        // Check participant status
        Optional<EventParticipant> participant = eventParticipantRepository.findFirstByEventIdAndUserId(eventId, userId);

        // Determine organizer role for this user
        String organizerRole = null;
        if (userId.equals(event.getCreatedByUserId())) {
            organizerRole = "organizer";
        } else if (isSystemAdmin) {
            organizerRole = "admin";
        } else {
            try {
                if (organizationService.isOrganizationLeaderOrManager(userId, organization.getId())) {
                    organizerRole = "club_leader";
                }
            } catch (Exception e) {
                log.warn("Could not determine club role for user {} on event {}", userId, eventId, e);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("event", event);
        result.put("organizerRole", organizerRole);

        String status = participant.map(EventParticipant::getStatus).orElse(null);
        if (status != null && "REJECTED".equals(status) && !event.isPaid()) {
            status = "REGISTRATION_REJECTED";
        }
        result.put("participantStatus", status);

        // The caller's own registration record (ticket, team, payment state) so
        // non-manager participants can render their ticket without the
        // manager-only participants endpoint.
        participant.ifPresent(p -> result.put("myParticipant", decryptParticipant(p)));

        // Include the user's team (with member summaries) so the detail screen
        // renders the "My Team" card without a second round trip.
        participant.filter(p -> p.getTeamId() != null).ifPresent(p ->
                eventTeamRepository.findById(p.getTeamId()).ifPresent(team ->
                        result.put("myTeam", buildTeamPayload(event, team))));

        return result;
    }

    /** Team document plus a lightweight member list, as sent to clients. */
    Map<String, Object> buildTeamPayload(Event event, EventTeam team) {
        List<EventParticipant> members = eventParticipantRepository
                .findByEventIdAndTeamId(event.getId(), team.getId());
        List<Map<String, Object>> memberSummaries = members.stream()
                .sorted(java.util.Comparator.comparing(EventParticipant::isTeamLeader).reversed())
                .map(m -> {
                    Map<String, Object> s = new HashMap<>();
                    s.put("participantId", m.getId());
                    s.put("userId", m.getUserId());
                    s.put("name", decryptParticipant(m).getName());
                    s.put("picture", m.getPicture());
                    s.put("status", m.getStatus());
                    s.put("checkedIn", m.isCheckedIn());
                    s.put("isTeamLeader", m.isTeamLeader());
                    return s;
                })
                .collect(Collectors.toList());

        Map<String, Object> payload = new HashMap<>();
        payload.put("team", team);
        payload.put("members", memberSummaries);
        payload.put("minTeamSize", event.getMinTeamSize());
        payload.put("maxTeamSize", event.getMaxTeamSize());
        return payload;
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
        List<Event> result = filtered.subList(fromIndex, toIndex);
        populateJoinedCount(result);
        return result;
    }

    /**
     * Merged, filtered, paged feed of regular events + bulletin events, soonest-first.
     * {@code filter} is one of "upcoming" (default), "ongoing", "completed", "all".
     * Each entry in the returned "content" list is {@code {type: "EVENT"|"BULLETIN", data: {...}}},
     * matching the shape the home dashboard already returns so client-side parsing is shared.
     */
    public Map<String, Object> getEventsFeed(String userId, String filter, int page, int size) {
        Instant now = Instant.now();

        final String userCollegeId;
        if (userId != null && !userId.isBlank()) {
            User user = userService.getUserByUserId(userId);
            userCollegeId = (user.getCollegeDetails() != null) ? user.getCollegeDetails().getCollegeId() : null;
        } else {
            userCollegeId = null;
        }
        String cid = (userCollegeId == null) ? "" : userCollegeId;

        List<Event> events;
        List<com.cax.cax_backend.bulletinevent.model.BulletinEvent> bulletins;
        switch (filter == null ? "upcoming" : filter) {
            case "ongoing":
                events = eventRepository.findOngoingEvents("ACTIVE", now, cid);
                bulletins = bulletinEventService.getOngoingBulletinEvents(cid, now);
                break;
            case "completed":
                events = eventRepository.findCompletedEvents("ACTIVE", now, cid);
                bulletins = bulletinEventService.getCompletedBulletinEvents(cid, now);
                break;
            case "all":
                events = eventRepository.findAllScopedEvents("ACTIVE", cid);
                bulletins = bulletinEventService.getBulletinEventsForUser(cid);
                break;
            case "upcoming":
            default:
                events = eventRepository.findUpcomingEvents("ACTIVE", now, cid);
                bulletins = bulletinEventService.getUpcomingBulletinEvents(cid, now);
                break;
        }
        populateJoinedCount(events);

        // College-level events are always shown first (their own tier, soonest-first);
        // global events and bulletins are mixed together after, also soonest-first.
        List<Map<String, Object>> collegeTier = new java.util.ArrayList<>();
        List<Map<String, Object>> globalTier = new java.util.ArrayList<>();
        for (Event event : events) {
            (event.isGlobal() ? globalTier : collegeTier).add(toTaggedMap(event, "EVENT"));
        }
        for (com.cax.cax_backend.bulletinevent.model.BulletinEvent bulletin : bulletins) {
            globalTier.add(toTaggedMap(bulletin, "BULLETIN"));
        }
        java.util.Comparator<Map<String, Object>> byStartDate = java.util.Comparator.comparing(
                m -> (String) ((Map<String, Object>) m.get("data")).get("eventStartDate"));
        collegeTier.sort(byStartDate);
        globalTier.sort(byStartDate);

        List<Map<String, Object>> tagged = new java.util.ArrayList<>(collegeTier);
        tagged.addAll(globalTier);

        int total = tagged.size();
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        int fromIndex = page * size;
        List<Map<String, Object>> content = (fromIndex >= total || size <= 0)
                ? new java.util.ArrayList<>()
                : tagged.subList(fromIndex, Math.min(fromIndex + size, total));

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", total);
        result.put("totalPages", totalPages);
        result.put("last", (page + 1) >= totalPages);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toTaggedMap(Object source, String type) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("type", type);
        item.put("data", objectMapper.convertValue(source, Map.class));
        return item;
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

        List<Event> events = eventRepository.findAllById(eventIds).stream()
                .filter(e -> !e.isDeleted())
                .collect(Collectors.toList());
        populateJoinedCount(events);
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

        if (event.isExternallyManaged()) {
            throw new BusinessException.BadRequestException(
                    "This event is managed externally. Please register via the provided external link.");
        }

        if ("TEAM".equals(event.getParticipationType())) {
            throw new BusinessException.BadRequestException(
                    "This event only accepts team registrations. Create a team or join one with a team code.");
        }

        EventParticipant participant = buildParticipant(userId, event, idCardDetails);

        try {
            EventParticipant saved = eventParticipantRepository.save(participant);
            refreshEventScore(eventId);
            return decryptParticipant(saved);
        } catch (DuplicateKeyException e) {
            // Two concurrent requests both passed the existsBy check above; the unique
            // (eventId, userId) index rejected the second insert. Surface a friendly
            // error instead of a raw 500 rather than leaving a duplicate record behind.
            throw new BusinessException.BadRequestException("You are already registered for this event.");
        }
    }

    /**
     * Runs every registration-time check (event open, not already registered,
     * college restriction, required fields) and returns an unsaved participant.
     * Shared by individual registration and team create/join flows.
     */
    EventParticipant buildParticipant(String userId, Event event, Map<String, Object> idCardDetails) {
        String eventId = event.getId();

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

        // Mirror of the mobile gate: unverified accounts cannot register.
        // Enforced here too so a raw API call can't bypass the client check.
        boolean privileged = user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN
                || user.getRole() == com.cax.cax_backend.common.enums.UserRole.SUPER_STUDENT;
        if (!user.isIdVerified() && !privileged) {
            throw new BusinessException.BadRequestException(
                    "Your account must be verified before registering for events.");
        }

        // Non-global events are restricted to the same college
        if (!event.isGlobal()) {
            Organization organization = organizationService.getOrganizationById(event.getOrganizationId());
            if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
                throw new BusinessException.BadRequestException("User has no college assigned.");
            }
            if (!organization.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
                throw new BusinessException.BadRequestException("You can only register for events at your own college.");
            }
        }

        // Collect & validate ID card details when the event requires them (legacy compatibility)
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

        // Collect & validate dynamic required fields
        String gender = null;
        String dateOfBirth = null;
        String phoneNumber = null;
        String collegeNameField = null;
        String departmentField = null;
        String registerNumber = null;
        String yearOfStudy = null;

        if (event.getRequiredFields() != null) {
            for (String field : event.getRequiredFields()) {
                Object val = idCardDetails != null ? idCardDetails.get(field) : null;
                String trimmed = trimToNull(val);
                if (trimmed == null) {
                    throw new BusinessException.BadRequestException(field + " is required to register for this event.");
                }
                switch (field) {
                    case "gender": gender = trimmed; break;
                    case "dateOfBirth": dateOfBirth = trimmed; break;
                    case "phoneNumber": phoneNumber = trimmed; break;
                    case "collegeName": collegeNameField = trimmed; break;
                    case "department": departmentField = trimmed; break;
                    case "registerNumber": registerNumber = trimmed; break;
                    case "yearOfStudy": yearOfStudy = trimmed; break;
                }
            }
        }

        String collegeName = null;
        String userCollegeId = null;
        if (user.getCollegeDetails() != null) {
            collegeName = user.getCollegeDetails().getCollegeName();
            userCollegeId = user.getCollegeDetails().getCollegeId();
        }

        // Custom college name overrides user's default college name if requested
        if (collegeNameField != null) {
            collegeName = collegeNameField;
        }

        String initialStatus = event.isPaid() ? "PENDING_PAYMENT" : "PENDING_APPROVAL";

        return EventParticipant.builder()
                .eventId(eventId)
                .userId(userId)
                .name(user.getName())
                .email(user.getEmail())
                .picture(user.getPicture())
                .college(collegeName)
                .collegeId(userCollegeId)
                .idCardNumber(idCardNumber != null ? com.cax.cax_backend.common.util.EncryptionUtils.encrypt(idCardNumber) : null)
                .idCardName(idCardName)
                .idCardDepartment(idCardDepartment)
                .gender(gender)
                .dateOfBirth(dateOfBirth)
                .phoneNumber(phoneNumber)
                .collegeName(collegeNameField)
                .department(departmentField)
                .registerNumber(registerNumber)
                .yearOfStudy(yearOfStudy)
                .status(initialStatus)
                .registeredAt(Instant.now())
                .build();
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
        if (event.isExternallyManaged()) {
            throw new BusinessException.BadRequestException(
                    "Payment cannot be submitted for externally managed events.");
        }
        if (!event.isPaid()) {
            throw new BusinessException.BadRequestException("This is a free event, payment is not required.");
        }

        EventParticipant participant = eventParticipantRepository.findFirstByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BusinessException.BadRequestException("You are not registered for this event."));

        if (isTeamFeeButNotLeader(event, participant)) {
            throw new BusinessException.BadRequestException(
                    "The team fee for this event is paid once by your team leader.");
        }

        if (!"PENDING_PAYMENT".equals(participant.getStatus()) && !"REJECTED".equals(participant.getStatus())) {
            throw new BusinessException.BadRequestException("Payment submission is not allowed in current status: " + participant.getStatus());
        }

        // The amount is echoed by the client for the audit trail, but the fee the
        // participant owes is the event's — never trust the client's number.
        if (Math.abs(amount - event.getFee()) > 0.009) {
            throw new BusinessException.BadRequestException(
                    "Submitted amount (₹" + amount + ") does not match the event fee (₹" + event.getFee() + ").");
        }

        if (utrNumber == null || !utrNumber.trim().matches("\\d{12}")) {
            throw new BusinessException.BadRequestException(
                    "UTR number must be exactly 12 digits. Check the transaction reference in your UPI app.");
        }
        utrNumber = utrNumber.trim();

        participant.setUtrNumber(com.cax.cax_backend.common.util.EncryptionUtils.encrypt(utrNumber));
        participant.setPaymentScreenshot(screenshotUrl);
        participant.setAmountPaid(amount);
        participant.setStatus("PAYMENT_SUBMITTED");

        if (participant.getPaymentHistory() == null) {
            participant.setPaymentHistory(new java.util.ArrayList<>());
        }
        participant.getPaymentHistory().add(EventParticipant.PaymentHistoryEntry.builder()
                .status("PAYMENT_SUBMITTED")
                .utrNumber(participant.getUtrNumber())
                .paymentScreenshot(participant.getPaymentScreenshot())
                .amountPaid(participant.getAmountPaid())
                .timestamp(Instant.now())
                .build());

        return decryptParticipant(eventParticipantRepository.save(participant));
    }

    public void cancelRegistration(String userId, String eventId) {
        Event event = getEventById(eventId);
        if (event.isExternallyManaged()) {
            throw new BusinessException.BadRequestException("Registration cannot be cancelled for externally managed events.");
        }

        EventParticipant participant = eventParticipantRepository.findFirstByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("EventParticipant for user " + userId + " on event " + eventId + " not found"));

        if (participant.getTeamId() != null) {
            throw new BusinessException.BadRequestException("Cannot cancel registration because you are part of a team. Please leave the team instead.");
        }

        String status = participant.getStatus();
        if (!"PENDING_PAYMENT".equals(status) && !"PENDING_APPROVAL".equals(status) && !"REJECTED".equals(status)) {
            throw new BusinessException.BadRequestException("Cannot cancel registration in current status: " + status);
        }

        eventParticipantRepository.delete(participant);
        refreshEventScore(eventId);
    }


    public EventParticipant verifyPayment(String organizerId, String eventId, String participantId, boolean approved) {
        Event event = getEventById(eventId);
        verifyEventManager(organizerId, event);
        if (event.isExternallyManaged()) {
            throw new BusinessException.BadRequestException(
                    "Payment verification is not applicable for externally managed events.");
        }

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

        // Team members only receive tickets once their team is COMPLETE (handled
        // by recomputeTeamCompletion below); individuals get theirs immediately.
        if (approved && participant.getTeamId() == null
                && (participant.getTicketCode() == null || participant.getTicketCode().isEmpty())) {
            participant.setTicketCode(newTicketCode());
        }
        participant.setStatus(approved ? "VERIFIED" : "REJECTED");
        participant.setVerifiedByUserId(organizerId);
        participant.setVerifiedAt(Instant.now());

        // Capture verifier identity for manage-screen traceability
        try {
            User verifier = userService.getUserByUserId(organizerId);
            participant.setVerifiedByName(verifier.getName());
            String actingOrgId = findActorOrgId(organizerId, event);
            participant.setVerifiedByOrganizationId(actingOrgId);
            Organization actingOrg = organizationService.getOrganizationById(actingOrgId);
            participant.setVerifiedByOrganizationName(actingOrg.getName());
        } catch (Exception e) {
            log.warn("Could not capture verifier identity for participant {}", participantId, e);
        }

        if (participant.getPaymentHistory() == null) {
            participant.setPaymentHistory(new java.util.ArrayList<>());
        }
        participant.getPaymentHistory().add(EventParticipant.PaymentHistoryEntry.builder()
                .status(approved ? "VERIFIED" : "REJECTED")
                .utrNumber(participant.getUtrNumber())
                .paymentScreenshot(participant.getPaymentScreenshot())
                .amountPaid(participant.getAmountPaid())
                .timestamp(Instant.now())
                .verifiedByUserId(organizerId)
                .verifiedByName(participant.getVerifiedByName())
                .verifiedByOrganizationId(participant.getVerifiedByOrganizationId())
                .verifiedByOrganizationName(participant.getVerifiedByOrganizationName())
                .build());

        EventParticipant saved = eventParticipantRepository.save(participant);
        try {
            eventPublisher.publishEvent(new EventRegistrationReviewedEvent(this, saved, event));
        } catch (Exception e) {
            log.error("Failed to publish EventRegistrationReviewedEvent for participant: {}", participantId, e);
        }

        if (saved.getTeamId() != null) {
            saved = applyTeamEffectsAfterVerification(event, saved, approved);
        }
        return decryptParticipant(saved);
    }

    // ========================================================================
    // TEAM SUPPORT
    // ========================================================================

    String newTicketCode() {
        return "CAX-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** True when the event charges one fee per team and this member is not the leader. */
    private boolean isTeamFeeButNotLeader(Event event, EventParticipant participant) {
        return participant.getTeamId() != null
                && "PER_TEAM".equals(event.getTeamFeeType())
                && !participant.isTeamLeader();
    }

    /**
     * Runs after a team member's status changed to VERIFIED/REJECTED:
     * cascades a PER_TEAM leader verification to all teammates, then re-evaluates
     * team completion and issues tickets. Returns the participant re-read from
     * the DB so a ticket assigned during the recompute is reflected.
     */
    private EventParticipant applyTeamEffectsAfterVerification(
            Event event, EventParticipant saved, boolean approved) {
        Optional<EventTeam> teamOpt = eventTeamRepository.findById(saved.getTeamId());
        if (teamOpt.isEmpty()) {
            log.warn("Participant {} references missing team {}", saved.getId(), saved.getTeamId());
            return saved;
        }
        EventTeam team = teamOpt.get();

        if (approved && "PER_TEAM".equals(event.getTeamFeeType()) && saved.isTeamLeader()) {
            cascadeTeamPaymentVerification(event, team, saved);
        }

        recomputeTeamCompletion(event, team);
        return eventParticipantRepository.findById(saved.getId()).orElse(saved);
    }

    /**
     * The leader's PER_TEAM payment was verified: mark the team paid and verify
     * every teammate still waiting on it.
     */
    private void cascadeTeamPaymentVerification(Event event, EventTeam team, EventParticipant leader) {
        if (!"VERIFIED".equals(team.getPaymentStatus())) {
            team.setPaymentStatus("VERIFIED");
            team.setUpdatedAt(Instant.now());
            eventTeamRepository.save(team);
        }

        List<EventParticipant> members = eventParticipantRepository.findByEventIdAndTeamId(event.getId(), team.getId());
        for (EventParticipant member : members) {
            if (member.getId().equals(leader.getId())) continue;
            String s = member.getStatus();
            if (!"PENDING_PAYMENT".equals(s) && !"PAYMENT_SUBMITTED".equals(s) && !"PENDING_APPROVAL".equals(s)) {
                continue;
            }
            member.setStatus("VERIFIED");
            member.setVerifiedByUserId(leader.getVerifiedByUserId());
            member.setVerifiedByName(leader.getVerifiedByName());
            member.setVerifiedByOrganizationId(leader.getVerifiedByOrganizationId());
            member.setVerifiedByOrganizationName(leader.getVerifiedByOrganizationName());
            member.setVerifiedAt(Instant.now());
            if (member.getPaymentHistory() == null) {
                member.setPaymentHistory(new java.util.ArrayList<>());
            }
            member.getPaymentHistory().add(EventParticipant.PaymentHistoryEntry.builder()
                    .status("VERIFIED")
                    .timestamp(Instant.now())
                    .verifiedByUserId(leader.getVerifiedByUserId())
                    .verifiedByName(leader.getVerifiedByName())
                    .verifiedByOrganizationId(leader.getVerifiedByOrganizationId())
                    .verifiedByOrganizationName(leader.getVerifiedByOrganizationName())
                    .build());
            eventParticipantRepository.save(member);
            try {
                eventPublisher.publishEvent(new EventRegistrationReviewedEvent(this, member, event));
            } catch (Exception e) {
                log.error("Failed to publish review event for team member {}", member.getId(), e);
            }
        }
    }

    /**
     * Promotes a FORMING team to COMPLETE once it has minTeamSize non-rejected
     * members, and issues tickets to every VERIFIED member of a COMPLETE team
     * that doesn't have one yet (tickets are held back until completion).
     * Also demotes a COMPLETE team back to FORMING if rejections or removals
     * drop it below the minimum — unless someone is already checked in, at
     * which point the roster is the organizers' problem, not the state machine's.
     */
    void recomputeTeamCompletion(Event event, EventTeam team) {
        List<EventParticipant> members = eventParticipantRepository.findByEventIdAndTeamId(event.getId(), team.getId());
        long activeCount = members.stream().filter(m -> !"REJECTED".equals(m.getStatus())).count();

        if ("FORMING".equals(team.getStatus()) && activeCount >= event.getMinTeamSize()) {
            team.setStatus("COMPLETE");
            team.setUpdatedAt(Instant.now());
            team = eventTeamRepository.save(team);
        } else if ("COMPLETE".equals(team.getStatus())
                && activeCount < event.getMinTeamSize()
                && members.stream().noneMatch(EventParticipant::isCheckedIn)) {
            team.setStatus("FORMING");
            team.setUpdatedAt(Instant.now());
            team = eventTeamRepository.save(team);
        }

        if (!"COMPLETE".equals(team.getStatus())) return;

        for (EventParticipant member : members) {
            if ("VERIFIED".equals(member.getStatus())
                    && (member.getTicketCode() == null || member.getTicketCode().isEmpty())) {
                member.setTicketCode(newTicketCode());
                eventParticipantRepository.save(member);
            }
        }
    }

    public List<EventParticipant> getParticipants(String userId, String eventId) {
        Event event = getEventById(eventId);
        verifyEventManager(userId, event);

        List<EventParticipant> participants = eventParticipantRepository.findByEventId(eventId);
        participants.forEach(this::decryptParticipant);
        return participants;
    }

    public byte[] exportParticipantsToCsv(String userId, String eventId) {
        List<EventParticipant> participants = getParticipants(userId, eventId);

        StringBuilder csv = new StringBuilder();
        // CSV Header
        csv.append("Registration ID,Participant Name,Email Address,College Affiliation,ID Card Number,Name On ID Card,Department,Gender,Date of Birth,Phone Number,Custom College Name,Custom Department,Register Number,Year of Study,Registration Date,Ticket Status,Ticket Code,Amount Paid (INR),UTR Number,Checked In,Checked In Time,Suspicious,Suspicious Note,Team Name,Team Role\n");

        // Teams export grouped together, individuals after.
        participants = participants.stream()
                .sorted(java.util.Comparator.comparing(
                        p -> p.getTeamName() == null ? "￿" : p.getTeamName()))
                .collect(Collectors.toList());

        for (EventParticipant p : participants) {
            csv.append(escapeCsv(p.getId())).append(",")
               .append(escapeCsv(p.getName())).append(",")
               .append(escapeCsv(p.getEmail())).append(",")
               .append(escapeCsv(p.getCollege())).append(",")
               .append(escapeCsv(p.getIdCardNumber())).append(",")
               .append(escapeCsv(p.getIdCardName())).append(",")
               .append(escapeCsv(p.getIdCardDepartment())).append(",")
               .append(escapeCsv(p.getGender())).append(",")
               .append(escapeCsv(p.getDateOfBirth())).append(",")
               .append(escapeCsv(p.getPhoneNumber())).append(",")
               .append(escapeCsv(p.getCollegeName())).append(",")
               .append(escapeCsv(p.getDepartment())).append(",")
               .append(escapeCsv(p.getRegisterNumber())).append(",")
               .append(escapeCsv(p.getYearOfStudy())).append(",")
               .append(escapeCsv(p.getRegisteredAt() != null ? p.getRegisteredAt().toString() : "")).append(",")
               .append(escapeCsv(p.getStatus())).append(",")
               .append(escapeCsv(p.getTicketCode())).append(",")
               .append(p.getAmountPaid()).append(",")
               .append(escapeCsv(p.getUtrNumber())).append(",")
               .append(p.isCheckedIn() ? "Yes" : "No").append(",")
               .append(escapeCsv(p.getCheckedInAt() != null ? p.getCheckedInAt().toString() : "")).append(",")
               .append(p.isSuspicious() ? "Yes" : "No").append(",")
               .append(escapeCsv(p.getSuspiciousNote())).append(",")
               .append(escapeCsv(p.getTeamName())).append(",")
               .append(p.getTeamId() == null ? "" : (p.isTeamLeader() ? "Leader" : "Member")).append("\n");
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

    private void verifyClubLeader(String userId, Organization organization) {
        if (organizationService.isOrganizationLeaderOrManager(userId, organization.getId())) {
            return;
        }
        throw new BusinessException.BadRequestException("Only the President, Vice President, or Club Managers can perform this action.");
    }

    public void verifyEventManager(String userId, Event event) {
        if (userId.equals(event.getCreatedByUserId())) {
            return;
        }
        Organization organization = organizationService.getOrganizationById(event.getOrganizationId());
        if (organizationService.isOrganizationLeaderOrManager(userId, organization.getId())) {
            return;
        }
        User user = userService.getUserByUserId(userId);
        if (user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN) {
            return;
        }
        throw new BusinessException.BadRequestException(
                "Only the event organizer or the organization's leaders can manage this event.");
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
            String title = "Announcement: " + event.getName();
            String body = "A new announcement has been posted for the event: " + event.getName() + ".";
            
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
        Event event = getEventById(eventId);
        verifyEventManager(organizerId, event);
        if (event.isExternallyManaged()) {
            throw new BusinessException.BadRequestException(
                    "Check-in is not applicable for externally managed events.");
        }

        if ("CANCELLED".equals(event.getStatus())) {
            throw new BusinessException.BadRequestException(
                    "This event has been cancelled — check-in is disabled.");
        }
        if ("COMPLETED".equals(event.getStatus())) {
            throw new BusinessException.BadRequestException(
                    "This event is completed — check-in is closed.");
        }

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

        // Capture check-in operator identity for traceability across collaborating orgs
        try {
            User checker = userService.getUserByUserId(organizerId);
            participant.setCheckedInByUserId(organizerId);
            participant.setCheckedInByName(checker.getName());
            String actingOrgId = findActorOrgId(organizerId, event);
            participant.setCheckedInByOrganizationId(actingOrgId);
            Organization actingOrg = organizationService.getOrganizationById(actingOrgId);
            participant.setCheckedInByOrganizationName(actingOrg.getName());
        } catch (Exception e) {
            log.warn("Could not capture check-in operator identity for event {}", eventId, e);
        }

        return decryptParticipant(eventParticipantRepository.save(participant));
    }

    public Map<String, Object> getParticipantDetailsByCode(String callerId, String eventId, String ticketCode) {
        EventParticipant participant = eventParticipantRepository.findByEventIdAndTicketCode(eventId, ticketCode)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Ticket code not found for this event: " + ticketCode));

        // Only the ticket owner or an event manager can view ticket details
        boolean isOwner = callerId.equals(participant.getUserId());
        boolean isManager = hasEventManagePermission(callerId, getEventById(eventId));
        if (!isOwner && !isManager) {
            throw new BusinessException.BadRequestException("You do not have permission to view this ticket.");
        }

        Map<String, Object> details = new HashMap<>();
        details.put("participant", decryptParticipant(participant));
        return details;
    }

    public EventParticipant toggleSuspicious(String organizerId, String eventId, String participantId, boolean suspicious, String note) {
        Event event = getEventById(eventId);
        verifyEventManager(organizerId, event);
        if (event.isExternallyManaged()) {
            throw new BusinessException.BadRequestException(
                    "Participant management is not applicable for externally managed events.");
        }

        EventParticipant participant = eventParticipantRepository.findById(participantId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("EventParticipant", participantId));

        if (!participant.getEventId().equals(eventId)) {
            throw new BusinessException.BadRequestException("Participant does not belong to this event.");
        }

        participant.setSuspicious(suspicious);
        participant.setSuspiciousNote(note);

        return decryptParticipant(eventParticipantRepository.save(participant));
    }

    public boolean hasEventManagePermission(String userId, Event event) {
        if (userId.equals(event.getCreatedByUserId())) {
            return true;
        }
        if (organizationService.isOrganizationLeaderOrManager(userId, event.getOrganizationId())) {
            return true;
        }
        return false;
    }

    // ========================================================================
    // COLLABORATION MANAGEMENT
    // ========================================================================

    /** Adds an organization to the event's collaborators list. Only the primary org leader can do this. */
    public Event addCollaborator(String userId, String eventId, String collaboratingOrgId) {
        Event event = getEventById(eventId);

        if (!organizationService.isOrganizationLeaderOrManager(userId, event.getOrganizationId())) {
            User user = userService.getUserByUserId(userId);
            if (user.getRole() != com.cax.cax_backend.common.enums.UserRole.ADMIN) {
                throw new BusinessException.BadRequestException(
                        "Only the primary organization leader can add collaborators.");
            }
        }

        if (event.getOrganizationId().equals(collaboratingOrgId)) {
            throw new BusinessException.BadRequestException("An event cannot list itself as a collaborator.");
        }

        boolean alreadyAdded = event.getCollaborators().stream()
                .anyMatch(c -> c.getOrganizationId().equals(collaboratingOrgId));
        if (alreadyAdded) {
            throw new BusinessException.BadRequestException("This organization is already a collaborator.");
        }

        Organization collabOrg = organizationService.getOrganizationById(collaboratingOrgId);

        EventCollaborator collaborator = EventCollaborator.builder()
                .organizationId(collaboratingOrgId)
                .organizationName(collabOrg.getName())
                .organizationLogo(collabOrg.getLogo())
                .collegeId(collabOrg.getCollegeId())
                .build();

        event.getCollaborators().add(collaborator);
        event.setUpdatedAt(Instant.now());
        return eventRepository.save(event);
    }

    /** Removes a collaborating organization from the event. Only the primary org leader can do this. */
    public Event removeCollaborator(String userId, String eventId, String organizationId) {
        Event event = getEventById(eventId);

        if (!organizationService.isOrganizationLeaderOrManager(userId, event.getOrganizationId())) {
            User user = userService.getUserByUserId(userId);
            if (user.getRole() != com.cax.cax_backend.common.enums.UserRole.ADMIN) {
                throw new BusinessException.BadRequestException(
                        "Only the primary organization leader can remove collaborators from this event.");
            }
        }

        boolean removed = event.getCollaborators().removeIf(c -> c.getOrganizationId().equals(organizationId));
        if (!removed) {
            throw new BusinessException.ResourceNotFoundException("Collaboration", organizationId);
        }

        event.setUpdatedAt(Instant.now());
        Event saved = eventRepository.save(event);
        log.info("Collaborator removed: event={} org={}", eventId, organizationId);
        return saved;
    }

    /**
     * Resolves the organization the acting user belongs to for this event.
     * Tries the primary org first, then walks the accepted collaborators list.
     */
    private String findActorOrgId(String userId, Event event) {
        if (organizationService.isOrganizationLeaderOrManager(userId, event.getOrganizationId())) {
            return event.getOrganizationId();
        }
        return event.getOrganizationId();
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
                String title = "New Live Memory Shared";
                String body = "A new photo memory has been shared for the event: " + event.getName() + ".";

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
        if (memory.isDeleted()) {
            throw new BusinessException.ResourceNotFoundException("EventMemory", memoryId);
        }

        memory.setDeleted(true);
        memory.setDeletedAt(Instant.now());
        eventMemoryRepository.save(memory);
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
        if (memory.isDeleted()) {
            throw new BusinessException.ResourceNotFoundException("EventMemory", memoryId);
        }

        memory.setHidden(hidden);
        return eventMemoryRepository.save(memory);
    }

    public Page<EventMemory> getEventMemories(String userId, String eventId, int page, int size, String filter) {
        Event event = getEventById(eventId);
        boolean isManager = hasEventManagePermission(userId, event);
        
        if (!isManager) {
            Optional<EventParticipant> participantOpt = eventParticipantRepository.findFirstByEventIdAndUserId(eventId, userId);
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

    // ========================================================================
    // RAZORPAY PAYMENT
    // ========================================================================

    public Map<String, Object> initRazorpayPayment(String userId, String eventId) {
        if (!systemSettingService.isRazorpayEnabled()) {
            throw new BusinessException.BadRequestException("Razorpay payment gateway is currently disabled by admin.");
        }
        Event event = getEventById(eventId);
        if (!event.isPaid()) {
            throw new BusinessException.BadRequestException("This is a free event, payment is not required.");
        }
        if (event.isExternallyManaged()) {
            throw new BusinessException.BadRequestException("Payment not applicable for externally managed events.");
        }

        EventParticipant participant = eventParticipantRepository.findFirstByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BusinessException.BadRequestException("You are not registered for this event."));

        if (isTeamFeeButNotLeader(event, participant)) {
            throw new BusinessException.BadRequestException(
                    "The team fee for this event is paid once by your team leader.");
        }

        if (!"PENDING_PAYMENT".equals(participant.getStatus()) && !"REJECTED".equals(participant.getStatus())) {
            throw new BusinessException.BadRequestException(
                    "Razorpay payment not allowed in current status: " + participant.getStatus());
        }

        long amountInPaise = Math.round(event.getFee() * 100);
        String receipt = ("evnt_" + eventId + "_" + userId).substring(0,
                Math.min(40, 5 + eventId.length() + 1 + userId.length()));

        Map<String, Object> order = razorpayService.createOrder(amountInPaise, receipt);

        String razorpayOrderId = (String) order.get("id");
        participant.setRazorpayOrderId(razorpayOrderId);
        eventParticipantRepository.save(participant);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", razorpayOrderId);
        result.put("amount", amountInPaise);
        result.put("currency", "INR");
        result.put("keyId", razorpayService.getKeyId());
        result.put("eventName", event.getName());
        result.put("eventFee", event.getFee());
        return result;
    }

    public EventParticipant confirmRazorpayPayment(String userId, String eventId,
                                                   String razorpayPaymentId,
                                                   String razorpayOrderId,
                                                   String razorpaySignature) {
        if (!razorpayService.verifySignature(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
            throw new BusinessException.BadRequestException("Payment verification failed: invalid signature.");
        }

        Event event = getEventById(eventId);
        EventParticipant participant = eventParticipantRepository.findFirstByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BusinessException.BadRequestException("You are not registered for this event."));

        if (!"PENDING_PAYMENT".equals(participant.getStatus()) && !"REJECTED".equals(participant.getStatus())) {
            throw new BusinessException.BadRequestException(
                    "Cannot confirm payment in current status: " + participant.getStatus());
        }

        participant.setRazorpayOrderId(razorpayOrderId);
        participant.setRazorpayPaymentId(razorpayPaymentId);
        participant.setAmountPaid(event.getFee());
        participant.setStatus("VERIFIED");
        participant.setVerifiedAt(Instant.now());

        // Team members receive tickets only when their team is COMPLETE.
        if (participant.getTeamId() == null
                && (participant.getTicketCode() == null || participant.getTicketCode().isEmpty())) {
            participant.setTicketCode(newTicketCode());
        }

        if (participant.getPaymentHistory() == null) {
            participant.setPaymentHistory(new java.util.ArrayList<>());
        }
        participant.getPaymentHistory().add(EventParticipant.PaymentHistoryEntry.builder()
                .status("VERIFIED")
                .amountPaid(participant.getAmountPaid())
                .timestamp(Instant.now())
                .build());

        try {
            EventParticipant saved = eventParticipantRepository.save(participant);
            if (saved.getTeamId() != null) {
                saved = applyTeamEffectsAfterVerification(event, saved, true);
            }
            return decryptParticipant(saved);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            // Another request (e.g. a duplicate Razorpay success callback) verified this
            // same participant between our read and write. The @Version field caught it
            // before we could double-apply the payment. If that other request already
            // confirmed this exact payment, treat this call as a no-op success instead
            // of double-processing or erroring out.
            EventParticipant current = eventParticipantRepository.findFirstByEventIdAndUserId(eventId, userId)
                    .orElseThrow(() -> e);
            if ("VERIFIED".equals(current.getStatus()) && razorpayPaymentId.equals(current.getRazorpayPaymentId())) {
                return decryptParticipant(current);
            }
            throw new BusinessException.BadRequestException(
                    "This payment is already being processed. Please refresh and try again.");
        }
    }

    EventParticipant decryptParticipant(EventParticipant p) {
        if (p == null) return null;
        if (p.getName() != null) {
            try {
                p.setName(com.cax.cax_backend.common.util.EncryptionUtils.decrypt(p.getName()));
            } catch (Exception e) {
                // Ignore
            }
        }
        if (p.getIdCardNumber() != null) {
            p.setIdCardNumber(com.cax.cax_backend.common.util.EncryptionUtils.decrypt(p.getIdCardNumber()));
        }
        if (p.getUtrNumber() != null) {
            p.setUtrNumber(com.cax.cax_backend.common.util.EncryptionUtils.decrypt(p.getUtrNumber()));
        }
        if (p.getPaymentHistory() != null) {
            p.getPaymentHistory().forEach(h -> {
                if (h.getUtrNumber() != null) {
                    h.setUtrNumber(com.cax.cax_backend.common.util.EncryptionUtils.decrypt(h.getUtrNumber()));
                }
            });
        }
        return p;
    }

}
