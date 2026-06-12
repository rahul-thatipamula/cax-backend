package com.cax.cax_backend.common.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final R2StorageService r2StorageService;

    @GetMapping("/presigned-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPresignedUploadUrl(
            Authentication auth,
            @RequestParam("folder") String folder,
            @RequestParam("extension") String extension,
            @RequestParam("contentType") String contentType) {

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();

        // Validate folder parameter to prevent path traversal or arbitrary directory writing
        String cleanFolder = folder.replaceAll("[^a-zA-Z0-9-_]", "");
        if (cleanFolder.isBlank()) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Invalid folder name");
        }

        String ext = extension.toLowerCase();
        if (!ext.equals(".jpg") && !ext.equals(".jpeg") && !ext.equals(".png") && !ext.equals(".webp") &&
            !ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png") && !ext.equals("webp")) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Invalid file type. Only JPG, PNG, and WEBP are allowed");
        }

        Map<String, String> urls = r2StorageService.generatePresignedUploadUrl(cleanFolder, userId, ext, contentType);
        return ResponseEntity.ok(ApiResponse.success(urls));
    }
}
