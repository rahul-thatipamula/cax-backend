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
    private boolean academicDetailsAdded = false;

    @Builder.Default
    private boolean idVerified = false;

    private String idCardImagePath;

    @Builder.Default
    private boolean isOnline = false;

    private CollegeDetails collegeDetails;
    private AcademicDetails academicDetails;

    @Builder.Default
    private double coins = 0.0;

    @Builder.Default
    private double totalCoinsEarned = 0.0;

    @Builder.Default
    private double totalCoinsSpent = 0.0;

    private SocialLinks socialLinks;

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
    private Instant idCardExpiresAt;

    @Builder.Default
    private boolean reVerificationRequested = false;

    @Builder.Default
    private int rejectionCount = 0;
}
