package com.cax.cax_backend.bulletinevent.controller;

import com.cax.cax_backend.bulletinevent.model.BulletinEvent;
import com.cax.cax_backend.bulletinevent.service.BulletinEventService;
import com.cax.cax_backend.common.annotation.AdminActivityLog;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.util.JwtUtil;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bulletin-events")
@RequiredArgsConstructor
public class BulletinEventController {

    private final BulletinEventService bulletinEventService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    // ── User Client endpoints ────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<BulletinEvent>>> getBulletinEvents(
            @RequestHeader("Authorization") String authHeader) {
        String userId = jwtUtil.extractUserId(JwtUtil.extractFromHeader(authHeader));
        User user = userService.getUserByUserId(userId);
        String collegeId = (user.getCollegeDetails() != null) ? user.getCollegeDetails().getCollegeId() : null;

        List<BulletinEvent> events = bulletinEventService.getBulletinEventsForUser(collegeId);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    // ── Admin endpoints ─────────────────────────────────────────────────────

    @GetMapping("/admin/all")
    @AdminActivityLog(action = "List All Bulletin Events")
    public ResponseEntity<ApiResponse<List<BulletinEvent>>> getAllBulletinEvents(Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(bulletinEventService.getAllBulletinEvents()));
    }

    @PostMapping("/admin")
    @AdminActivityLog(action = "Create Bulletin Event")
    public ResponseEntity<ApiResponse<BulletinEvent>> createBulletinEvent(
            Authentication auth, @RequestBody BulletinEvent bulletinEvent) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.created("Bulletin Event created", bulletinEventService.createBulletinEvent(bulletinEvent)));
    }

    @PutMapping("/admin/{id}")
    @AdminActivityLog(action = "Update Bulletin Event", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<BulletinEvent>> updateBulletinEvent(
            Authentication auth,
            @PathVariable String id,
            @RequestBody BulletinEvent body) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(bulletinEventService.updateBulletinEvent(id, body)));
    }

    @PatchMapping("/admin/{id}/toggle-active")
    @AdminActivityLog(action = "Toggle Bulletin Event Active", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<BulletinEvent>> toggleActive(Authentication auth, @PathVariable String id) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(bulletinEventService.toggleActive(id)));
    }

    @DeleteMapping("/admin/{id}")
    @AdminActivityLog(action = "Soft Delete Bulletin Event", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<String>> deleteBulletinEvent(Authentication auth, @PathVariable String id) {
        checkAdmin(auth);
        bulletinEventService.softDeleteBulletinEvent(id);
        return ResponseEntity.ok(ApiResponse.success("Bulletin Event removed"));
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
