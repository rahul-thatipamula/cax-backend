package com.cax.cax_backend.coins.service;

import com.cax.cax_backend.coins.model.AdRewardConfig;
import com.cax.cax_backend.coins.model.CoinConfig;
import com.cax.cax_backend.coins.model.CoinTransaction;
import com.cax.cax_backend.coins.repository.AdRewardConfigRepository;
import com.cax.cax_backend.coins.repository.CoinConfigRepository;
import com.cax.cax_backend.coins.repository.CoinTransactionRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoinService {

    private final UserRepository userRepository;
    private final AdRewardConfigRepository adRewardConfigRepository;
    private final CoinTransactionRepository coinTransactionRepository;
    private final CoinConfigRepository coinConfigRepository;

    public Map<String, Object> getBalance(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return Map.of(
                "coins", user.getCoins(),
                "totalEarned", user.getTotalCoinsEarned(),
                "totalSpent", user.getTotalCoinsSpent()
        );
    }

    public CoinTransaction earnFromAd(String userId, String adType) {
        AdRewardConfig config = adRewardConfigRepository.findByAdTypeAndActiveTrue(adType)
                .orElseThrow(() -> new IllegalArgumentException("Ad type not found or inactive: " + adType));

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        double newBalance = user.getCoins() + config.getCoinsReward();
        user.setCoins(newBalance);
        user.setTotalCoinsEarned(user.getTotalCoinsEarned() + config.getCoinsReward());
        userRepository.save(user);

        CoinTransaction tx = CoinTransaction.builder()
                .userId(userId)
                .amount(config.getCoinsReward())
                .type("EARNED_AD")
                .referenceId(adType)
                .note("Earned from watching " + adType)
                .balanceAfter(newBalance)
                .build();

        return coinTransactionRepository.save(tx);
    }

    // Called internally by ThoughtBoostService
    public void deductCoins(String userId, double amount, String referenceId, String note) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getCoins() < amount) {
            throw new IllegalStateException("Insufficient coins");
        }

        double newBalance = user.getCoins() - amount;
        user.setCoins(newBalance);
        user.setTotalCoinsSpent(user.getTotalCoinsSpent() + amount);
        userRepository.save(user);

        CoinTransaction tx = CoinTransaction.builder()
                .userId(userId)
                .amount(-amount)
                .type("SPENT_BOOST")
                .referenceId(referenceId)
                .note(note)
                .balanceAfter(newBalance)
                .build();

        coinTransactionRepository.save(tx);
    }

    public List<CoinTransaction> getTransactions(String userId, int page, int size) {
        return coinTransactionRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
    }

    // ── Coin config ──────────────────────────────────────────────────────────

    public CoinConfig getConfig() {
        return coinConfigRepository.findById(CoinConfig.SINGLETON_ID)
                .orElseGet(() -> coinConfigRepository.save(
                        CoinConfig.builder().id(CoinConfig.SINGLETON_ID).build()));
    }

    public CoinConfig updateBoostCost(double boostCost, String adminUserId) {
        CoinConfig config = getConfig();
        config.setBoostCost(boostCost);
        config.setUpdatedAt(Instant.now());
        config.setUpdatedBy(adminUserId);
        return coinConfigRepository.save(config);
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    public List<AdRewardConfig> getAllAdConfigs() {
        return adRewardConfigRepository.findAllByOrderByCreatedAtDesc();
    }

    public AdRewardConfig saveAdConfig(AdRewardConfig config) {
        config.setUpdatedAt(Instant.now());
        return adRewardConfigRepository.save(config);
    }

    public AdRewardConfig updateAdConfig(String id, AdRewardConfig patch, String adminUserId) {
        AdRewardConfig existing = adRewardConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found"));
        existing.setCoinsReward(patch.getCoinsReward());
        existing.setDescription(patch.getDescription());
        existing.setActive(patch.isActive());
        existing.setUpdatedAt(Instant.now());
        existing.setUpdatedBy(adminUserId);
        return adRewardConfigRepository.save(existing);
    }
}
