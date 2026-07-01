package com.cax.cax_backend.game.service;

import com.cax.cax_backend.game.model.BingoGamePrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BingoGamePromptService {

    private static final String COLLECTION = "bingo_game_prompts";
    private static final int MAX_COUNT = 400; // matches BingoGameService.MAX_PROMPTS, the cap on a game's prompt pool

    private final MongoTemplate mongoTemplate;

    /** Draws {@code count} random, active prompts from the prompt bank (unbiased via $sample). */
    public List<BingoGamePrompt> getRandomPrompts(int count) {
        int safeCount = Math.max(1, Math.min(count, MAX_COUNT));
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("isActive").is(true)),
                Aggregation.sample(safeCount)
        );
        return mongoTemplate.aggregate(aggregation, COLLECTION, BingoGamePrompt.class).getMappedResults();
    }
}
