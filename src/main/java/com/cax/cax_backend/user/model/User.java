package com.cax.cax_backend.user.model;

import com.cax.cax_backend.common.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String googleId;

    @Indexed
    private String email;

    private String name;
    private String picture;
    private String coverPicture;

    @Builder.Default
    private UserRole role = UserRole.STUDENT;

    @Builder.Default
    private boolean collegeDetailsAdded = false;

    @Builder.Default
    private boolean idVerified = false;

    @Builder.Default
    private boolean isOnline = false;

    private CollegeDetails collegeDetails;

    @Builder.Default
    private double coins = 0.0;

    @Builder.Default
    private double totalCoinsEarned = 0.0;

    @Builder.Default
    private double totalCoinsSpent = 0.0;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String fcmToken;

    private Instant lastSeenFeedAt;
    private Instant lastNotificationSentAt;

    private Instant lastLoginAt;
    private Instant lastLogoutAt;
    private Instant lastSeenAt;

    @Builder.Default
    private boolean acceptedTerms = false;
    private Instant acceptedTermsAt;

    @Builder.Default
    private boolean twoFactorEnabled = false;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String twoFactorSecret;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<String> refreshTokens;

    private Instant updatedAt;
    private Instant collegeAddedAt;

    @Builder.Default
    private boolean blocked = false;

    private String blockReason;

    private Instant blockedAt;

    // Premium Subscription details
    private Instant premiumExpiresAt;
    private String premiumPack;
    private String premiumCardTheme;
    private String premiumMusicLink;

    // CAX Verification details
    private String caxId;

    // Student ID verification submission
    private String studentIdUrl;
    private Instant verificationSubmittedAt;

    // How this account got verified: DOMAIN_MATCH (college email) or
    // MANUAL_ID_CARD (admin-reviewed ID upload). Null for legacy accounts,
    // which are treated as DOMAIN_MATCH.
    private VerificationMethod verificationMethod;

    // Manual-track routing state mirrored from the latest manualVerifications
    // record so the mobile app can route from /auth/user alone:
    // NOT_SUBMITTED, PENDING, APPROVED, REJECTED, REVERIFY_REQUIRED, EXPIRED
    private String manualVerificationStatus;

    // For MANUAL_ID_CARD accounts: approval is valid until the next July 19.
    private Instant verificationValidUntil;

    public enum VerificationMethod {
        DOMAIN_MATCH, MANUAL_ID_CARD
    }

    private String appVersion;
    private int buildNumber;

    @Builder.Default
    private boolean waterReminderSubscribed = false;
    private Instant lastWaterReminderSentAt;

    private String displayName;

    public String getThoughtsDisplayName() {
        if (this.displayName != null && !this.displayName.isBlank()) {
            return this.displayName;
        }
        return com.cax.cax_backend.common.util.PseudonymUtils.generatePseudonym(this.userId);
    }
}
