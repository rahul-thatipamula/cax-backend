package com.cax.cax_backend.settings.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.settings.model.SystemSetting;
import com.cax.cax_backend.settings.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/version")
@RequiredArgsConstructor
public class VersionController {

    private final SystemSettingService systemSettingService;

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLatestVersion() {
        SystemSetting settings = systemSettingService.getSystemSetting();
        Map<String, Object> response = new HashMap<>();
        response.put("latestVersion", settings.getLatestVersion() != null ? settings.getLatestVersion() : "1.0.0");
        response.put("minRequiredVersion", settings.getMinRequiredVersion() != null ? settings.getMinRequiredVersion() : "1.0.0");
        response.put("latestBuildNumber", settings.getLatestBuildNumber());
        response.put("minRequiredBuildNumber", settings.getMinRequiredBuildNumber());
        response.put("updateMessage", settings.getUpdateMessage() != null ? settings.getUpdateMessage() : "A new version of CAX is available. Please update to continue.");
        response.put("storeUrl", settings.getStoreUrl() != null ? settings.getStoreUrl() : "");
        response.put("razorpayEnabled", settings.isRazorpayEnabled());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
