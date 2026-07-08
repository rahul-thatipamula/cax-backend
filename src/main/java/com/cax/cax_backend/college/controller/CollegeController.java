package com.cax.cax_backend.college.controller;

import com.cax.cax_backend.college.model.College;
import com.cax.cax_backend.college.repository.CollegeRepository;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.time.Instant;
import java.util.List;

@RestController @RequestMapping("/api/colleges") @RequiredArgsConstructor
public class CollegeController {
    private final CollegeRepository repo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<College>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(repo.findAllActive()));
    }

    @Cacheable(value = "colleges", key = "#id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<College>> getById(@PathVariable String id) {
        College c = repo.findById(id).orElseThrow(() -> new BusinessException.ResourceNotFoundException("College", id));
        if (c.isDeleted()) {
            throw new BusinessException.ResourceNotFoundException("College", id);
        }
        return ResponseEntity.ok(ApiResponse.success(c));
    }

    @Cacheable(value = "colleges", key = "'search:' + #query")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<College>>> search(@RequestParam String query) {
        return ResponseEntity.ok(ApiResponse.success(repo.findByCollegeNameContainingIgnoreCase(query)));
    }

    @CacheEvict(value = "colleges", allEntries = true)
    @PostMapping
    public ResponseEntity<ApiResponse<College>> create(@RequestBody College college) {
        if (college.getCollegeCode() == null || college.getCollegeCode().isBlank()) {
            throw new BusinessException.BadRequestException("College code cannot be empty");
        }
        if (college.getCollegeName() == null || college.getCollegeName().isBlank()) {
            throw new BusinessException.BadRequestException("College name cannot be empty");
        }
        
        String cleanCode = college.getCollegeCode().trim();
        String cleanName = college.getCollegeName().trim();
        
        if (repo.existsByCollegeCodeIgnoreCase(cleanCode)) {
            throw new BusinessException.ResourceAlreadyExistsException("College", "Code '" + cleanCode + "'");
        }
        if (repo.existsByCollegeNameIgnoreCase(cleanName)) {
            throw new BusinessException.ResourceAlreadyExistsException("College", "Name '" + cleanName + "'");
        }
        
        college.setCollegeCode(cleanCode);
        college.setCollegeName(cleanName);
        college.setCreatedAt(Instant.now());
        if (!college.isActive()) {
            college.setActive(true);
        }
        College saved = repo.save(college);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @CacheEvict(value = "colleges", allEntries = true)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable String id) {
        College college = repo.findById(id)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("College", id));
        if (college.isDeleted()) {
            throw new BusinessException.ResourceNotFoundException("College", id);
        }
        college.setDeleted(true);
        college.setDeletedAt(Instant.now());
        repo.save(college);
        return ResponseEntity.ok(ApiResponse.success("College deleted successfully"));
    }

    @CacheEvict(value = "colleges", allEntries = true)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<College>> update(@PathVariable String id, @RequestBody College updated) {
        College existing = repo.findById(id).orElseThrow(() -> new BusinessException.ResourceNotFoundException("College", id));
        if (existing.isDeleted()) {
            throw new BusinessException.ResourceNotFoundException("College", id);
        }

        if (updated.getCollegeCode() == null || updated.getCollegeCode().isBlank()) {
            throw new BusinessException.BadRequestException("College code cannot be empty");
        }
        if (updated.getCollegeName() == null || updated.getCollegeName().isBlank()) {
            throw new BusinessException.BadRequestException("College name cannot be empty");
        }
        
        String newCode = updated.getCollegeCode().trim();
        String newName = updated.getCollegeName().trim();
        
        // If code changed, check if new code conflicts with another college
        if (!newCode.equalsIgnoreCase(existing.getCollegeCode())) {
            if (repo.existsByCollegeCodeIgnoreCase(newCode)) {
                throw new BusinessException.ResourceAlreadyExistsException("College", "Code '" + newCode + "'");
            }
        }
        
        // If name changed, check if new name conflicts with another college
        if (!newName.equalsIgnoreCase(existing.getCollegeName())) {
            if (repo.existsByCollegeNameIgnoreCase(newName)) {
                throw new BusinessException.ResourceAlreadyExistsException("College", "Name '" + newName + "'");
            }
        }
        
        existing.setCollegeName(newName);
        existing.setCollegeCode(newCode);
        existing.setLocation(updated.getLocation());
        existing.setUniversity(updated.getUniversity());
        existing.setType(updated.getType());
        existing.setLogoUrl(updated.getLogoUrl());
        existing.setEmailDomains(updated.getEmailDomains());
        existing.setActive(updated.isActive());
        existing.setUpdatedAt(Instant.now());
        College saved = repo.save(existing);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }
}
