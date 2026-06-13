package com.cax.cax_backend.notification.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.notification.model.Notification;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.common.enums.NotificationEnums;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable String id) {
        service.markAsRead(id); return ResponseEntity.ok(ApiResponse.success("Marked as read"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        service.deleteNotification(id); return ResponseEntity.ok(ApiResponse.success("Deleted"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", service.getUnreadCount((String) auth.getPrincipal()))));
    }

    @PostMapping("/send-custom")
    public ResponseEntity<ApiResponse<Void>> sendCustomNotification(
            @RequestBody Map<String, Object> body) {
        String targetType = (String) body.get("targetType"); // "ALL", "INDIVIDUAL", or "COLLEGE"
        String title = (String) body.get("title");
        String bodyText = (String) body.get("body");
        String userId = (String) body.get("userId"); // Required if INDIVIDUAL
        String collegeId = (String) body.get("collegeId"); // Required if COLLEGE
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
}
