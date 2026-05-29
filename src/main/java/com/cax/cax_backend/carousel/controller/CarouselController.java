package com.cax.cax_backend.carousel.controller;

import com.cax.cax_backend.carousel.model.Carousel;
import com.cax.cax_backend.carousel.repository.CarouselRepository;
import com.cax.cax_backend.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController @RequestMapping("/api/carousel") @RequiredArgsConstructor
public class CarouselController {
    private final CarouselRepository repo;

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

    @PostMapping
    public ResponseEntity<ApiResponse<Carousel>> create(@RequestBody Carousel body) {
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
    public ResponseEntity<ApiResponse<List<Carousel>>> getAllCarousels(@RequestParam(required = false) String collegeId) {
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

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Carousel>> update(@PathVariable String id, @RequestBody Carousel body) {
        Carousel carousel = repo.findById(id).orElseThrow();
        carousel.setTitle(body.getTitle());
        carousel.setDescription(body.getDescription());
        carousel.setImageUrl(body.getImageUrl());
        carousel.setActionLink(body.getActionLink());
        carousel.setType(body.getType());
        carousel.setDisplayOrder(body.getDisplayOrder());
        carousel.setActive(body.isActive());
        carousel.setCollegeId(body.getCollegeId());
        carousel.setTargetAudience(body.getTargetAudience());
        carousel.setExpiresAt(body.getExpiresAt());
        carousel.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(ApiResponse.success(repo.save(carousel)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable String id) {
        repo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Carousel deleted"));
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<Carousel>> toggleActive(@PathVariable String id) {
        Carousel carousel = repo.findById(id).orElseThrow();
        carousel.setActive(!carousel.isActive());
        carousel.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(ApiResponse.success(repo.save(carousel)));
    }
}
