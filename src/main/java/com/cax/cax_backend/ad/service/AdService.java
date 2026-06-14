package com.cax.cax_backend.ad.service;

import com.cax.cax_backend.ad.model.Ad;
import com.cax.cax_backend.ad.model.UserAdTracking;
import com.cax.cax_backend.ad.repository.AdRepository;
import com.cax.cax_backend.ad.repository.UserAdTrackingRepository;
import com.cax.cax_backend.ad.dto.UserAdAnalyticsDto;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdService {

    private final AdRepository adRepository;
    private final UserAdTrackingRepository userAdTrackingRepository;
    private final UserRepository userRepository;

    /**
     * Returns the best active ad for the given user.
     * Priority: college-specific ads first, then global ads.
     * Respects maxViewsPerUser limit.
     */
    public Optional<Ad> getActiveAdForUser(String userId, String collegeId) {
        List<Ad> candidates = new ArrayList<>();

        // 1. College-specific active ads
        if (collegeId != null && !collegeId.isBlank()) {
            candidates.addAll(adRepository.findByActiveAndCollegeId(true, collegeId));
        }

        // 2. Global active ads
        candidates.addAll(adRepository.findByActiveAndGlobal(true, true));

        // 3. Filter by maxViewsPerUser
        for (Ad ad : candidates) {
            Optional<UserAdTracking> tracking = userAdTrackingRepository
                    .findByUserIdAndAdId(userId, ad.getId());
            int viewCount = tracking.map(UserAdTracking::getViewCount).orElse(0);
            if (viewCount < ad.getMaxViewsPerUser()) {
                return Optional.of(ad);
            }
        }

        return Optional.empty();
    }

    /**
     * Record an impression (view) for a user-ad pair.
     */
    public void recordImpression(String userId, String adId) {
        Optional<UserAdTracking> existing = userAdTrackingRepository
                .findByUserIdAndAdId(userId, adId);

        UserAdTracking tracking;
        if (existing.isPresent()) {
            tracking = existing.get();
            tracking.setViewCount(tracking.getViewCount() + 1);
            tracking.setLastViewedAt(Instant.now());
        } else {
            tracking = UserAdTracking.builder()
                    .userId(userId)
                    .adId(adId)
                    .viewCount(1)
                    .clickCount(0)
                    .lastViewedAt(Instant.now())
                    .build();
        }

        userAdTrackingRepository.save(tracking);

        adRepository.findById(adId).ifPresent(ad -> {
            ad.setTotalImpressions(ad.getTotalImpressions() + 1);
            adRepository.save(ad);
        });
    }

    /**
     * Record a click for an ad and returns its redirect URL.
     */
    public String recordClick(String userId, String adId) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new IllegalArgumentException("Ad not found: " + adId));
        
        ad.setTotalClicks(ad.getTotalClicks() + 1);
        adRepository.save(ad);

        if (userId != null && !userId.isBlank()) {
            Optional<UserAdTracking> existing = userAdTrackingRepository
                    .findByUserIdAndAdId(userId, adId);
            UserAdTracking tracking;
            if (existing.isPresent()) {
                tracking = existing.get();
                tracking.setClickCount(tracking.getClickCount() + 1);
                tracking.setLastClickedAt(Instant.now());
            } else {
                tracking = UserAdTracking.builder()
                        .userId(userId)
                        .adId(adId)
                        .viewCount(0)
                        .clickCount(1)
                        .lastClickedAt(Instant.now())
                        .build();
            }
            userAdTrackingRepository.save(tracking);
        }
        
        String url = ad.getRedirectUrl();
        return (url != null && !url.isBlank()) ? url : "/";
    }

    /**
     * Retrieve user-level detailed analytics for a campaign.
     */
    public List<UserAdAnalyticsDto> getAdAnalytics(String adId) {
        List<UserAdTracking> trackings = userAdTrackingRepository.findByAdId(adId);
        List<UserAdAnalyticsDto> result = new ArrayList<>();
        
        for (UserAdTracking tracking : trackings) {
            String userName = "Unknown User";
            String userEmail = "N/A";
            
            Optional<User> userOpt = userRepository.findByUserId(tracking.getUserId());
            if (userOpt.isPresent()) {
                userName = userOpt.get().getName();
                userEmail = userOpt.get().getEmail();
            }
            
            result.add(UserAdAnalyticsDto.builder()
                    .userId(tracking.getUserId())
                    .userName(userName)
                    .userEmail(userEmail)
                    .viewCount(tracking.getViewCount())
                    .clickCount(tracking.getClickCount())
                    .lastViewedAt(tracking.getLastViewedAt())
                    .lastClickedAt(tracking.getLastClickedAt())
                    .build());
        }
        return result;
    }

    // ── Admin CRUD ──────────────────────────────────────────────────────────

    public List<Ad> getAllAds() {
        return adRepository.findAll();
    }

    @CacheEvict(value = "ads", allEntries = true)
    public Ad createAd(Ad ad) {
        ad.setCreatedAt(Instant.now());
        return adRepository.save(ad);
    }

    @CacheEvict(value = "ads", allEntries = true)
    public Ad updateAd(String id, Ad body) {
        Ad ad = adRepository.findById(id).orElseThrow();
        ad.setTitle(body.getTitle());
        ad.setImageUrl(body.getImageUrl());
        ad.setRedirectUrl(body.getRedirectUrl());
        ad.setActive(body.isActive());
        ad.setGlobal(body.isGlobal());
        ad.setCollegeId(body.getCollegeId());
        ad.setMaxViewsPerUser(body.getMaxViewsPerUser());
        ad.setCloseTimerSeconds(body.getCloseTimerSeconds());
        ad.setUpdatedAt(Instant.now());
        return adRepository.save(ad);
    }

    @CacheEvict(value = "ads", allEntries = true)
    public void deleteAd(String id) {
        adRepository.deleteById(id);
    }

    @CacheEvict(value = "ads", allEntries = true)
    public Ad toggleActive(String id) {
        Ad ad = adRepository.findById(id).orElseThrow();
        ad.setActive(!ad.isActive());
        ad.setUpdatedAt(Instant.now());
        return adRepository.save(ad);
    }
}
