package com.cax.cax_backend.event.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.model.EventTeam;
import com.cax.cax_backend.event.repository.EventParticipantRepository;
import com.cax.cax_backend.event.repository.EventTeamRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Team registration flows for events. Teams are a grouping layer over
 * EventParticipant records — payment, tickets, and check-in stay per person
 * (except the PER_TEAM fee cascade, handled in EventService.verifyPayment).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventTeamService {

    private final EventService eventService;
    private final EventTeamRepository eventTeamRepository;
    private final EventParticipantRepository eventParticipantRepository;

    // No 0/O or 1/I — codes get read out loud at busy registration desks.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    // ========================================================================
    // JOIN FLOWS
    // ========================================================================

    /** Leader creates a team and registers themselves as its first member. */
    public Map<String, Object> createTeam(String userId, String eventId, String teamName,
                                          Map<String, Object> registrationDetails) {
        Event event = eventService.getEventById(eventId);
        requireTeamParticipation(event);

        if (teamName == null || teamName.trim().length() < 3 || teamName.trim().length() > 40) {
            throw new BusinessException.BadRequestException("Team name must be between 3 and 40 characters.");
        }
        teamName = teamName.trim();

        EventParticipant participant = eventService.buildParticipant(userId, event, registrationDetails);

        EventTeam team = EventTeam.builder()
                .eventId(eventId)
                .teamName(teamName)
                .leaderUserId(userId)
                .teamCode(generateTeamCode(eventId))
                .build();
        team = saveTeamWithCodeRetry(team, eventId);

        participant.setTeamId(team.getId());
        participant.setTeamName(teamName);
        participant.setTeamLeader(true);
        EventParticipant savedParticipant;
        try {
            savedParticipant = eventParticipantRepository.save(participant);
        } catch (DuplicateKeyException e) {
            // Concurrent double-tap: the second insert lost the unique-index race.
            // Remove the team shell we just created so no empty team lingers.
            eventTeamRepository.deleteById(team.getId());
            throw new BusinessException.BadRequestException("You are already registered for this event.");
        }

        log.info("Team '{}' ({}) created for event {} by user {}", teamName, team.getTeamCode(), eventId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("team", team);
        result.put("participant", eventService.decryptParticipant(savedParticipant));
        return result;
    }

    /** A member joins an existing team using its invite code. */
    public Map<String, Object> joinTeam(String userId, String eventId, String teamCode,
                                        Map<String, Object> registrationDetails) {
        Event event = eventService.getEventById(eventId);
        requireTeamParticipation(event);

        if (teamCode == null || teamCode.isBlank()) {
            throw new BusinessException.BadRequestException("Team code is required.");
        }
        EventTeam team = eventTeamRepository
                .findByEventIdAndTeamCode(eventId, teamCode.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException.BadRequestException(
                        "No team found with this code. Check the code with your team leader."));

        if ("CANCELLED".equals(team.getStatus())) {
            throw new BusinessException.BadRequestException("This team has been cancelled.");
        }

        long activeMembers = countActiveMembers(eventId, team.getId());
        if (activeMembers >= event.getMaxTeamSize()) {
            throw new BusinessException.BadRequestException(
                    "This team is already full (" + event.getMaxTeamSize() + " members max).");
        }

        EventParticipant participant = eventService.buildParticipant(userId, event, registrationDetails);
        participant.setTeamId(team.getId());
        participant.setTeamName(team.getTeamName());
        participant.setTeamLeader(false);

        // PER_TEAM events: the fee covers the whole team, so members joining a
        // team whose payment is already verified are verified immediately.
        if (event.isPaid() && "PER_TEAM".equals(event.getTeamFeeType())) {
            if ("VERIFIED".equals(team.getPaymentStatus())) {
                participant.setStatus("VERIFIED");
                participant.setVerifiedAt(Instant.now());
            }
            // else: stays PENDING_PAYMENT; the client shows "waiting for the
            // team leader's payment" instead of a pay button.
        }

        EventParticipant savedParticipant;
        try {
            savedParticipant = eventParticipantRepository.save(participant);
        } catch (DuplicateKeyException e) {
            throw new BusinessException.BadRequestException("You are already registered for this event.");
        }

        // May promote the team to COMPLETE and issue held-back tickets.
        eventService.recomputeTeamCompletion(event, team);

        Map<String, Object> result = new HashMap<>();
        result.put("team", eventTeamRepository.findById(team.getId()).orElse(team));
        result.put("participant", eventService.decryptParticipant(
                eventParticipantRepository.findById(savedParticipant.getId()).orElse(savedParticipant)));
        return result;
    }

    /** The caller's team for this event, with member summaries. */
    public Map<String, Object> getMyTeam(String userId, String eventId) {
        Event event = eventService.getEventById(eventId);
        EventParticipant participant = eventParticipantRepository
                .findFirstByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BusinessException.BadRequestException("You are not registered for this event."));
        if (participant.getTeamId() == null) {
            throw new BusinessException.BadRequestException("You joined this event individually, not as a team.");
        }
        EventTeam team = eventTeamRepository.findById(participant.getTeamId())
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Team", participant.getTeamId()));
        return eventService.buildTeamPayload(event, team);
    }

    // ========================================================================
    // LEAVE / REMOVE (only while the team is still FORMING)
    // ========================================================================

    public void leaveTeam(String userId, String eventId) {
        Event event = eventService.getEventById(eventId);
        EventParticipant participant = eventParticipantRepository
                .findFirstByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BusinessException.BadRequestException("You are not registered for this event."));
        if (participant.getTeamId() == null) {
            throw new BusinessException.BadRequestException("You joined this event individually, not as a team.");
        }
        EventTeam team = eventTeamRepository.findById(participant.getTeamId())
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Team", participant.getTeamId()));

        if (!"FORMING".equals(team.getStatus())) {
            throw new BusinessException.BadRequestException(
                    "Your team is already complete — members can no longer leave. Contact the event organizers.");
        }
        if (participant.isCheckedIn()) {
            throw new BusinessException.BadRequestException("You are already checked in and cannot leave the team.");
        }
        // Leaving deletes the registration record — never allow that once money
        // or an approval is attached to it.
        if (participant.getAmountPaid() > 0
                || "VERIFIED".equals(participant.getStatus())
                || "PAYMENT_SUBMITTED".equals(participant.getStatus())) {
            throw new BusinessException.BadRequestException(
                    "Your registration already has a payment or verification attached. "
                            + "Contact the event organizers to make changes.");
        }

        if (participant.isTeamLeader()) {
            long others = countActiveMembers(eventId, team.getId()) - 1;
            if (others > 0) {
                throw new BusinessException.BadRequestException(
                        "As team leader, remove your members first — leaving now would strand them.");
            }
            // Sole member: leaving disbands the team.
            eventParticipantRepository.delete(participant);
            eventTeamRepository.delete(team);
            log.info("Team {} disbanded by leader {} for event {}", team.getId(), userId, eventId);
            return;
        }

        eventParticipantRepository.delete(participant);
        log.info("User {} left team {} for event {}", userId, team.getId(), eventId);
    }

    /** Leader (or an event manager) removes a member while the team is FORMING. */
    public void removeMember(String actorId, String eventId, String teamId, String memberUserId) {
        Event event = eventService.getEventById(eventId);
        EventTeam team = eventTeamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Team", teamId));
        if (!team.getEventId().equals(eventId)) {
            throw new BusinessException.BadRequestException("Team does not belong to this event.");
        }

        boolean isLeader = actorId.equals(team.getLeaderUserId());
        if (!isLeader) {
            // Throws unless the actor manages this event.
            eventService.verifyEventManager(actorId, event);
        }

        // Once a team is COMPLETE, only event managers may still remove members.
        if (!"FORMING".equals(team.getStatus()) && isLeader) {
            throw new BusinessException.BadRequestException(
                    "The team is already complete — members can only be removed by the event organizers.");
        }

        EventParticipant member = eventParticipantRepository
                .findFirstByEventIdAndUserId(eventId, memberUserId)
                .orElseThrow(() -> new BusinessException.BadRequestException("This user is not part of the event."));
        if (!team.getId().equals(member.getTeamId())) {
            throw new BusinessException.BadRequestException("This user is not part of your team.");
        }
        if (member.isTeamLeader()) {
            throw new BusinessException.BadRequestException("The team leader cannot be removed from the team.");
        }
        if (member.isCheckedIn()) {
            throw new BusinessException.BadRequestException("This member is already checked in and cannot be removed.");
        }
        // Leaders can't delete a registration that carries a payment or an
        // approval — only event managers may resolve those.
        if (isLeader && (member.getAmountPaid() > 0
                || "VERIFIED".equals(member.getStatus())
                || "PAYMENT_SUBMITTED".equals(member.getStatus()))) {
            throw new BusinessException.BadRequestException(
                    "This member has already paid or been verified — only the event organizers can remove them.");
        }

        eventParticipantRepository.delete(member);
        log.info("User {} removed from team {} (event {}) by {}", memberUserId, teamId, eventId, actorId);
    }

    // ========================================================================
    // BULK CHECK-IN
    // ========================================================================

    /**
     * Checks in every eligible member of the team (VERIFIED, ticketed, not yet
     * checked in). Reuses the single-participant check-in so per-member
     * validation and operator identity capture stay identical to a QR scan.
     */
    public Map<String, Object> checkInTeam(String organizerId, String eventId, String teamId) {
        Event event = eventService.getEventById(eventId);
        eventService.verifyEventManager(organizerId, event);

        // Fail fast with a clear message instead of silently skipping everyone.
        if ("CANCELLED".equals(event.getStatus())) {
            throw new BusinessException.BadRequestException(
                    "This event has been cancelled — check-in is disabled.");
        }
        if ("COMPLETED".equals(event.getStatus())) {
            throw new BusinessException.BadRequestException(
                    "This event is completed — check-in is closed.");
        }

        EventTeam team = eventTeamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Team", teamId));
        if (!team.getEventId().equals(eventId)) {
            throw new BusinessException.BadRequestException("Team does not belong to this event.");
        }

        List<EventParticipant> members = eventParticipantRepository.findByEventIdAndTeamId(eventId, teamId);
        List<EventParticipant> checkedIn = new java.util.ArrayList<>();
        int skipped = 0;
        for (EventParticipant member : members) {
            boolean eligible = "VERIFIED".equals(member.getStatus())
                    && member.getTicketCode() != null && !member.getTicketCode().isEmpty()
                    && !member.isCheckedIn();
            if (!eligible) {
                skipped++;
                continue;
            }
            try {
                checkedIn.add(eventService.checkInParticipant(organizerId, eventId, member.getTicketCode()));
            } catch (Exception e) {
                log.warn("Bulk check-in skipped member {} of team {}: {}", member.getId(), teamId, e.getMessage());
                skipped++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("checkedIn", checkedIn);
        result.put("skippedCount", skipped);
        result.put("teamName", team.getTeamName());
        return result;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void requireTeamParticipation(Event event) {
        if (event.isExternallyManaged()) {
            throw new BusinessException.BadRequestException(
                    "This event is managed externally. Please register via the provided external link.");
        }
        String type = event.getParticipationType();
        if (!"TEAM".equals(type) && !"BOTH".equals(type)) {
            throw new BusinessException.BadRequestException("This event does not accept team registrations.");
        }
    }

    private long countActiveMembers(String eventId, String teamId) {
        return eventParticipantRepository.findByEventIdAndTeamId(eventId, teamId).stream()
                .filter(m -> !"REJECTED".equals(m.getStatus()))
                .count();
    }

    private String generateTeamCode(String eventId) {
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder sb = new StringBuilder(7);
            for (int i = 0; i < 6; i++) {
                if (i == 3) sb.append('-');
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!eventTeamRepository.existsByEventIdAndTeamCode(eventId, code)) {
                return code;
            }
        }
        throw new BusinessException.BadRequestException("Could not generate a team code. Please try again.");
    }

    private EventTeam saveTeamWithCodeRetry(EventTeam team, String eventId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return eventTeamRepository.save(team);
            } catch (DuplicateKeyException e) {
                // Code collision race against the unique (eventId, teamCode) index.
                team.setTeamCode(generateTeamCode(eventId));
            }
        }
        throw new BusinessException.BadRequestException("Could not create the team. Please try again.");
    }
}
