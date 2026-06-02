package com.cax.cax_backend.reward.service;

import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.model.WalletEmbedded;
import com.cax.cax_backend.user.repository.UserRepository;
import com.cax.cax_backend.reward.model.*;
import com.cax.cax_backend.reward.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RewardService {

    private final UserRepository userRepository;
    private final UserReferralRepository userReferralRepository;
    private final ReferralSettingsRepository referralSettingsRepository;
    private final RewardRepository rewardRepository;
    private final RedemptionLogRepository redemptionLogRepository;
    private final TaskRepository taskRepository;
    private final TaskCompletionRepository taskCompletionRepository;

    // ─── Referral Settings ───

    public ReferralSettings getReferralSettings() {
        return referralSettingsRepository.findById("default")
                .orElseGet(() -> {
                    ReferralSettings defaultSettings = ReferralSettings.builder()
                            .id("default")
                            .referralCoins(50.0)
                            .updatedAt(Instant.now())
                            .build();
                    return referralSettingsRepository.save(defaultSettings);
                });
    }

    public ReferralSettings updateReferralSettings(double coins, String updatedBy) {
        ReferralSettings settings = getReferralSettings();
        settings.setReferralCoins(coins);
        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedBy(updatedBy);
        return referralSettingsRepository.save(settings);
    }

    // ─── User Referral Codes ───

    public UserReferral getOrCreateReferral(String userId) {
        return userReferralRepository.findById(userId)
                .orElseGet(() -> {
                    String code = "CAX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    // Ensure uniqueness
                    while (userReferralRepository.findByReferralCode(code).isPresent()) {
                        code = "CAX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    }
                    UserReferral referral = UserReferral.builder()
                            .id(userId)
                            .referralCode(code)
                            .createdAt(Instant.now())
                            .build();
                    return userReferralRepository.save(referral);
                });
    }

    @Transactional
    public boolean processReferral(String newUserId, String referralCode) {
        if (referralCode == null || referralCode.trim().isEmpty()) {
            return false;
        }

        Optional<UserReferral> referrerReferralOpt = userReferralRepository.findByReferralCode(referralCode.trim().toUpperCase());
        if (referrerReferralOpt.isEmpty()) {
            log.warn("Invalid referral code presented: {}", referralCode);
            return false;
        }

        UserReferral referrerReferral = referrerReferralOpt.get();
        String referrerId = referrerReferral.getId();

        if (referrerId.equals(newUserId)) {
            log.warn("User cannot refer themselves: {}", newUserId);
            return false;
        }

        // Check if new user is already referred
        Optional<UserReferral> newUserReferralOpt = userReferralRepository.findById(newUserId);
        if (newUserReferralOpt.isPresent() && newUserReferralOpt.get().getReferredBy() != null) {
            log.warn("New user {} has already been referred", newUserId);
            return false;
        }

        // Link referral
        UserReferral newUserReferral = newUserReferralOpt.orElseGet(() -> {
            String code = "CAX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            return UserReferral.builder().id(newUserId).referralCode(code).build();
        });
        newUserReferral.setReferredBy(referrerId);
        userReferralRepository.save(newUserReferral);

        // Reward referrer
        User referrer = userRepository.findByUserId(referrerId)
                .or(() -> userRepository.findById(referrerId))
                .orElse(null);
        if (referrer != null) {
            ReferralSettings settings = getReferralSettings();
            double rewardCoins = settings.getReferralCoins();
            
            WalletEmbedded wallet = referrer.getWallet();
            if (wallet == null) {
                wallet = new WalletEmbedded(0.0, 0.0, 0.0);
            }
            wallet.setBalance(wallet.getBalance() + rewardCoins);
            wallet.setTotalEarned(wallet.getTotalEarned() + rewardCoins);
            referrer.setWallet(wallet);
            userRepository.save(referrer);
            
            log.info("Rewarded referrer {} with {} coins for inviting {}", referrerId, rewardCoins, newUserId);
            return true;
        }

        return false;
    }

    // ─── Rewards store ───

    public List<Reward> getEnabledRewards() {
        return rewardRepository.findByEnabled(true);
    }

    public List<Reward> getAllRewardsAdmin() {
        return rewardRepository.findAll();
    }

    public Reward saveReward(Reward reward) {
        if (reward.getCreatedAt() == null) {
            reward.setCreatedAt(Instant.now());
        }
        return rewardRepository.save(reward);
    }

    public void deleteReward(String id) {
        rewardRepository.deleteById(id);
    }

    @Transactional
    public RedemptionLog redeemReward(String userId, String rewardId) {
        User user = userRepository.findByUserId(userId)
                .or(() -> userRepository.findById(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isIdVerified()) {
            throw new IllegalStateException("ID verification is required to redeem rewards");
        }

        Reward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalArgumentException("Reward not found"));

        if (!reward.isEnabled() || reward.getStock() <= 0) {
            throw new IllegalStateException("Reward is currently unavailable or out of stock");
        }

        WalletEmbedded wallet = user.getWallet();
        if (wallet == null) {
            wallet = new WalletEmbedded(0.0, 0.0, 0.0);
        }

        if (wallet.getBalance() < reward.getCost()) {
            throw new IllegalStateException("Insufficient coin balance");
        }

        // Deduct coins
        wallet.setBalance(wallet.getBalance() - reward.getCost());
        wallet.setTotalSpent(wallet.getTotalSpent() + reward.getCost());
        user.setWallet(wallet);
        userRepository.save(user);

        // Deduct stock
        reward.setStock(reward.getStock() - 1);
        rewardRepository.save(reward);

        // Create random mock gift code
        String mockGiftCode = "GC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        // Create log
        RedemptionLog log = RedemptionLog.builder()
                .userId(userId)
                .rewardId(rewardId)
                .rewardTitle(reward.getTitle())
                .cost(reward.getCost())
                .giftCode(mockGiftCode)
                .redeemedAt(Instant.now())
                .build();

        return redemptionLogRepository.save(log);
    }

    public List<RedemptionLog> getUserRedemptions(String userId) {
        return redemptionLogRepository.findByUserId(userId);
    }

    // ─── Tasks ───

    public List<Task> getEnabledTasks() {
        return taskRepository.findByEnabled(true);
    }

    public List<Task> getAllTasksAdmin() {
        return taskRepository.findAll();
    }

    public Task saveTask(Task task) {
        if (task.getCreatedAt() == null) {
            task.setCreatedAt(Instant.now());
        }
        return taskRepository.save(task);
    }

    public void deleteTask(String id) {
        taskRepository.deleteById(id);
    }

    @Transactional
    public TaskCompletion claimTask(String userId, String taskId) {
        User user = userRepository.findByUserId(userId)
                .or(() -> userRepository.findById(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        if (!task.isEnabled()) {
            throw new IllegalStateException("This task is disabled");
        }

        // Check if already claimed
        if (taskCompletionRepository.findByUserIdAndTaskId(userId, taskId).isPresent()) {
            throw new IllegalStateException("Task reward has already been claimed");
        }

        // Validate task logic based on title / description keyword tags
        String title = task.getTitle().toLowerCase();
        String desc = task.getDescription().toLowerCase();

        if (title.contains("verify") || desc.contains("verify") || title.contains("id card") || desc.contains("id card")) {
            if (!user.isIdVerified()) {
                throw new IllegalStateException("You must verify your ID card to complete this task");
            }
        } else if (title.contains("refer") || desc.contains("refer") || title.contains("invite") || desc.contains("invite")) {
            // Check if they referred someone. In this decoupled model, count references in UserReferral
            long referralsCount = userReferralRepository.findAll().stream()
                    .filter(ur -> userId.equals(ur.getReferredBy()))
                    .count();
            if (referralsCount < 1) {
                throw new IllegalStateException("You must refer at least 1 student to complete this task");
            }
        }

        // Award coins
        WalletEmbedded wallet = user.getWallet();
        if (wallet == null) {
            wallet = new WalletEmbedded(0.0, 0.0, 0.0);
        }
        wallet.setBalance(wallet.getBalance() + task.getRewardCoins());
        wallet.setTotalEarned(wallet.getTotalEarned() + task.getRewardCoins());
        user.setWallet(wallet);
        userRepository.save(user);

        // Save completion
        TaskCompletion completion = TaskCompletion.builder()
                .userId(userId)
                .taskId(taskId)
                .claimedAt(Instant.now())
                .build();

        return taskCompletionRepository.save(completion);
    }

    public List<TaskCompletion> getUserCompletions(String userId) {
        return taskCompletionRepository.findByUserId(userId);
    }
}
