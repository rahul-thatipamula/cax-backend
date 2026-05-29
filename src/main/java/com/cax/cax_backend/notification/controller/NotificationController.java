package com.cax.cax_backend.notification.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.notification.model.Notification;
import com.cax.cax_backend.notification.service.NotificationService;
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
}
