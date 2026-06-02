package com.cax.cax_backend.reward.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.enums.UserRole;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import com.cax.cax_backend.reward.model.*;
import com.cax.cax_backend.reward.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rewards/admin")
@RequiredArgsConstructor
public class AdminRewardController {

    private final RewardService rewardService;
    private final UserRepository userRepository;

    private void checkAdminAccess(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        User user = userRepository.findByUserId(userId)
                .or(() -> userRepository.findById(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getRole() != UserRole.ADMIN) {
            throw new IllegalStateException("Only administrators can perform this action");
        }
    }

    // ─── Rewards ───

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Reward>>> getAllRewards(Authentication auth) {
        checkAdminAccess(auth);
        return ResponseEntity.ok(ApiResponse.success(rewardService.getAllRewardsAdmin()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Reward>> createReward(
            Authentication auth,
            @RequestBody Reward reward) {
        checkAdminAccess(auth);
        Reward created = rewardService.saveReward(reward);
        return ResponseEntity.ok(ApiResponse.success("Reward created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Reward>> updateReward(
            Authentication auth,
            @PathVariable String id,
            @RequestBody Reward rewardDetails) {
        checkAdminAccess(auth);
        rewardDetails.setId(id);
        Reward updated = rewardService.saveReward(rewardDetails);
        return ResponseEntity.ok(ApiResponse.success("Reward updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReward(
            Authentication auth,
            @PathVariable String id) {
        checkAdminAccess(auth);
        rewardService.deleteReward(id);
        return ResponseEntity.ok(ApiResponse.success("Reward deleted successfully"));
    }

    // ─── Tasks ───

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<List<Task>>> getAllTasks(Authentication auth) {
        checkAdminAccess(auth);
        return ResponseEntity.ok(ApiResponse.success(rewardService.getAllTasksAdmin()));
    }

    @PostMapping("/tasks")
    public ResponseEntity<ApiResponse<Task>> createTask(
            Authentication auth,
            @RequestBody Task task) {
        checkAdminAccess(auth);
        Task created = rewardService.saveTask(task);
        return ResponseEntity.ok(ApiResponse.success("Task created successfully", created));
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<Task>> updateTask(
            Authentication auth,
            @PathVariable String id,
            @RequestBody Task taskDetails) {
        checkAdminAccess(auth);
        taskDetails.setId(id);
        Task updated = rewardService.saveTask(taskDetails);
        return ResponseEntity.ok(ApiResponse.success("Task updated successfully", updated));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            Authentication auth,
            @PathVariable String id) {
        checkAdminAccess(auth);
        rewardService.deleteTask(id);
        return ResponseEntity.ok(ApiResponse.success("Task deleted successfully"));
    }

    // ─── Settings ───

    @PutMapping("/referral/settings")
    public ResponseEntity<ApiResponse<ReferralSettings>> updateReferralSettings(
            Authentication auth,
            @RequestBody Map<String, Double> body) {
        checkAdminAccess(auth);
        String userId = (String) auth.getPrincipal();
        Double coins = body.get("referralCoins");
        if (coins == null || coins < 0) {
            throw new IllegalArgumentException("Referral coins value must be positive");
        }
        ReferralSettings settings = rewardService.updateReferralSettings(coins, userId);
        return ResponseEntity.ok(ApiResponse.success("Referral coins updated successfully", settings));
    }
}
