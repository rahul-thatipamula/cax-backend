package com.cax.cax_backend.publicfeed.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public marketing-site stats (caxone.in) — served under /api/public/** (permitAll). */
@RestController
@RequestMapping("/api/public/stats")
@RequiredArgsConstructor
public class PublicStatsController {

    private final UserRepository userRepository;

    @GetMapping("/student-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> studentCount() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", userRepository.countVerifiedUsers())));
    }
}
