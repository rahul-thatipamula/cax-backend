package com.cax.cax_backend.newsletter.service;

import com.cax.cax_backend.newsletter.event.NewsletterSubscribeEvent;
import com.cax.cax_backend.newsletter.model.NewsletterSubscription;
import com.cax.cax_backend.newsletter.repository.NewsletterSubscriptionRepository;
import com.cax.cax_backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NewsletterService {

    private final NewsletterSubscriptionRepository subscriptionRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"
    );

    /**
     * Subscribe a new email to the newsletter.
     */
    public NewsletterSubscription subscribe(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new BusinessException.BadRequestException("Email cannot be empty");
        }

        String cleanedEmail = email.trim().toLowerCase();

        // 1. Check email formation
        if (!EMAIL_PATTERN.matcher(cleanedEmail).matches()) {
            throw new BusinessException.BadRequestException("Invalid email format");
        }

        // 2. Check for duplicates
        if (subscriptionRepository.findByEmail(cleanedEmail).isPresent()) {
            throw new BusinessException.ResourceAlreadyExistsException("Newsletter subscription", cleanedEmail);
        }

        // 3. Save subscription
        NewsletterSubscription subscription = NewsletterSubscription.builder()
                .email(cleanedEmail)
                .createdAt(Instant.now())
                .build();

        NewsletterSubscription savedSubscription = subscriptionRepository.save(subscription);
        eventPublisher.publishEvent(new NewsletterSubscribeEvent(this, savedSubscription));
        return savedSubscription;
    }
}
