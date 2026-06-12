package com.cax.cax_backend.idcard.scheduler;

import com.cax.cax_backend.common.enums.CarouselEnums.VerificationStatus;
import com.cax.cax_backend.common.service.R2StorageService;
import com.cax.cax_backend.idcard.model.IDCard;
import com.cax.cax_backend.idcard.repository.IDCardRepository;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Cleanup scheduler that automatically deletes verified/rejected ID card files from Cloudflare R2
 * after 24 hours of verification.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IDCardCleanupScheduler {

    private final IDCardRepository idCardRepository;
    private final UserRepository userRepository;
    private final R2StorageService r2StorageService;

    // Runs once every hour
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupVerifiedIDCards() {
        log.info("Starting scheduled cleanup of verified ID card files...");
        Instant cutoffTime = Instant.now().minus(24, ChronoUnit.HOURS);

        // Find all ID cards approved or rejected more than 24 hours ago that still have an image URL
        List<IDCard> cardsToClean = idCardRepository.findAll().stream()
                .filter(card -> card.getStatus() != VerificationStatus.PENDING)
                .filter(card -> card.getVerifiedAt() != null && card.getVerifiedAt().isBefore(cutoffTime))
                .filter(card -> card.getImageUrl() != null && !card.getImageUrl().isEmpty())
                .toList();

        if (cardsToClean.isEmpty()) {
            log.info("No verified ID cards found requiring file deletion.");
            return;
        }

        log.info("Found {} ID cards ready for file deletion. Processing...", cardsToClean.size());

        for (IDCard card : cardsToClean) {
            try {
                String imageUrl = card.getImageUrl();
                log.info("Deleting ID card document from storage for user {}: {}", card.getUserId(), imageUrl);
                
                // Delete physical file from R2
                r2StorageService.deleteFile(imageUrl);

                // Clear document fields from IDCard and User
                card.setImageUrl(null);
                idCardRepository.save(card);

                userRepository.findByUserId(card.getUserId()).ifPresent(user -> {
                    user.setIdCardImagePath(null);
                    userRepository.save(user);
                });

                log.info("Successfully cleaned up ID card files for user {}", card.getUserId());
            } catch (Exception e) {
                log.error("Failed to delete ID card storage files for user: {}", card.getUserId(), e);
            }
        }
    }
}
