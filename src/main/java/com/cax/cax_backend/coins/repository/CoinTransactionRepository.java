package com.cax.cax_backend.coins.repository;

import com.cax.cax_backend.coins.model.CoinTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CoinTransactionRepository extends MongoRepository<CoinTransaction, String> {

    List<CoinTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
