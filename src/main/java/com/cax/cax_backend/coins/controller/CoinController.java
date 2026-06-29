package com.cax.cax_backend.coins.controller;

import com.cax.cax_backend.coins.model.AdRewardConfig;
import com.cax.cax_backend.coins.model.CoinConfig;
import com.cax.cax_backend.coins.model.CoinTransaction;
import com.cax.cax_backend.coins.service.CoinService;
import com.cax.cax_backend.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coins")
@RequiredArgsConstructor
public class CoinController {

    private final CoinService coinService;

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> balance(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(coinService.getBalance(auth.getName())));
    }

    @PostMapping("/earn-ad")
    public ResponseEntity<ApiResponse<CoinTransaction>> earnFromAd(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String adType = body.get("adType");
        if (adType == null || adType.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("adType is required", 400, 400));
        }
        CoinTransaction tx = coinService.earnFromAd(auth.getName(), adType);
        return ResponseEntity.ok(ApiResponse.success("Coins earned", tx));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<CoinTransaction>>> transactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(coinService.getTransactions(auth.getName(), page, size)));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<CoinConfig>> getConfig() {
        return ResponseEntity.ok(ApiResponse.success(coinService.getConfig()));
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    @PutMapping("/admin/boost-cost")
    public ResponseEntity<ApiResponse<CoinConfig>> updateBoostCost(
            @RequestBody Map<String, Double> body,
            Authentication auth) {
        Double cost = body.get("boostCost");
        if (cost == null || cost <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("boostCost must be positive", 400, 400));
        }
        return ResponseEntity.ok(ApiResponse.success("Updated", coinService.updateBoostCost(cost, auth.getName())));
    }

    @GetMapping("/admin/ad-configs")
    public ResponseEntity<ApiResponse<List<AdRewardConfig>>> listAdConfigs() {
        return ResponseEntity.ok(ApiResponse.success(coinService.getAllAdConfigs()));
    }

    @PostMapping("/admin/ad-configs")
    public ResponseEntity<ApiResponse<AdRewardConfig>> createAdConfig(
            @RequestBody AdRewardConfig config) {
        return ResponseEntity.ok(ApiResponse.success("Created", coinService.saveAdConfig(config)));
    }

    @PutMapping("/admin/ad-configs/{id}")
    public ResponseEntity<ApiResponse<AdRewardConfig>> updateAdConfig(
            @PathVariable String id,
            @RequestBody AdRewardConfig patch,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Updated", coinService.updateAdConfig(id, patch, auth.getName())));
    }
}
