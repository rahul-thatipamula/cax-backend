package com.cax.cax_backend.settings.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.settings.model.SystemSetting;
import com.cax.cax_backend.settings.service.SystemSettingService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<ApiResponse<SystemSetting>> toggleOnlyAllowCollegeEmails(
            Authentication auth,
            @RequestParam boolean enabled) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(
                systemSettingService.updateOnlyAllowCollegeEmails(enabled)
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
