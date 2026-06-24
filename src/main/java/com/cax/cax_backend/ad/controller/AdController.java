package com.cax.cax_backend.ad.controller;

import com.cax.cax_backend.ad.model.Ad;
import com.cax.cax_backend.ad.service.AdService;
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
import java.util.Optional;
import com.cax.cax_backend.ad.dto.UserAdAnalyticsDto;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
public class AdController {

    private final AdService adService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    // ── Client endpoints ────────────────────────────────────────────────────

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<Ad>> getActiveAd(
            @RequestHeader("Authorization") String authHeader) {

        String userId = jwtUtil.extractUserId(JwtUtil.extractFromHeader(authHeader));
        User user = userService.getUserByUserId(userId);
        String collegeId = (user.getCollegeDetails() != null) ? user.getCollegeDetails().getCollegeId() : null;

        Optional<Ad> adOpt = adService.getActiveAdForUser(userId, collegeId);

        if (adOpt.isPresent()) {
            Ad ad = adOpt.get();
            Ad responseAd = Ad.builder()
                    .id(ad.getId())
                    .adId(ad.getAdId())
                    .title(ad.getTitle())
                    .imageUrl(ad.getImageUrl())
                    .active(ad.isActive())
                    .global(ad.isGlobal())
                    .collegeId(ad.getCollegeId())
                    .maxViewsPerUser(ad.getMaxViewsPerUser())
                    .closeTimerSeconds(ad.getCloseTimerSeconds())
                    .createdAt(ad.getCreatedAt())
                    .updatedAt(ad.getUpdatedAt())
                    .build();

            if (ad.getRedirectUrl() != null && !ad.getRedirectUrl().isBlank()) {
                String trackingUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/ads/")
                        .path(ad.getId())
                        .path("/click")
                        .queryParam("userId", userId)
                        .toUriString();
                responseAd.setRedirectUrl(trackingUrl);
            } else {
                responseAd.setRedirectUrl(null);
            }
            return ResponseEntity.ok(ApiResponse.success(responseAd));
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/click")
    public ResponseEntity<Void> recordClick(
            @PathVariable String id,
            @RequestParam(required = false) String userId) {
        String redirectUrl = adService.recordClick(userId, id);
        return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                .location(java.net.URI.create(redirectUrl))
                .build();
    }

    @PostMapping("/{id}/impression")
    public ResponseEntity<ApiResponse<String>> recordImpression(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id) {
        String userId = jwtUtil.extractUserId(JwtUtil.extractFromHeader(authHeader));
        adService.recordImpression(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Impression recorded"));
    }

    // ── Admin endpoints ─────────────────────────────────────────────────────

    @GetMapping("/admin/all")
    @AdminActivityLog(action = "List All Ads")
    public ResponseEntity<ApiResponse<List<Ad>>> getAllAds(Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(adService.getAllAds()));
    }

    @PostMapping("/admin")
    @AdminActivityLog(action = "Create Ad")
    public ResponseEntity<ApiResponse<Ad>> createAd(Authentication auth, @RequestBody Ad ad) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.created("Ad created", adService.createAd(ad)));
    }

    @PutMapping("/admin/{id}")
    @AdminActivityLog(action = "Update Ad", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<Ad>> updateAd(
            Authentication auth,
            @PathVariable String id,
            @RequestBody Ad body) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(adService.updateAd(id, body)));
    }

    @DeleteMapping("/admin/{id}")
    @AdminActivityLog(action = "Delete Ad", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<String>> deleteAd(Authentication auth, @PathVariable String id) {
        checkAdmin(auth);
        adService.deleteAd(id);
        return ResponseEntity.ok(ApiResponse.success("Ad deleted"));
    }

    @PutMapping("/admin/{id}/toggle")
    @AdminActivityLog(action = "Toggle Ad Status", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<Ad>> toggleAd(Authentication auth, @PathVariable String id) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(adService.toggleActive(id)));
    }

    @GetMapping("/admin/{id}/analytics")
    @AdminActivityLog(action = "View Ad Analytics", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<List<UserAdAnalyticsDto>>> getAdAnalytics(
            Authentication auth,
            @PathVariable String id) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(adService.getAdAnalytics(id)));
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
