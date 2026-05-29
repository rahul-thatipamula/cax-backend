package com.cax.cax_backend.aicredit.controller;

import com.cax.cax_backend.aicredit.model.AICreditConfig;
import com.cax.cax_backend.aicredit.repository.AICreditRepository;
import com.cax.cax_backend.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController @RequestMapping("/api/admin/ai-credits") @RequiredArgsConstructor
public class AICreditController {
    private final AICreditRepository repo;

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<AICreditConfig>> getConfig() {
        AICreditConfig config = repo.findById("default").orElse(AICreditConfig.builder().build());
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/config")
    public ResponseEntity<ApiResponse<AICreditConfig>> updateConfig(@RequestBody AICreditConfig body) {
        body.setId("default");
        body.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(ApiResponse.success(repo.save(body)));
    }
}
