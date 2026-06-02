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
    private WalletEmbedded wallet;
    private SocialLinks socialLinks;

    private String fcmToken;

    @Builder.Default
    private boolean acceptedTerms = false;
    private Instant acceptedTermsAt;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;
    private Instant collegeAddedAt;
}
