package com.cax.cax_backend.event.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.cax.cax_backend.common.service.R2StorageService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final R2StorageService r2StorageService;

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

    @PostMapping("/clubs/{clubId}/events")
    public ResponseEntity<ApiResponse<Event>> createEvent(
            Authentication auth,
            @PathVariable String clubId,
            @RequestBody Event eventData) {
        String userId = (String) auth.getPrincipal();
        Event created = eventService.createEvent(userId, clubId, eventData);
        return ResponseEntity.ok(ApiResponse.created("Event created successfully", created));
    }

    @PutMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<Event>> updateEvent(
            Authentication auth,
            @PathVariable String eventId,
            @RequestBody Event eventData) {
        String userId = (String) auth.getPrincipal();
        Event updated = eventService.updateEvent(userId, eventId, eventData);
        return ResponseEntity.ok(ApiResponse.success("Event updated successfully", updated));
    }

    @PutMapping("/events/{eventId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelEvent(
            Authentication auth,
            @PathVariable String eventId) {
        String userId = (String) auth.getPrincipal();
        eventService.cancelEvent(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success("Event cancelled successfully"));
    }

    @GetMapping("/clubs/{clubId}/events")
    public ResponseEntity<ApiResponse<List<Event>>> getClubEvents(
            Authentication auth,
            @PathVariable String clubId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        List<Event> events = eventService.getClubEvents(userId, clubId);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEventDetails(
            Authentication auth,
            @PathVariable String eventId) {
        String userId = (String) auth.getPrincipal();
        Map<String, Object> detail = eventService.getEventDetailForUser(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping("/events/discover")
    public ResponseEntity<ApiResponse<List<Event>>> discoverEvents() {
        List<Event> events = eventService.discoverEvents();
        return ResponseEntity.ok(ApiResponse.success(events));
    }


    // ========================================================================
    // PARTICIPANT MANAGEMENT
    // ========================================================================

    @PostMapping("/events/{eventId}/register")
    public ResponseEntity<ApiResponse<EventParticipant>> registerForEvent(
            Authentication auth,
            @PathVariable String eventId) {
        String userId = (String) auth.getPrincipal();
        EventParticipant participant = eventService.registerForEvent(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success("Registered successfully", participant));
    }

    @PostMapping("/events/{eventId}/payment")
    public ResponseEntity<ApiResponse<EventParticipant>> submitPayment(
            Authentication auth,
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body) {
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
        String userId = (String) auth.getPrincipal();
        List<EventParticipant> participants = eventService.getParticipants(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success(participants));
    }

    @PutMapping("/events/{eventId}/participants/{participantId}/verify")
    public ResponseEntity<ApiResponse<EventParticipant>> verifyPayment(
            Authentication auth,
            @PathVariable String eventId,
            @PathVariable String participantId,
            @RequestParam boolean approved) {
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
        String organizerId = (String) auth.getPrincipal();
        EventParticipant participant = eventService.checkInParticipant(organizerId, eventId, ticketCode);
        return ResponseEntity.ok(ApiResponse.success("Checked in successfully", participant));
    }

    @GetMapping("/events/{eventId}/ticket")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTicketDetails(
            Authentication auth,
            @PathVariable String eventId,
            @RequestParam String ticketCode) {
        Map<String, Object> details = eventService.getParticipantDetailsByCode(eventId, ticketCode);
        return ResponseEntity.ok(ApiResponse.success(details));
    }
}
