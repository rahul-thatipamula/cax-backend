package com.cax.cax_backend.ad.controller;

import com.cax.cax_backend.ad.model.Ad;
import com.cax.cax_backend.ad.service.AdService;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

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
        Optional<Ad> ad = adService.getActiveAdForUser(userId, collegeId);
        return ad
                .map(a -> ResponseEntity.ok(ApiResponse.success(a)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success(null)));
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
    public ResponseEntity<ApiResponse<Ad>> createAd(@RequestBody Ad ad) {
        return ResponseEntity.ok(ApiResponse.created("Ad created", adService.createAd(ad)));
    }

    /**
     * Update an ad (admin).
     * PUT /api/ads/admin/{id}
     */
    @PutMapping("/admin/{id}")
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
    public ResponseEntity<ApiResponse<String>> deleteAd(@PathVariable String id) {
        adService.deleteAd(id);
        return ResponseEntity.ok(ApiResponse.success("Ad deleted"));
    }

    /**
     * Toggle active status (admin).
     * PUT /api/ads/admin/{id}/toggle
     */
    @PutMapping("/admin/{id}/toggle")
    public ResponseEntity<ApiResponse<Ad>> toggleAd(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(adService.toggleActive(id)));
    }
}
