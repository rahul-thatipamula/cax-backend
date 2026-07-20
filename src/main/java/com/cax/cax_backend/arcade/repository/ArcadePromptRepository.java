package com.cax.cax_backend.arcade.repository;

import com.cax.cax_backend.arcade.model.ArcadeGameType;
import com.cax.cax_backend.arcade.model.ArcadePrompt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ArcadePromptRepository extends MongoRepository<ArcadePrompt, String> {

    List<ArcadePrompt> findByGameTypeAndActiveTrue(ArcadeGameType gameType);

    long countByGameType(ArcadeGameType gameType);
}
