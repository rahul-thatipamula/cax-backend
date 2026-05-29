package com.cax.cax_backend.word.repository;

import com.cax.cax_backend.word.model.WordOfTheDay;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface WordRepository extends MongoRepository<WordOfTheDay, String> {
    Optional<WordOfTheDay> findByActiveTrue();
}
