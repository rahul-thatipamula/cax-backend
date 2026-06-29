package com.cax.cax_backend.boost.controller;

import com.cax.cax_backend.boost.model.ThoughtBoostRequest;
import com.cax.cax_backend.boost.service.ThoughtBoostService;
import com.cax.cax_backend.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/boost")
@RequiredArgsConstructor
public class ThoughtBoostController {

    private final ThoughtBoostService thoughtBoostService;

    @PostMapping("/thoughts/{thoughtId}")
    public ResponseEntity<ApiResponse<ThoughtBoostRequest>> boostThought(
            @PathVariable String thoughtId,
            Authentication auth) {
        String userId = auth.getName();
        ThoughtBoostRequest result = thoughtBoostService.submitBoost(userId, thoughtId);
        return ResponseEntity.ok(ApiResponse.success("Thought boosted successfully", result));
    }

    @GetMapping("/thoughts/me")
    public ResponseEntity<ApiResponse<List<ThoughtBoostRequest>>> myBoosts(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(thoughtBoostService.getMyBoosts(auth.getName())));
    }

    @GetMapping("/cost")
    public ResponseEntity<ApiResponse<Map<String, Object>>> boostCost() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("coins", thoughtBoostService.getBoostCost())));
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    @GetMapping("/admin/queue")
    public ResponseEntity<ApiResponse<List<ThoughtBoostRequest>>> adminQueue() {
        return ResponseEntity.ok(ApiResponse.success(thoughtBoostService.getAllBoostRequests()));
    }
}
