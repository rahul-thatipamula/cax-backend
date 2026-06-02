package com.cax.cax_backend.newsletter.repository;

import com.cax.cax_backend.newsletter.model.NewsletterSubscription;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface NewsletterSubscriptionRepository extends MongoRepository<NewsletterSubscription, String> {
    Optional<NewsletterSubscription> findByEmail(String email);
}
