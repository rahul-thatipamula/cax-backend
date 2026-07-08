package com.cax.cax_backend.notification.controller;

import com.cax.cax_backend.common.annotation.AdminActivityLog;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.notification.dto.NotificationAdminView;
import com.cax.cax_backend.notification.model.Notification;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.common.enums.NotificationEnums;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/notifications") @RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Notification>>> getNotifications(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.getUserNotifications((String) auth.getPrincipal())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(Authentication auth, @PathVariable String id) {
        String userId = (String) auth.getPrincipal();
        service.markAsRead(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Marked as read"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(Authentication auth, @PathVariable String id) {
        String userId = (String) auth.getPrincipal();
        service.deleteNotification(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Deleted"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", service.getUnreadCount((String) auth.getPrincipal()))));
    }

    @PostMapping("/send-custom")
    public ResponseEntity<ApiResponse<Void>> sendCustomNotification(
            Authentication auth,
            @RequestBody Map<String, Object> body) {
        checkAdmin(auth);
        String targetType = (String) body.get("targetType");
        String title = (String) body.get("title");
        String bodyText = (String) body.get("body");
        String userId = (String) body.get("userId");
        String collegeId = (String) body.get("collegeId");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) body.get("data");

        if (title == null || title.isBlank() || bodyText == null || bodyText.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Title and body are required", 1400, 400));
        }

        if ("INDIVIDUAL".equalsIgnoreCase(targetType)) {
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("userId is required for INDIVIDUAL targetType", 1400, 400));
            }
            service.createNotification(userId, title, bodyText, NotificationEnums.NotificationType.SYSTEM, data);
        } else if ("ALL".equalsIgnoreCase(targetType)) {
            service.sendNotificationToAll(title, bodyText, NotificationEnums.NotificationType.SYSTEM, data);
        } else if ("COLLEGE".equalsIgnoreCase(targetType)) {
            if (collegeId == null || collegeId.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("collegeId is required for COLLEGE targetType", 1400, 400));
            }
            service.sendNotificationToCollege(collegeId, title, bodyText, NotificationEnums.NotificationType.SYSTEM, data);
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid targetType", 1400, 400));
        }

        return ResponseEntity.ok(ApiResponse.success("Custom notification processed successfully"));
    }

    @GetMapping("/admin/all")
    @AdminActivityLog(action = "List All Notifications")
    public ResponseEntity<ApiResponse<Page<NotificationAdminView>>> getAdminNotifications(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String collegeId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        checkAdmin(auth);

        Instant start = parseInstant(startDate);
        Instant end = parseInstant(endDate);

        Page<NotificationAdminView> result = service.getAdminNotifications(
                page, size, search, userId, collegeId, read, start, end);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException(
                    "Invalid date format, expected ISO-8601 (e.g. 2026-07-01T00:00:00Z)");
        }
    }

    private void checkAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        Claims claims = (Claims) auth.getCredentials();
        Boolean isAdmin = claims.get("isAdmin", Boolean.class);
        if (!Boolean.TRUE.equals(isAdmin)) {
            throw new com.cax.cax_backend.common.exception.AuthException.AdminOnlyException();
        }
    }
}
