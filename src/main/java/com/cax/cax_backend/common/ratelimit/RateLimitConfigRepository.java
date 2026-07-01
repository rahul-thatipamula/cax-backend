package com.cax.cax_backend.common.ratelimit;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface RateLimitConfigRepository extends MongoRepository<RateLimitConfig, String> {
}
