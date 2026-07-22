package com.cax.cax_backend.event.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.cax.cax_backend.common.service.R2StorageService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import com.cax.cax_backend.event.model.EventMemory;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final R2StorageService r2StorageService;
    private final EventAnalyticsService eventAnalyticsService;

    /** Same authentication guard used across this controller, in one place. */
    private String requireUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        return (String) auth.getPrincipal();
    }

    @PostMapping(value = "/events/upload-image", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> uploadEventImage(
            Authentication auth,
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder) throws java.io.IOException {

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();

        if (file.isEmpty()) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("File is empty");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("File size exceeds 5MB limit");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Invalid filename");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        
        boolean isValidExtension = extension.equals(".jpg") || extension.equals(".jpeg") || extension.equals(".png") || extension.equals(".pdf");
        
        String contentType = file.getContentType();
        boolean isValidMimeType = false;
        if (contentType == null || contentType.equalsIgnoreCase("application/octet-stream")) {
            isValidMimeType = true;
        } else {
            String mime = contentType.toLowerCase();
            isValidMimeType = mime.equals("image/jpeg") || mime.equals("image/jpg") || mime.equals("image/png") || mime.equals("application/pdf");
        }

        if (!isValidExtension || !isValidMimeType) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Invalid file type. Only JPG, PNG, and PDF are allowed");
        }

        String folderPath = "events/" + folder;
        String r2Url = r2StorageService.uploadFile(file, folderPath);
        return ResponseEntity.ok(ApiResponse.success("Image uploaded successfully", r2Url));
    }

    // ========================================================================
    // EVENT CRUD
    // ========================================================================

    @PostMapping("/organizations/{organizationId}/events")
    public ResponseEntity<ApiResponse<Event>> createEvent(
            Authentication auth,
            @PathVariable String organizationId,
            @Valid @RequestBody Event eventData) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        Event created = eventService.createEvent(userId, organizationId, eventData);
        return ResponseEntity.ok(ApiResponse.created("Event created successfully", created));
    }

    @PutMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<Event>> updateEvent(
            Authentication auth,
            @PathVariable String eventId,
            @Valid @RequestBody Event eventData) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        Event updated = eventService.updateEvent(userId, eventId, eventData);
        return ResponseEntity.ok(ApiResponse.success("Event updated successfully", updated));
    }

    @PutMapping("/events/{eventId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelEvent(
            Authentication auth,
            @PathVariable String eventId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        eventService.cancelEvent(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success("Event cancelled successfully"));
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            Authentication auth,
            @PathVariable String eventId) {
        String userId = requireUserId(auth);
        eventService.deleteEvent(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success("Event deleted successfully"));
    }

    @PostMapping("/events/{eventId}/announce")
    public ResponseEntity<ApiResponse<Void>> sendEventAnnouncement(
            Authentication auth,
            @PathVariable String eventId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        eventService.sendEventAnnouncementNotification(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success("Announcement notification sent successfully"));
    }

    @GetMapping("/organizations/{organizationId}/events")
    public ResponseEntity<ApiResponse<List<Event>>> getOrganizationEvents(
            Authentication auth,
            @PathVariable String organizationId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        List<Event> events = eventService.getOrganizationEvents(userId, organizationId);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEventDetails(
            Authentication auth,
            @PathVariable String eventId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        Map<String, Object> detail = eventService.getEventDetailForUser(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping("/events/discover")
    public ResponseEntity<ApiResponse<List<Event>>> discoverEvents(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String userId = auth != null && auth.isAuthenticated() ? (String) auth.getPrincipal() : null;
        List<Event> events = eventService.discoverEvents(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    /**
     * Merged events + bulletin events feed, backing both the home screen's
     * "Upcoming Events" section and the Events tab's Upcoming/Ongoing/Completed/All filters.
     */
    @GetMapping("/events/feed")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEventsFeed(
            Authentication auth,
            @RequestParam(defaultValue = "upcoming") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String userId = auth != null && auth.isAuthenticated() ? (String) auth.getPrincipal() : null;
        Map<String, Object> feed = eventService.getEventsFeed(userId, filter, page, size);
        return ResponseEntity.ok(ApiResponse.success(feed));
    }

    @GetMapping("/events/joined")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getJoinedEvents(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        List<Map<String, Object>> joined = eventService.getJoinedEvents(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(joined));
    }


    // ========================================================================
    // PARTICIPANT MANAGEMENT
    // ========================================================================

    @PostMapping("/events/{eventId}/register")
    public ResponseEntity<ApiResponse<EventParticipant>> registerForEvent(
            Authentication auth,
            @PathVariable String eventId,
            @RequestBody(required = false) Map<String, Object> body) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        EventParticipant participant = eventService.registerForEvent(userId, eventId, body);
        return ResponseEntity.ok(ApiResponse.success("Registered successfully", participant));
    }

    @DeleteMapping("/events/{eventId}/participants/me")
    public ResponseEntity<ApiResponse<Void>> cancelRegistration(
            Authentication auth,
            @PathVariable String eventId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        eventService.cancelRegistration(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success("Registration cancelled successfully"));
    }


    @PostMapping("/events/{eventId}/payment")
    public ResponseEntity<ApiResponse<EventParticipant>> submitPayment(
            Authentication auth,
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        String utrNumber = (String) body.get("utrNumber");
        String paymentScreenshot = (String) body.get("paymentScreenshot");
        double amount = body.get("amount") != null ? ((Number) body.get("amount")).doubleValue() : 0;
        EventParticipant participant = eventService.submitPayment(userId, eventId, utrNumber, paymentScreenshot, amount);
        return ResponseEntity.ok(ApiResponse.success("Payment submitted successfully", participant));
    }

    @GetMapping("/events/{eventId}/participants")
    public ResponseEntity<ApiResponse<List<EventParticipant>>> getParticipants(
            Authentication auth,
            @PathVariable String eventId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        List<EventParticipant> participants = eventService.getParticipants(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success(participants));
    }

    @GetMapping("/events/{eventId}/participants/export")
    public ResponseEntity<byte[]> exportParticipants(
            Authentication auth,
            @PathVariable String eventId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        byte[] csvData = eventService.exportParticipantsToCsv(userId, eventId);

        String filename = "event_" + eventId + "_participants.csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .body(csvData);
    }


    @PutMapping("/events/{eventId}/participants/{participantId}/verify")
    public ResponseEntity<ApiResponse<EventParticipant>> verifyPayment(
            Authentication auth,
            @PathVariable String eventId,
            @PathVariable String participantId,
            @RequestParam boolean approved) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        EventParticipant participant = eventService.verifyPayment(userId, eventId, participantId, approved);
        String message = approved ? "Participant verified successfully" : "Participant rejected";
        return ResponseEntity.ok(ApiResponse.success(message, participant));
    }

    @PostMapping("/events/{eventId}/checkin")
    public ResponseEntity<ApiResponse<EventParticipant>> checkInParticipant(
            Authentication auth,
            @PathVariable String eventId,
            @RequestParam String ticketCode) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String organizerId = (String) auth.getPrincipal();
        EventParticipant participant = eventService.checkInParticipant(organizerId, eventId, ticketCode);
        return ResponseEntity.ok(ApiResponse.success("Checked in successfully", participant));
    }

    @GetMapping("/events/{eventId}/ticket")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTicketDetails(
            Authentication auth,
            @PathVariable String eventId,
            @RequestParam String ticketCode) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        Map<String, Object> details = eventService.getParticipantDetailsByCode(userId, eventId, ticketCode);
        return ResponseEntity.ok(ApiResponse.success(details));
    }

    @PutMapping("/events/{eventId}/participants/{participantId}/suspicious")
    public ResponseEntity<ApiResponse<EventParticipant>> toggleSuspicious(
            Authentication auth,
            @PathVariable String eventId,
            @PathVariable String participantId,
            @RequestBody Map<String, Object> body) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        boolean suspicious = (boolean) body.getOrDefault("suspicious", false);
        String note = (String) body.get("suspiciousNote");
        EventParticipant participant = eventService.toggleSuspicious(userId, eventId, participantId, suspicious, note);
        return ResponseEntity.ok(ApiResponse.success("Suspicious status updated successfully", participant));
    }


    // ========================================================================
    // COLLABORATION MANAGEMENT
    // ========================================================================

    /**
     * Invite another organization to collaborate on this event.
     * Body: { "organizationId": "org-xxx" }
     */
    @PostMapping("/events/{eventId}/collaborators")
    public ResponseEntity<ApiResponse<com.cax.cax_backend.event.model.Event>> addCollaborator(
            Authentication auth,
            @PathVariable String eventId,
            @RequestBody Map<String, String> body) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        String organizationId = body.get("organizationId");
        if (organizationId == null || organizationId.isBlank()) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("organizationId is required");
        }
        com.cax.cax_backend.event.model.Event event = eventService.addCollaborator(userId, eventId, organizationId);
        return ResponseEntity.ok(ApiResponse.success("Collaborator added successfully", event));
    }

    /** Remove a collaborating organization from the event. Only the primary org leader can do this. */
    @DeleteMapping("/events/{eventId}/collaborators/{organizationId}")
    public ResponseEntity<ApiResponse<com.cax.cax_backend.event.model.Event>> removeCollaborator(
            Authentication auth,
            @PathVariable String eventId,
            @PathVariable String organizationId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        com.cax.cax_backend.event.model.Event event = eventService.removeCollaborator(userId, eventId, organizationId);
        return ResponseEntity.ok(ApiResponse.success("Collaborator removed successfully", event));
    }

    @PostMapping("/events/{eventId}/memories")
    public ResponseEntity<ApiResponse<EventMemory>> uploadMemory(
            Authentication auth,
            @PathVariable String eventId,
            @RequestBody Map<String, String> body) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        String imageUrl = body.get("imageUrl");
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("imageUrl is required");
        }
        EventMemory memory = eventService.uploadMemory(userId, eventId, imageUrl);
        return ResponseEntity.ok(ApiResponse.success("Memory photo uploaded successfully", memory));
    }

    @DeleteMapping("/events/{eventId}/memories/{memoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteMemory(
            Authentication auth,
            @PathVariable String eventId,
            @PathVariable String memoryId) {
        String userId = (String) auth.getPrincipal();
        eventService.deleteMemory(userId, eventId, memoryId);
        return ResponseEntity.ok(ApiResponse.success("Memory photo deleted successfully"));
    }

    @PutMapping("/events/{eventId}/memories/{memoryId}/toggle-hide")
    public ResponseEntity<ApiResponse<EventMemory>> toggleHideMemory(
            Authentication auth,
            @PathVariable String eventId,
            @PathVariable String memoryId,
            @RequestParam boolean hidden) {
        String userId = (String) auth.getPrincipal();
        EventMemory memory = eventService.toggleHideMemory(userId, eventId, memoryId, hidden);
        return ResponseEntity.ok(ApiResponse.success("Memory visibility updated", memory));
    }

    @GetMapping("/events/{eventId}/memories")
    public ResponseEntity<ApiResponse<Page<EventMemory>>> getEventMemories(
            Authentication auth,
            @PathVariable String eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String filter) {
        String userId = (String) auth.getPrincipal();
        Page<EventMemory> memories = eventService.getEventMemories(userId, eventId, page, size, filter);
        return ResponseEntity.ok(ApiResponse.success(memories));
    }

    // ── Interaction Analytics Tracking Endpoints ────────────────────────────

    @PostMapping("/events/{id}/track-view")
    public ResponseEntity<ApiResponse<String>> trackEventView(@PathVariable String id) {
        eventAnalyticsService.recordEventView(id);
        return ResponseEntity.ok(ApiResponse.success("Event view recorded"));
    }

    @PostMapping("/events/{id}/track-share")
    public ResponseEntity<ApiResponse<String>> trackEventShare(@PathVariable String id) {
        eventAnalyticsService.recordEventShare(id);
        return ResponseEntity.ok(ApiResponse.success("Event share recorded"));
    }

    @PostMapping("/events/{id}/track-click")
    public ResponseEntity<ApiResponse<String>> trackEventClick(@PathVariable String id) {
        eventAnalyticsService.recordEventClick(id);
        return ResponseEntity.ok(ApiResponse.success("Event click recorded"));
    }

}

