package com.cax.cax_backend.arcade.service;

import com.cax.cax_backend.arcade.model.ArcadeGameType;
import com.cax.cax_backend.arcade.model.ArcadePrompt;
import com.cax.cax_backend.arcade.model.ArcadeSession;
import com.cax.cax_backend.arcade.repository.ArcadePromptRepository;
import com.cax.cax_backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Serves round content from the prompt bank.
 *
 * <p>Prompts are drawn here rather than accepted from the host. That is partly a security
 * decision — a host-supplied prompt would be arbitrary text pushed onto every phone in the
 * room with no moderation path — and partly what makes the games endless without anyone
 * having to write questions before they play.
 */
@Service
@RequiredArgsConstructor
public class ArcadePromptService {

    private final ArcadePromptRepository promptRepository;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * The active bank for a game type. Cached because every round start reads it and the bank
     * changes only when it is seeded or edited.
     */
    @Cacheable(value = "arcadePrompts", key = "#gameType")
    public List<ArcadePrompt> bankFor(ArcadeGameType gameType) {
        return promptRepository.findByGameTypeAndActiveTrue(gameType);
    }

    /**
     * Picks a prompt this session has not used yet. Once the bank is exhausted — a long game
     * on a thin bank — it falls back to allowing repeats rather than failing the round, since
     * a repeated question is a much better outcome than a game that cannot continue.
     */
    public ArcadePrompt drawFor(ArcadeSession session) {
        List<ArcadePrompt> bank = bankFor(session.getGameType());
        if (bank.isEmpty()) {
            throw new BusinessException.BadRequestException(
                    "This game is not available right now. Please try another one.");
        }

        List<String> used = session.getUsedPromptIds() == null ? List.of() : session.getUsedPromptIds();
        List<ArcadePrompt> unused = new ArrayList<>(bank.size());
        for (ArcadePrompt p : bank) {
            if (!used.contains(p.getId())) unused.add(p);
        }

        List<ArcadePrompt> pool = unused.isEmpty() ? bank : unused;
        return pool.get(RANDOM.nextInt(pool.size()));
    }
}
