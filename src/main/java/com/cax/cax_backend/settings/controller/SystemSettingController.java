package com.cax.cax_backend.settings.controller;

import com.cax.cax_backend.common.annotation.AdminActivityLog;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.settings.dto.VersionSettingsRequest;
import com.cax.cax_backend.settings.model.SystemSetting;
import com.cax.cax_backend.settings.service.SystemSettingService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings/system")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    @GetMapping
    public ResponseEntity<ApiResponse<SystemSetting>> getSystemSetting(Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(systemSettingService.getSystemSetting()));
    }

    @PostMapping("/toggle-college-emails")
    @AdminActivityLog(action = "Toggle Only Allow College Emails", resourceIdParam = "enabled")
    public ResponseEntity<ApiResponse<SystemSetting>> toggleOnlyAllowCollegeEmails(
            Authentication auth,
            @RequestParam boolean enabled) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(
                systemSettingService.updateOnlyAllowCollegeEmails(enabled)
        ));
    }

    @PostMapping("/version")
    @AdminActivityLog(action = "Update App Version Settings")
    public ResponseEntity<ApiResponse<SystemSetting>> updateVersionSettings(
            Authentication auth,
            @RequestBody VersionSettingsRequest request) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(
                systemSettingService.updateVersionSettings(request)
        ));
    }

    @GetMapping("/version/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getVersionStats(Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(
                systemSettingService.getVersionStats()
        ));
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
