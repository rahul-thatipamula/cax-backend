package com.cax.cax_backend.thoughtsubscription.repository;

import com.cax.cax_backend.thoughtsubscription.model.ThoughtSubscription;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThoughtSubscriptionRepository extends MongoRepository<ThoughtSubscription, String> {
    List<ThoughtSubscription> findBySubscriberIdAndActiveTrueOrderBySubscribedAtDesc(String subscriberId);
    List<ThoughtSubscription> findByAuthorIdAndActiveTrue(String authorId);
    Optional<ThoughtSubscription> findBySubscriberIdAndAuthorId(String subscriberId, String authorId);
    boolean existsBySubscriberIdAndAuthorIdAndActiveTrue(String subscriberId, String authorId);
    long countByAuthorIdAndActiveTrue(String authorId);
}
