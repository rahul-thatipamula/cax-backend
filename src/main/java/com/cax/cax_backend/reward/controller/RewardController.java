package com.cax.cax_backend.reward.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.reward.model.*;
import com.cax.cax_backend.reward.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rewards")
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Reward>>> getEnabledRewards() {
        return ResponseEntity.ok(ApiResponse.success(rewardService.getEnabledRewards()));
    }

    @PostMapping("/{id}/redeem")
    public ResponseEntity<ApiResponse<RedemptionLog>> redeemReward(
            Authentication auth,
            @PathVariable String id) {
        String userId = (String) auth.getPrincipal();
        RedemptionLog log = rewardService.redeemReward(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Gift card redeemed successfully!", log));
    }

    @GetMapping("/redemptions")
    public ResponseEntity<ApiResponse<List<RedemptionLog>>> getUserRedemptions(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(rewardService.getUserRedemptions(userId)));
    }

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<List<Task>>> getEnabledTasks() {
        return ResponseEntity.ok(ApiResponse.success(rewardService.getEnabledTasks()));
    }

    @GetMapping("/tasks/completions")
    public ResponseEntity<ApiResponse<List<TaskCompletion>>> getUserCompletions(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(rewardService.getUserCompletions(userId)));
    }

    @PostMapping("/tasks/{id}/claim")
    public ResponseEntity<ApiResponse<TaskCompletion>> claimTask(
            Authentication auth,
            @PathVariable String id) {
        String userId = (String) auth.getPrincipal();
        TaskCompletion completion = rewardService.claimTask(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Task reward claimed successfully!", completion));
    }

    @GetMapping("/referral/code")
    public ResponseEntity<ApiResponse<UserReferral>> getUserReferralCode(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(rewardService.getOrCreateReferral(userId)));
    }

    @PostMapping("/referral/apply")
    public ResponseEntity<ApiResponse<Void>> applyReferralCode(
            Authentication auth,
            @RequestBody Map<String, String> body) {
        String userId = (String) auth.getPrincipal();
        String code = body.get("code");
        boolean success = rewardService.processReferral(userId, code);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("Referral applied successfully!"));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to apply referral code. It may be invalid or already used.", 400, 400));
        }
    }

    @GetMapping("/referral/settings")
    public ResponseEntity<ApiResponse<ReferralSettings>> getReferralSettings() {
        return ResponseEntity.ok(ApiResponse.success(rewardService.getReferralSettings()));
    }
}
