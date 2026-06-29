package com.cax.cax_backend.boost.service;

import com.cax.cax_backend.boost.model.BoostStatus;
import com.cax.cax_backend.boost.model.ThoughtBoostRequest;
import com.cax.cax_backend.boost.repository.ThoughtBoostRequestRepository;
import com.cax.cax_backend.coins.service.CoinService;
import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.repository.ThoughtRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ThoughtBoostService {

    private final ThoughtBoostRequestRepository boostRepository;
    private final ThoughtRepository thoughtRepository;
    private final CoinService coinService;

    public ThoughtBoostRequest submitBoost(String userId, String thoughtId) {
        Thought thought = thoughtRepository.findById(thoughtId)
                .orElseThrow(() -> new IllegalArgumentException("Thought not found"));

        if (thought.isDisabled()) {
            throw new IllegalArgumentException("Cannot boost a disabled thought");
        }

        if (!thought.getUserId().equals(userId)) {
            throw new IllegalArgumentException("You can only boost your own thoughts");
        }

        boolean alreadyQueued = boostRepository.existsByThoughtIdAndStatusIn(
                thoughtId, List.of(BoostStatus.PENDING, BoostStatus.ACTIVE));
        if (alreadyQueued) {
            throw new IllegalStateException("This thought is already pending or active in the boost queue");
        }

        double boostCost = coinService.getConfig().getBoostCost();

        coinService.deductCoins(userId, boostCost, thoughtId, "Boosted thought: " + thought.getHeading());

        ThoughtBoostRequest request = ThoughtBoostRequest.builder()
                .thoughtId(thoughtId)
                .userId(userId)
                .collegeId(thought.getCollegeId())
                .thoughtHeading(thought.getHeading())
                .coinsSpent(boostCost)
                .status(BoostStatus.PENDING)
                .build();

        return boostRepository.save(request);
    }

    public List<ThoughtBoostRequest> getMyBoosts(String userId) {
        return boostRepository.findByUserId(userId);
    }

    public double getBoostCost() {
        return coinService.getConfig().getBoostCost();
    }

    // Called by trending endpoint — returns active boosted thoughts (up to 3)
    public List<Thought> getActiveBoostedThoughts() {
        List<ThoughtBoostRequest> active = boostRepository.findByStatus(BoostStatus.ACTIVE);
        if (active.isEmpty()) return List.of();

        List<String> ids = active.stream()
                .map(ThoughtBoostRequest::getThoughtId)
                .collect(Collectors.toList());

        return thoughtRepository.findAllById(ids).stream()
                .filter(t -> !t.isDisabled())
                .collect(Collectors.toList());
    }

    // Called by ThoughtBoostScheduler every 3 hours
    public void runSchedulerWindow() {
        // 1. Complete all currently ACTIVE boosts
        List<ThoughtBoostRequest> currentlyActive = boostRepository.findByStatus(BoostStatus.ACTIVE);
        Instant now = Instant.now();
        for (ThoughtBoostRequest req : currentlyActive) {
            req.setStatus(BoostStatus.COMPLETED);
            req.setCompletedAt(now);
        }
        boostRepository.saveAll(currentlyActive);
        log.info("[BoostScheduler] Completed {} previously active boosts", currentlyActive.size());

        // 2. Pick up to 3 oldest PENDING boosts and activate them
        List<ThoughtBoostRequest> pending = boostRepository.findByStatusOrderByRequestedAtAsc(
                BoostStatus.PENDING, PageRequest.of(0, 3));
        for (ThoughtBoostRequest req : pending) {
            req.setStatus(BoostStatus.ACTIVE);
            req.setActivatedAt(now);
        }
        boostRepository.saveAll(pending);
        log.info("[BoostScheduler] Activated {} new boosts for next 3-hour window", pending.size());
    }

    // Admin: full queue view
    public List<ThoughtBoostRequest> getAllBoostRequests() {
        return boostRepository.findAll();
    }
}
