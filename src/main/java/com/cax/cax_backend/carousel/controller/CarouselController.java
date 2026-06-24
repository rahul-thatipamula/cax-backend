package com.cax.cax_backend.carousel.controller;

import com.cax.cax_backend.carousel.model.Carousel;
import com.cax.cax_backend.carousel.repository.CarouselRepository;
import com.cax.cax_backend.common.dto.ApiResponse;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.cax.cax_backend.common.annotation.AdminActivityLog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController @RequestMapping("/api/carousel") @RequiredArgsConstructor
public class CarouselController {
    private final CarouselRepository repo;

    // Public endpoint — intentionally unauthenticated, shows banners on home screen
    @Cacheable(value = "carousels", key = "#collegeId != null ? #collegeId : 'default'")
    @GetMapping
    public ResponseEntity<ApiResponse<List<Carousel>>> getCarousels(@RequestParam(required = false) String collegeId) {
        List<Carousel> items;
        if (collegeId != null && !collegeId.isBlank()) {
            items = new ArrayList<>(repo.findByCollegeIdIsNullAndIsActiveTrueOrderByDisplayOrderAsc());
            items.addAll(repo.findByCollegeIdAndIsActiveTrueOrderByDisplayOrderAsc(collegeId));
            items.sort(Comparator.comparingInt(Carousel::getDisplayOrder));
        } else {
            items = repo.findByIsActiveTrueOrderByDisplayOrderAsc();
        }
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @CacheEvict(value = "carousels", allEntries = true)
    @PostMapping
    @AdminActivityLog(action = "Create Carousel Banner")
    public ResponseEntity<ApiResponse<Carousel>> create(Authentication auth, @RequestBody Carousel body) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.created("Carousel created", repo.save(body)));
    }

    @PostMapping("/{id}/click")
    public ResponseEntity<ApiResponse<Carousel>> trackClick(@PathVariable String id) {
        Carousel carousel = repo.findById(id).orElseThrow();
        carousel.setClicks(carousel.getClicks() + 1);
        carousel.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(ApiResponse.success(repo.save(carousel)));
    }

    @GetMapping("/admin/all")
    @AdminActivityLog(action = "List All Carousel Banners")
    public ResponseEntity<ApiResponse<List<Carousel>>> getAllCarousels(
            Authentication auth,
            @RequestParam(required = false) String collegeId) {
        checkAdmin(auth);
        List<Carousel> items;
        if (collegeId != null && !collegeId.isBlank()) {
            items = new ArrayList<>(repo.findByCollegeIdIsNullOrderByDisplayOrderAsc());
            items.addAll(repo.findByCollegeIdOrderByDisplayOrderAsc(collegeId));
            items.sort(Comparator.comparingInt(Carousel::getDisplayOrder));
        } else {
            items = repo.findAllByOrderByDisplayOrderAsc();
        }
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @CacheEvict(value = "carousels", allEntries = true)
    @PutMapping("/{id}")
    @AdminActivityLog(action = "Update Carousel Banner", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<Carousel>> update(Authentication auth, @PathVariable String id, @RequestBody Carousel body) {
        checkAdmin(auth);
        Carousel carousel = repo.findById(id).orElseThrow();
        carousel.setTitle(body.getTitle());
        carousel.setDescription(body.getDescription());
        carousel.setImageUrl(body.getImageUrl());
        carousel.setActionLink(body.getActionLink());
        carousel.setActionUrl(body.getActionLink());
        carousel.setType(body.getType());
        carousel.setDesignType(body.getDesignType());
        carousel.setDisplayOrder(body.getDisplayOrder());
        carousel.setActive(body.isActive());
        carousel.setCollegeId(body.getCollegeId());
        carousel.setTargetAudience(body.getTargetAudience());
        carousel.setExpiresAt(body.getExpiresAt());
        carousel.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(ApiResponse.success(repo.save(carousel)));
    }

    @CacheEvict(value = "carousels", allEntries = true)
    @DeleteMapping("/{id}")
    @AdminActivityLog(action = "Delete Carousel Banner", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<String>> delete(Authentication auth, @PathVariable String id) {
        checkAdmin(auth);
        repo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Carousel deleted"));
    }

    @CacheEvict(value = "carousels", allEntries = true)
    @PutMapping("/{id}/toggle")
    @AdminActivityLog(action = "Toggle Carousel Banner Status", resourceIdParam = "id")
    public ResponseEntity<ApiResponse<Carousel>> toggleActive(Authentication auth, @PathVariable String id) {
        checkAdmin(auth);
        Carousel carousel = repo.findById(id).orElseThrow();
        carousel.setActive(!carousel.isActive());
        carousel.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(ApiResponse.success(repo.save(carousel)));
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
