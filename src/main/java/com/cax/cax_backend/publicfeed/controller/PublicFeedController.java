package com.cax.cax_backend.publicfeed.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.repository.EventRepository;
import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.repository.ThoughtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only, sanitized global feed shown to users whose manual verification
 * is still pending — keeps them engaged instead of facing an empty app.
 * Served under /api/public/** (permitAll in SecurityConfig); exposes only
 * safe display fields, never contact/payment/participant data.
 */
@RestController
@RequestMapping("/api/public/feed")
@RequiredArgsConstructor
public class PublicFeedController {

    private final EventRepository eventRepository;
    private final ThoughtRepository thoughtRepository;

    @GetMapping("/global")
    public ResponseEntity<ApiResponse<Map<String, Object>>> globalFeed() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("events", globalEvents());
        result.put("thoughts", trendingThoughts());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private List<Map<String, Object>> globalEvents() {
        Instant now = Instant.now();
        return eventRepository.findByStatusAndGlobalTrue("ACTIVE").stream()
                .filter(e -> !e.isDeleted())
                .filter(e -> e.getEventEndDate() == null || e.getEventEndDate().isAfter(now))
                .sorted(Comparator.comparing(Event::getEventStartDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(15)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("name", e.getName());
                    m.put("description", truncate(e.getDescription(), 200));
                    m.put("logo", e.getLogo());
                    m.put("eventStartDate", e.getEventStartDate());
                    m.put("eventEndDate", e.getEventEndDate());
                    m.put("collegeName", e.getCollegeName());
                    m.put("joinedCount", e.getJoinedCount());
                    m.put("isPaid", e.isPaid());
                    return m;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> trendingThoughts() {
        return thoughtRepository.findActiveAll(PageRequest.of(0, 60)).stream()
                .sorted(Comparator.comparingInt(
                        (Thought t) -> (t.getLikes() != null ? t.getLikes().size() : 0)
                                + (t.getComments() != null ? t.getComments().size() : 0))
                        .reversed())
                .limit(12)
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("heading", t.getHeading());
                    m.put("content", truncate(t.getContent(), 280));
                    m.put("collegeName", t.getCollegeName());
                    m.put("likeCount", t.getLikes() != null ? t.getLikes().size() : 0);
                    m.put("commentCount", t.getComments() != null ? t.getComments().size() : 0);
                    m.put("createdAt", t.getCreatedAt());
                    m.put("creatorName", t.getCreatorName());
                    m.put("creatorVerified", t.isCreatorVerified());
                    return m;
                })
                .collect(Collectors.toList());
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
