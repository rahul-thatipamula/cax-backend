package com.cax.cax_backend.thoughtsubscription.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.AuthException;
import com.cax.cax_backend.thoughtsubscription.model.ThoughtSubscription;
import com.cax.cax_backend.thoughtsubscription.service.ThoughtSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class ThoughtSubscriptionController {

    private final ThoughtSubscriptionService subscriptionService;

    @PostMapping("/{authorId}")
    public ResponseEntity<ApiResponse<ThoughtSubscription>> subscribe(
            Authentication auth,
            @PathVariable String authorId) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Subscribed", subscriptionService.subscribe(userId, authorId)));
    }

    @DeleteMapping("/{authorId}")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(
            Authentication auth,
            @PathVariable String authorId) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        subscriptionService.unsubscribe(userId, authorId);
        return ResponseEntity.ok(ApiResponse.success("Unsubscribed"));
    }

    @GetMapping("/{authorId}/status")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> status(
            Authentication auth,
            @PathVariable String authorId) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        boolean subscribed = subscriptionService.isSubscribed(userId, authorId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("subscribed", subscribed)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<ThoughtSubscription>>> mine(Authentication auth) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.listMine(userId)));
    }

    @GetMapping("/{authorId}/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> count(
            Authentication auth,
            @PathVariable String authorId) {
        requireAuth(auth);
        long count = subscriptionService.countSubscribers(authorId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    private void requireAuth(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null)
            throw new AuthException.UnauthorizedException("User is not authenticated");
    }
}
