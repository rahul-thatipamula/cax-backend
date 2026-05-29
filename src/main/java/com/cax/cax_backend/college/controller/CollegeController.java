package com.cax.cax_backend.college.controller;

import com.cax.cax_backend.college.model.College;
import com.cax.cax_backend.college.repository.CollegeRepository;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController @RequestMapping("/api/colleges") @RequiredArgsConstructor
public class CollegeController {
    private final CollegeRepository repo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<College>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(repo.findByIsActiveTrue()));
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
}
