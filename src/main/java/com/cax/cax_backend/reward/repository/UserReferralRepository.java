package com.cax.cax_backend.reward.repository;

import com.cax.cax_backend.reward.model.UserReferral;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserReferralRepository extends MongoRepository<UserReferral, String> {
    Optional<UserReferral> findByReferralCode(String referralCode);
}
