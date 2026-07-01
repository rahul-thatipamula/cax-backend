package com.cax.cax_backend.common.ratelimit;

import com.cax.cax_backend.common.annotation.AdminActivityLog;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.AuthException;
import io.jsonwebtoken.Claims;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin surface for the adaptive rate limiter — view current rules/event-mode state, edit
 *  rules, and toggle "event mode" (e.g. for a hackathon) without a redeploy. Same admin-gating
 *  pattern as SystemSettingController (checkAdmin via the isAdmin JWT claim). */
@RestController
@RequestMapping("/api/settings/rate-limits")
@RequiredArgsConstructor
public class RateLimitAdminController {

    private final RateLimitConfigService configService;
    private final RateLimitAutoScaler autoScaler;

    @GetMapping
    public ResponseEntity<ApiResponse<RateLimitConfig>> getConfig(Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(configService.getConfig()));
    }

    /** Per-rule multiplier the auto-scaler has currently applied, purely from observed
     *  traffic — 1.0 means no surge detected for that rule right now. Lets an admin see
     *  that the system reacted on its own, without having to have caught it happening. */
    @GetMapping("/auto-scale-status")
    public ResponseEntity<ApiResponse<Map<String, Double>>> getAutoScaleStatus(Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(autoScaler.currentMultipliers()));
    }

    @PutMapping("/rules")
    @AdminActivityLog(action = "Update Rate Limit Rules")
    public ResponseEntity<ApiResponse<RateLimitConfig>> updateRules(
            Authentication auth,
            @RequestBody UpdateRulesRequest request) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(
                configService.updateRules(request.getRules(), request.getDefaultRule())
        ));
    }

    /** Turns "event mode" on/off — e.g. POST with {"enabled":true,"multiplier":50} right
     *  before a hackathon, and {"enabled":false} once it's over. If expiresAt is set, it
     *  auto-reverts even if nobody remembers to call this again. */
    @PostMapping("/event-mode")
    @AdminActivityLog(action = "Toggle Rate Limit Event Mode")
    public ResponseEntity<ApiResponse<RateLimitConfig>> setEventMode(
            Authentication auth,
            @RequestBody EventModeRequest request) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(
                configService.setEventMode(request.isEnabled(), request.getMultiplier(), request.getExpiresAt())
        ));
    }

    @Data
    public static class UpdateRulesRequest {
        private List<RateLimitRule> rules;
        private RateLimitRule defaultRule;
    }

    private void checkAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new AuthException.UnauthorizedException("User is not authenticated");
        }
        Claims claims = (Claims) auth.getCredentials();
        Boolean isAdmin = claims.get("isAdmin", Boolean.class);
        if (!Boolean.TRUE.equals(isAdmin)) {
            throw new AuthException.AdminOnlyException();
        }
    }
}
