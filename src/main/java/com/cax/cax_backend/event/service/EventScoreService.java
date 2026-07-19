package com.cax.cax_backend.event.service;

import com.cax.cax_backend.event.model.EventScore;
import com.cax.cax_backend.event.repository.EventScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Maintains each event's ranking score in its own collection, separate from the Event document.
 * A new event's starting score inherits a decayed share of the organizing club's best past events,
 * so a consistently well-performing organization's new events start with a head start over an
 * organization with no track record. The score then grows with how many people join.
 */
@Service
@RequiredArgsConstructor
public class EventScoreService {

    private static final double INHERITANCE_DECAY = 0.3;
    private static final double POINTS_PER_PARTICIPANT = 2.0;

    private final EventScoreRepository eventScoreRepository;

    /**
     * Creates the score record for a newly created event, seeded from the decayed average of the
     * organizing club's top 3 already-completed past events (0 if none).
     */
    public EventScore initializeScore(String eventId, String organizationId, Instant eventEndDate) {
        Instant now = Instant.now();
        double baseScore = 0;
        if (organizationId != null && !organizationId.isBlank()) {
            List<EventScore> topPastEvents =
                    eventScoreRepository.findTop3ByOrganizationIdAndEventEndDateBeforeOrderByScoreDesc(organizationId, now);
            if (!topPastEvents.isEmpty()) {
                double average = topPastEvents.stream().mapToDouble(EventScore::getScore).average().orElse(0);
                baseScore = average * INHERITANCE_DECAY;
            }
        }

        EventScore eventScore = EventScore.builder()
                .eventId(eventId)
                .organizationId(organizationId)
                .eventEndDate(eventEndDate)
                .baseScore(baseScore)
                .joinedCount(0)
                .score(baseScore)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return eventScoreRepository.save(eventScore);
    }

    /** Recomputes an event's score from its current participant count. Call whenever that count changes. */
    public void recalculate(String eventId, long joinedCount) {
        eventScoreRepository.findByEventId(eventId).ifPresent(existing -> {
            existing.setJoinedCount(joinedCount);
            existing.setScore(existing.getBaseScore() + joinedCount * POINTS_PER_PARTICIPANT);
            existing.setUpdatedAt(Instant.now());
            eventScoreRepository.save(existing);
        });
    }

    public double getScore(String eventId) {
        return eventScoreRepository.findByEventId(eventId).map(EventScore::getScore).orElse(0.0);
    }
}
