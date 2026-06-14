package com.cax.cax_backend.ad.controller;

import com.cax.cax_backend.ad.model.Ad;
import com.cax.cax_backend.ad.service.AdService;
import com.cax.cax_backend.common.annotation.AdminActivityLog;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    // ── Client endpoints ────────────────────────────────────────────────────

    /**
     * Returns the best active ad for the authenticated user.
     * Pass collegeId as a query param so the mobile app can request
     * college-specific ads.
     *
     * GET /api/ads/active?collegeId=xyz
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<Ad>> getActiveAd(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String collegeId) {

        String userId = jwtUtil.extractUserId(JwtUtil.extractFromHeader(authHeader));
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

    /**
     * Redirects to the target URL of the ad and records a click.
     * GET /api/ads/{id}/click
     */
    @GetMapping("/{id}/click")
    public ResponseEntity<Void> recordClick(
            @PathVariable String id,
            @RequestParam(required = false) String userId) {
        String redirectUrl = adService.recordClick(userId, id);
        return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                .location(java.net.URI.create(redirectUrl))
                .build();
    }

    /**
     * Record an impression when the ad is shown to the user.
     *
     * POST /api/ads/{id}/impression
     */
    @PostMapping("/{id}/impression")
    public ResponseEntity<ApiResponse<String>> recordImpression(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id) {

        String userId = jwtUtil.extractUserId(JwtUtil.extractFromHeader(authHeader));
        adService.recordImpression(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Impression recorded"));
    }

    // ── Admin endpoints ─────────────────────────────────────────────────────

    /**
     * List all ads (admin).
     * GET /api/ads/admin/all
     */
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<Ad>>> getAllAds() {
        return ResponseEntity.ok(ApiResponse.success(adService.getAllAds()));
    }

    /**
     * Create a new ad (admin).
     * POST /api/ads/admin
     */
    @PostMapping("/admin")
    @AdminActivityLog(action = "Create Ad")
    public ResponseEntity<ApiResponse<Ad>> createAd(@RequestBody Ad ad) {
        return ResponseEntity.ok(ApiResponse.created("Ad created", adService.createAd(ad)));
    }

    /**
     * Update an ad (admin).
     * PUT /api/ads/admin/{id}
     */
    @PutMapping("/admin/{id}")
    @AdminActivityLog(action = "Update Ad", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<Ad>> updateAd(
            @PathVariable String id,
            @RequestBody Ad body) {
        return ResponseEntity.ok(ApiResponse.success(adService.updateAd(id, body)));
    }

    /**
     * Delete an ad (admin).
     * DELETE /api/ads/admin/{id}
     */
    @DeleteMapping("/admin/{id}")
    @AdminActivityLog(action = "Delete Ad", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<String>> deleteAd(@PathVariable String id) {
        adService.deleteAd(id);
        return ResponseEntity.ok(ApiResponse.success("Ad deleted"));
    }

    /**
     * Toggle active status (admin).
     * PUT /api/ads/admin/{id}/toggle
     */
    @PutMapping("/admin/{id}/toggle")
    @AdminActivityLog(action = "Toggle Ad Status", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<Ad>> toggleAd(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(adService.toggleActive(id)));
    }

    /**
     * Get user-level analytics for a specific ad.
     * GET /api/ads/admin/{id}/analytics
     */
    @GetMapping("/admin/{id}/analytics")
    public ResponseEntity<ApiResponse<List<UserAdAnalyticsDto>>> getAdAnalytics(
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(adService.getAdAnalytics(id)));
    }
}
