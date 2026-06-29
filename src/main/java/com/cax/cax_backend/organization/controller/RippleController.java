package com.cax.cax_backend.organization.controller;

import com.cax.cax_backend.organization.model.Ripple;
import com.cax.cax_backend.organization.service.RippleService;
import com.cax.cax_backend.common.dto.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class RippleController {

    private final RippleService rippleService;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRippleRequest {
        private String content;
    }

    @GetMapping("/{organizationId}/ripples")
    public ResponseEntity<ApiResponse<List<Ripple>>> getRipples(
            @PathVariable String organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Ripple> ripples = rippleService.getRipples(organizationId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Ripples fetched successfully", ripples));
    }

    @PostMapping("/{organizationId}/ripples")
    public ResponseEntity<ApiResponse<Ripple>> createRipple(
            Authentication auth,
            @PathVariable String organizationId,
            @RequestBody CreateRippleRequest request) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        Ripple ripple = rippleService.createRipple(userId, organizationId, request.getContent());
        return ResponseEntity.ok(ApiResponse.created("Ripple created successfully", ripple));
    }

    @DeleteMapping("/ripples/{rippleId}")
    public ResponseEntity<ApiResponse<Void>> deleteRipple(
            Authentication auth,
            @PathVariable String rippleId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        rippleService.deleteRipple(userId, rippleId);
        return ResponseEntity.ok(ApiResponse.success("Ripple deleted successfully"));
    }
}
