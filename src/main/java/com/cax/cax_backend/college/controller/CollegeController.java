package com.cax.cax_backend.college.controller;

import com.cax.cax_backend.college.model.College;
import com.cax.cax_backend.college.repository.CollegeRepository;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController @RequestMapping("/api/colleges") @RequiredArgsConstructor
public class CollegeController {
    private final CollegeRepository repo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<College>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(repo.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<College>> getById(@PathVariable String id) {
        College c = repo.findById(id).orElseThrow(() -> new BusinessException.ResourceNotFoundException("College", id));
        return ResponseEntity.ok(ApiResponse.success(c));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<College>>> search(@RequestParam String query) {
        return ResponseEntity.ok(ApiResponse.success(repo.findByCollegeNameContainingIgnoreCase(query)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<College>> create(@RequestBody College college) {
        college.setCreatedAt(Instant.now());
        if (!college.isActive()) {
            college.setActive(true);
        }
        College saved = repo.save(college);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable String id) {
        if (!repo.existsById(id)) {
            throw new BusinessException.ResourceNotFoundException("College", id);
        }
        repo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("College deleted successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<College>> update(@PathVariable String id, @RequestBody College updated) {
        College existing = repo.findById(id).orElseThrow(() -> new BusinessException.ResourceNotFoundException("College", id));
        existing.setCollegeName(updated.getCollegeName());
        existing.setCollegeCode(updated.getCollegeCode());
        existing.setLocation(updated.getLocation());
        existing.setUniversity(updated.getUniversity());
        existing.setType(updated.getType());
        existing.setLogoUrl(updated.getLogoUrl());
        existing.setActive(updated.isActive());
        existing.setUpdatedAt(Instant.now());
        College saved = repo.save(existing);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }
}
