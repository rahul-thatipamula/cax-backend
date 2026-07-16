package com.cax.cax_backend.auth.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.cax.cax_backend.college.model.College;
import com.cax.cax_backend.college.repository.CollegeRepository;
import com.cax.cax_backend.common.enums.UserRole;
import com.cax.cax_backend.common.exception.AuthException;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.common.util.JwtUtil;
import com.cax.cax_backend.common.util.EncryptionUtils;
import com.cax.cax_backend.common.util.EmailDomainUtils;
import com.cax.cax_backend.common.util.TotpUtil;

import com.cax.cax_backend.user.event.CollegeSelectedEvent;
import com.cax.cax_backend.user.event.UserProfileUpdatedEvent;
import com.cax.cax_backend.user.event.UserSignupEvent;
import com.cax.cax_backend.user.model.CollegeDetails;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.cax.cax_backend.settings.service.SystemSettingService;
import com.cax.cax_backend.collegereport.service.CollegeReportService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthService {

    // Owner/developer account — bypasses the academic-email-domain requirement at login and
    // is auto-promoted to ADMIN on every login. Scoped to this single email only; every other
    // account still goes through the normal college-domain verification and role assignment.
    // TODO: move to environment config (tracked in docs/SECURITY_AUDIT_2026-06-23.md, open item #1).
    private static final String OWNER_BYPASS_EMAIL = "rahulthatipamula97@gmail.com";

    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final JwtUtil jwtUtil;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final ApplicationEventPublisher eventPublisher;
    private final SystemSettingService systemSettingService;
    private final CollegeReportService collegeReportService;

    public AuthService(UserRepository userRepository, CollegeRepository collegeRepository, JwtUtil jwtUtil, GoogleIdTokenVerifier googleIdTokenVerifier, ApplicationEventPublisher eventPublisher, SystemSettingService systemSettingService, CollegeReportService collegeReportService) {
        this.userRepository = userRepository;
        this.collegeRepository = collegeRepository;
        this.jwtUtil = jwtUtil;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.eventPublisher = eventPublisher;
        this.systemSettingService = systemSettingService;
        this.collegeReportService = collegeReportService;
    }

    /**
     * Google login/signup via Google ID token verification.
     */
    public Map<String, Object> handleGoogleLoginOrSignup(String googleIdTokenStr) {
        try {
            if (googleIdTokenStr != null && googleIdTokenStr.regionMatches(true, 0, "Bearer ", 0, 7)) {
                googleIdTokenStr = googleIdTokenStr.substring(7).trim();
            }
            GoogleIdToken decodedToken = googleIdTokenVerifier.verify(googleIdTokenStr);
            if (decodedToken == null) {
                throw new AuthException.InvalidTokenException("Google ID token signature verification failed");
            }

            GoogleIdToken.Payload payload = decodedToken.getPayload();
            String uid = payload.getSubject();
            String email = payload.getEmail();
            if (email != null) {
                email = email.toLowerCase().trim();
            }
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            if (email == null || email.isBlank()) {
                throw new AuthException.InvalidTokenException("Email not found in token");
            }

            int atIndex = email.indexOf('@');
            String domain = atIndex != -1 ? email.substring(atIndex + 1) : "";

            // Find existing user by googleId or email
            final String finalEmail = email;
            User user = userRepository.findByGoogleId(uid)
                    .or(() -> userRepository.findByEmail(finalEmail))
                    .orElse(null);

            if (user != null && user.getName() != null) {
                try {
                    user.setName(EncryptionUtils.decrypt(user.getName()));
                } catch (Exception e) {
                    log.warn("Failed to decrypt user name on load: {}", e.getMessage());
                }
            }

            boolean isOwnerAccount = OWNER_BYPASS_EMAIL.equalsIgnoreCase(email);
            boolean isAdmin = (user != null && user.getRole() == UserRole.ADMIN) || isOwnerAccount;

            boolean isPersonalDomain = isPersonalEmailDomain(domain);

            // Personal-email users are no longer blocked: they log in and go
            // through the manual ID-card verification track instead.
            boolean manualVerificationTrack = false;
            College matchedCollege;
            if (!isAdmin && isPersonalDomain) {
                if (systemSettingService.isPlayStoreTestingEnabled()) {
                    matchedCollege = getOrCreateCAXoneCollege();
                    log.info("Play Store testing: personal email '{}' assigned to CAXone College", email);
                } else {
                    matchedCollege = null;
                    manualVerificationTrack = true;
                    log.info("Personal email '{}' logging in via manual-verification track", email);
                }
            } else {
                matchedCollege = findMatchedCollege(domain);
                if (!isAdmin && matchedCollege == null) {
                    log.warn("No college match found for domain '{}'. User email: {}", domain, email);
                    throw new AuthException.ForbiddenException("College details not added yet. We haven't registered your college email domain on CAX yet.");
                }
            }

            boolean isNewUser = false;
            if (user == null) {
                isNewUser = true;
                String initialName = name != null ? name : email.split("@")[0];
                User.UserBuilder userBuilder = User.builder()
                        .userId(uid)
                        .googleId(uid)
                        .email(email)
                        .name(com.cax.cax_backend.common.util.EncryptionUtils.encrypt(initialName))
                        .displayName(generateUniqueNickname(email, initialName))
                        .picture(picture)
                        .role(UserRole.STUDENT)
                        .acceptedTerms(false)
                        .acceptedTermsAt(null)
                        .isOnline(true)
                        .lastLoginAt(Instant.now())
                        .lastSeenAt(Instant.now());

                user = userBuilder.build();
                if (manualVerificationTrack) {
                    user.setVerificationMethod(User.VerificationMethod.MANUAL_ID_CARD);
                    user.setManualVerificationStatus("NOT_SUBMITTED");
                }
                boolean collegeAssigned = updateCollegeDetailsIfMatched(user, matchedCollege);
                if (collegeAssigned) {
                    log.info("Auto-assigned college '{}' to new user: {}", matchedCollege.getCollegeName(), user.getUserId());
                }
                user = userRepository.save(user);
                if (user.getName() != null) {
                    user.setName(com.cax.cax_backend.common.util.EncryptionUtils.decrypt(user.getName()));
                }
                log.info("New user created: {}", uid);
                eventPublisher.publishEvent(new UserSignupEvent(this, user));
                if (collegeAssigned) {
                    eventPublisher.publishEvent(new CollegeSelectedEvent(this, user));
                }
            } else {
                if (user.getGoogleId() == null || user.getGoogleId().isEmpty()) {
                    user.setGoogleId(uid);
                }
                if (picture != null && !picture.equals(user.getPicture())) {
                    user.setPicture(picture);
                }

                if (manualVerificationTrack && user.getVerificationMethod() == null) {
                    // Legacy personal-email account (created before the manual track
                    // existed, e.g. via testing mode) — pull it onto the manual track.
                    user.setVerificationMethod(User.VerificationMethod.MANUAL_ID_CARD);
                    if (user.getManualVerificationStatus() == null) {
                        user.setManualVerificationStatus(user.isIdVerified() ? "APPROVED" : "NOT_SUBMITTED");
                    }
                }
                boolean collegeHealed = updateCollegeDetailsIfMatched(user, matchedCollege);
                if (collegeHealed) {
                    log.info("Auto-healed college '{}' for existing user: {}", matchedCollege.getCollegeName(), user.getUserId());
                }

                healPlainTextEncryption(user);

                if (user.getDisplayName() == null || user.getDisplayName().isEmpty()) {
                    user.setDisplayName(generateUniqueNickname(user.getEmail(), user.getName()));
                }

                user.setOnline(true);
                user.setLastLoginAt(Instant.now());
                user.setLastSeenAt(Instant.now());
                user.setUpdatedAt(Instant.now());
                
                if (user.getName() != null) {
                    user.setName(com.cax.cax_backend.common.util.EncryptionUtils.encrypt(user.getName()));
                }
                user = userRepository.save(user);
                if (user.getName() != null) {
                    user.setName(com.cax.cax_backend.common.util.EncryptionUtils.decrypt(user.getName()));
                }
                
                log.info("Existing user logged in: {}", user.getUserId());
                if (collegeHealed) {
                    eventPublisher.publishEvent(new CollegeSelectedEvent(this, user));
                }
            }

            // Owner account: persist ADMIN role so every downstream check that reads
            // user.getRole() directly (not just the JWT's isAdmin claim) treats this account
            // as admin consistently, on this and every future login.
            if (isOwnerAccount && user.getRole() != UserRole.ADMIN) {
                user.setRole(UserRole.ADMIN);
                user = userRepository.save(user);
                log.info("Owner account {} auto-promoted to ADMIN role on login", user.getUserId());
            }

            // Ensure CAX ID is assigned immediately on every login
            if (user.isIdVerified() && (user.getCaxId() == null || user.getCaxId().isEmpty())) {
                user.setCaxId(generateUniqueCaxId());
                if (user.getName() != null) {
                    user.setName(com.cax.cax_backend.common.util.EncryptionUtils.encrypt(user.getName()));
                }
                user = userRepository.save(user);
                if (user.getName() != null) {
                    user.setName(com.cax.cax_backend.common.util.EncryptionUtils.decrypt(user.getName()));
                }
                log.info("CAX ID generated for user {} on login", user.getUserId());
            }

            boolean hasElevatedAccess = user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified());

            if (user.isTwoFactorEnabled()) {
                String tempToken = jwtUtil.generateTemp2FaToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), hasElevatedAccess);
                return Map.of(
                        "success", true,
                        "twoFactorRequired", true,
                        "tempToken", tempToken,
                        "message", "Two-factor authentication required"
                );
            }

            String token = jwtUtil.generateToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), hasElevatedAccess, user.getCreatedAt());
            String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), hasElevatedAccess);

            if (user.getRefreshTokens() == null) {
                user.setRefreshTokens(new ArrayList<>());
            }
            user.getRefreshTokens().add(EncryptionUtils.hashSHA256(refreshToken));
            
            if (user.getName() != null) {
                user.setName(com.cax.cax_backend.common.util.EncryptionUtils.encrypt(user.getName()));
            }
            userRepository.save(user);
            if (user.getName() != null) {
                user.setName(com.cax.cax_backend.common.util.EncryptionUtils.decrypt(user.getName()));
            }

            Map<String, String> redirectInfo = determineRedirect(user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("token", token);
            result.put("refreshToken", refreshToken);
            result.put("userId", user.getUserId());
            result.put("message", redirectInfo.get("message"));
            result.put("redirect", redirectInfo.get("redirect"));
            result.put("user", user);
            result.put("isNewUser", isNewUser);
            return result;

        } catch (AuthException.InvalidTokenException | AuthException.ForbiddenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google login failed — cause: {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            throw new AuthException.InvalidTokenException("Google login failed: " + e.getMessage());
        }
    }

    /**
     * Get user by JWT token.
     */
    public User getUser(String token) {
        String userId = jwtUtil.extractUserId(token);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);
        if (user.getName() != null) {
            try {
                user.setName(com.cax.cax_backend.common.util.EncryptionUtils.decrypt(user.getName()));
            } catch (Exception e) {
                // Ignore
            }
        }

        String email = user.getEmail() != null ? user.getEmail().toLowerCase().trim() : "";
        int atIndex = email.indexOf('@');
        String domain = atIndex != -1 ? email.substring(atIndex + 1) : "";
        boolean isAdmin = user.getRole() == UserRole.ADMIN;
        boolean isCAXone = systemSettingService.isPlayStoreTestingEnabled() && isCAXoneUser(user);
        // Personal-email accounts go through the manual ID-card track; they are
        // allowed in but stay unverified until an admin approves their ID card.
        boolean isManualTrack = user.getVerificationMethod() == User.VerificationMethod.MANUAL_ID_CARD
                || isPersonalEmailDomain(domain);

        user = getUserAndHealIfVerified(user);
        if (!isAdmin && !isCAXone && !isManualTrack && !hasCollegeDetails(user)) {
            throw new AuthException.ForbiddenException("College details not added yet. We haven't registered your college email domain on CAX yet.");
        }
        return user;
    }

    /**
     * Update FCM token.
     */
    public void updateFcmToken(String userId, String fcmToken) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);
        user.setFcmToken(EncryptionUtils.encrypt(fcmToken));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        log.info("FCM token updated for user: {}", userId);
    }

    /**
     * Update user profile fields.
     */
    public User updateUser(String token, Map<String, Object> updates) {
        String userId = jwtUtil.extractUserId(token);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);

        if (user.getName() != null) {
            try {
                user.setName(com.cax.cax_backend.common.util.EncryptionUtils.decrypt(user.getName()));
            } catch (Exception e) {
                // Ignore
            }
        }

        if (updates.containsKey("name") && updates.get("name") != null) {
            String newNickname = ((String) updates.get("name")).trim();
            if (!com.cax.cax_backend.common.util.PseudonymUtils.isValidNickname(newNickname)) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException(
                        "Nickname must be 8-16 characters and contain only letters, numbers, _, and .");
            }
            if (com.cax.cax_backend.common.util.ProfanityFilter.isOffensive(newNickname)) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Inappropriate nickname detected");
            }
            java.util.Optional<User> existing = userRepository.findByDisplayNameIgnoreCase(newNickname);
            if (existing.isPresent() && !existing.get().getUserId().equals(userId)) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Nickname is already taken");
            }
            user.setDisplayName(newNickname);
        }
        if (updates.containsKey("picture") && updates.get("picture") != null) {
            user.setPicture((String) updates.get("picture"));
        }
        if (updates.containsKey("coverPicture") && updates.get("coverPicture") != null) {
            user.setCoverPicture((String) updates.get("coverPicture"));
        }
        if (updates.containsKey("acceptedTerms")) {
            Object acceptedVal = updates.get("acceptedTerms");
            if (acceptedVal != null) {
                user.setAcceptedTerms((Boolean) acceptedVal);
                if (Boolean.TRUE.equals(acceptedVal)) {
                    user.setAcceptedTermsAt(Instant.now());
                }
            }
        }
        // Premium fields are server-controlled only — never accept from client

        user.setUpdatedAt(Instant.now());
        if (user.getName() != null) {
            user.setName(com.cax.cax_backend.common.util.EncryptionUtils.encrypt(user.getName()));
        }
        user = userRepository.save(user);
        if (user.getName() != null) {
            user.setName(com.cax.cax_backend.common.util.EncryptionUtils.decrypt(user.getName()));
        }
        log.info("User profile updated for: {}", userId);
        eventPublisher.publishEvent(new UserProfileUpdatedEvent(this, user));
        return user;
    }

    /**
     * Generate test token (dev only).
     */
    public Map<String, Object> generateTestToken(String userId) {
        User user = userRepository.findByUserId(userId)
                .or(() -> userRepository.findById(userId))
                .orElseGet(() -> {
                    log.info("Test user not found, generating mock user: {}", userId);
                    UserRole defaultRole = userId.toLowerCase().contains("super_student")
                            ? UserRole.SUPER_STUDENT
                            : UserRole.STUDENT;
                    User newUser = User.builder()
                            .userId(userId)
                            .googleId("mock_google_" + userId)
                            .email(userId + "@cax.edu")
                            .name(userId.substring(0, 1).toUpperCase() + userId.substring(1).replace("_", " "))
                            .role(defaultRole)
                            .collegeDetailsAdded(true)
                            .collegeDetails(com.cax.cax_backend.user.model.CollegeDetails.builder()
                                    .collegeId("test_college_1")
                                    .collegeName("CAX State University")
                                    .collegeCode("CAXU")
                                    .location("CAX HQ")
                                    .build())
                            .idVerified(defaultRole == UserRole.SUPER_STUDENT)
                            .acceptedTerms(true)
                            .acceptedTermsAt(Instant.now())
                            .createdAt(Instant.now())
                            .build();
                    return userRepository.save(newUser);
                });

        boolean hasElevatedAccess = user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified());
        String token = jwtUtil.generateToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), hasElevatedAccess, user.getCreatedAt());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), hasElevatedAccess);

        if (user.getRefreshTokens() == null) {
            user.setRefreshTokens(new ArrayList<>());
        }
        user.getRefreshTokens().add(EncryptionUtils.hashSHA256(refreshToken));

        user.setOnline(true);
        user.setLastLoginAt(Instant.now());
        user.setLastSeenAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("user", Map.of(
                "id", user.getUserId(),
                "email", user.getEmail(),
                "name", user.getName() != null ? user.getName() : "User",
                "role", user.getRole().getValue()
        ));
        result.put("message", "Test token generated successfully");
        return result;
    }

    /**
     * Get user's selected college.
     */
    public College getSelectedCollege(String token) {
        String userId = jwtUtil.extractUserId(token);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);
        if (!user.isCollegeDetailsAdded() || user.getCollegeDetails() == null) {
            return null;
        }
        return collegeRepository.findById(user.getCollegeDetails().getCollegeId()).orElse(null);
    }

    public Map<String, Object> refresh(String refreshToken) {
        try {
            io.jsonwebtoken.Claims claims = jwtUtil.verifyToken(refreshToken);
            String tokenType = claims.get("type", String.class);
            if (!"refresh".equals(tokenType)) {
                throw new AuthException.InvalidTokenException("Token is not a refresh token");
            }

            String userId = claims.get("userId", String.class);
            User user = userRepository.findByUserId(userId)
                    .orElseThrow(AuthException.UserNotFoundException::new);

            String tokenHash = EncryptionUtils.hashSHA256(refreshToken);
            boolean containsLegacy = user.getRefreshTokens() != null && user.getRefreshTokens().contains(refreshToken);
            boolean containsHashed = user.getRefreshTokens() != null && user.getRefreshTokens().contains(tokenHash);

            if (!containsLegacy && !containsHashed) {
                throw new AuthException.InvalidTokenException("Refresh token is invalid or has been revoked");
            }

            if (containsLegacy) {
                user.getRefreshTokens().remove(refreshToken);
            } else {
                user.getRefreshTokens().remove(tokenHash);
            }

            // Personal-email users refresh normally — verification gating is handled
            // through the manual ID-card track, not by blocking the session.
            boolean hasElevatedAccess = user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified());
            String newAccessToken = jwtUtil.generateToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), hasElevatedAccess, user.getCreatedAt());
            String newRefreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), hasElevatedAccess);

            user.getRefreshTokens().add(EncryptionUtils.hashSHA256(newRefreshToken));
            userRepository.save(user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("token", newAccessToken);
            result.put("refreshToken", newRefreshToken);
            return result;
        } catch (Exception e) {
            throw new AuthException.InvalidTokenException("Refresh token failed: " + e.getMessage());
        }
    }

    public void invalidateTokens(String token) {
        try {
            String userId = jwtUtil.extractUserId(token);
            userRepository.findByUserId(userId).ifPresent(user -> {
                user.setRefreshTokens(new java.util.ArrayList<>());
                user.setOnline(false);
                user.setLastLogoutAt(Instant.now());
                userRepository.save(user);
            });
        } catch (Exception e) {
            log.error("Failed to invalidate tokens on logout", e);
        }
    }

    public Map<String, Object> generate2FaSetup(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);

        String secret = TotpUtil.generateSecretKey();
        user.setTwoFactorSecret(EncryptionUtils.encrypt(secret));
        userRepository.save(user);

        String qrCodeUrl = TotpUtil.getQrCodeUrl(user.getEmail(), secret, "CAX");

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("secretKey", secret);
        result.put("qrCodeUrl", qrCodeUrl);
        return result;
    }

    public Map<String, Object> enable2Fa(String userId, String code) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);

        if (user.getTwoFactorSecret() == null || user.getTwoFactorSecret().isBlank()) {
            throw new BusinessException.BadRequestException("2FA setup not initiated");
        }

        boolean verified = TotpUtil.verifyCode(EncryptionUtils.decrypt(user.getTwoFactorSecret()), code, 1);
        if (!verified) {
            throw new BusinessException.BadRequestException("Invalid verification code");
        }

        user.setTwoFactorEnabled(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Two-factor authentication enabled successfully");
        return result;
    }

    public Map<String, Object> disable2Fa(String userId, String code) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);

        if (!user.isTwoFactorEnabled()) {
            throw new BusinessException.BadRequestException("Two-factor authentication is not enabled");
        }

        boolean verified = TotpUtil.verifyCode(EncryptionUtils.decrypt(user.getTwoFactorSecret()), code, 1);
        if (!verified) {
            throw new BusinessException.BadRequestException("Invalid verification code");
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Two-factor authentication disabled successfully");
        return result;
    }

    public Map<String, Object> verify2FaLogin(String tempToken, String code) {
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtUtil.verifyToken(tempToken);
        } catch (Exception e) {
            throw new AuthException.InvalidTokenException("Invalid or expired 2FA token");
        }

        String type = claims.get("type", String.class);
        if (!"temp_2fa".equals(type)) {
            throw new AuthException.InvalidTokenException("Invalid token type");
        }

        String userId = claims.get("userId", String.class);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);

        boolean verified = TotpUtil.verifyCode(EncryptionUtils.decrypt(user.getTwoFactorSecret()), code, 1);
        if (!verified) {
            throw new BusinessException.BadRequestException("Invalid verification code");
        }

        // Ensure CAX ID is assigned immediately on 2FA login completion
        if (user.isIdVerified() && (user.getCaxId() == null || user.getCaxId().isEmpty())) {
            user.setCaxId(generateUniqueCaxId());
            log.info("CAX ID generated for user {} on 2FA login", user.getUserId());
        }

        boolean hasElevatedAccess = user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified());
        String token = jwtUtil.generateToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), hasElevatedAccess, user.getCreatedAt());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), hasElevatedAccess);

        if (user.getRefreshTokens() == null) {
            user.setRefreshTokens(new ArrayList<>());
        }
        user.getRefreshTokens().add(EncryptionUtils.hashSHA256(refreshToken));

        user.setOnline(true);
        user.setLastLoginAt(Instant.now());
        user.setLastSeenAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        Map<String, String> redirectInfo = determineRedirect(user);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("userId", user.getUserId());
        result.put("message", redirectInfo.get("message"));
        result.put("redirect", redirectInfo.get("redirect"));
        result.put("user", user);
        return result;
    }

    private static final String CAXONE_COLLEGE_CODE = "CAXONE";

    private boolean isPersonalEmailDomain(String domain) {
        return EmailDomainUtils.isPersonalEmailDomain(domain);
    }

    private College getOrCreateCAXoneCollege() {
        return collegeRepository.findByCollegeCode(CAXONE_COLLEGE_CODE).orElseGet(() -> {
            College caxone = College.builder()
                    .collegeName("CAXone")
                    .collegeCode(CAXONE_COLLEGE_CODE)
                    .location("India")
                    .university("CAX Platform")
                    .type("Platform")
                    .build();
            College saved = collegeRepository.save(caxone);
            log.info("Created CAXone College with id: {}", saved.getId());
            return saved;
        });
    }

    private boolean isCAXoneUser(User user) {
        return user.getCollegeDetails() != null
                && CAXONE_COLLEGE_CODE.equalsIgnoreCase(user.getCollegeDetails().getCollegeCode());
    }

    private boolean healPlainTextEncryption(User user) {
        boolean changed = false;
        if (user.getFcmToken() != null && !user.getFcmToken().startsWith("v2:")) {
            user.setFcmToken(EncryptionUtils.encrypt(user.getFcmToken()));
            changed = true;
        }
        if (user.getTwoFactorSecret() != null && !user.getTwoFactorSecret().startsWith("v2:")) {
            user.setTwoFactorSecret(EncryptionUtils.encrypt(user.getTwoFactorSecret()));
            changed = true;
        }
        if (changed) {
            log.info("Re-encrypted plain-text fields for user: {}", user.getUserId());
        }
        return changed;
    }

    private College findMatchedCollege(String domain) {
        List<College> activeColleges = collegeRepository.findByIsActiveTrue();
        for (College college : activeColleges) {
            if (college.getEmailDomains() != null && !college.getEmailDomains().isEmpty()) {
                for (String rawDomain : college.getEmailDomains()) {
                    if (rawDomain == null || rawDomain.isBlank()) continue;
                    String baseDomain = rawDomain.trim().toLowerCase();
                    if (baseDomain.startsWith("@")) {
                        baseDomain = baseDomain.substring(1);
                    }
                    if (domain.equalsIgnoreCase(baseDomain) || domain.endsWith("." + baseDomain)) {
                        return college;
                    }
                }
            }
        }
        return null;
    }

    private boolean updateCollegeDetailsIfMatched(User user, College matchedCollege) {
        boolean needsCollegeUpdate = !user.isCollegeDetailsAdded()
                || user.getCollegeDetails() == null
                || user.getCollegeDetails().getCollegeId() == null
                || user.getCollegeDetails().getCollegeId().isEmpty()
                || (matchedCollege != null && !matchedCollege.getId().equals(user.getCollegeDetails().getCollegeId()));
        if (matchedCollege != null && needsCollegeUpdate) {
            boolean isFirstAssignment = !hasCollegeDetails(user);
            user.setCollegeDetailsAdded(true);
            user.setCollegeDetails(CollegeDetails.builder()
                    .collegeId(matchedCollege.getId())
                    .collegeName(matchedCollege.getCollegeName())
                    .collegeCode(matchedCollege.getCollegeCode())
                    .location(matchedCollege.getLocation())
                    .build());
            if (isFirstAssignment || user.getCollegeAddedAt() == null) {
                user.setCollegeAddedAt(Instant.now());
            }
            user.setUpdatedAt(Instant.now());
            user.setIdVerified(true);
            user.setVerificationMethod(User.VerificationMethod.DOMAIN_MATCH);
            if (user.getCaxId() == null || user.getCaxId().isEmpty()) {
                user.setCaxId(generateUniqueCaxId());
            }
            return true;
        }
        return false;
    }

    private boolean hasCollegeDetails(User user) {
        return user.isCollegeDetailsAdded()
                && user.getCollegeDetails() != null
                && user.getCollegeDetails().getCollegeId() != null
                && !user.getCollegeDetails().getCollegeId().isBlank();
    }

    private Map<String, String> determineRedirect(User user) {
        String redirect;
        String message;
        if (!user.isAcceptedTerms()) {
            redirect = "/terms-acceptance";
            message = "Terms acceptance required.";
        } else if (isPendingManualVerification(user)) {
            redirect = "/manual-verification";
            message = "Student ID verification required.";
        } else {
            redirect = "/app";
            message = "Login Successful.";
        }
        return Map.of("redirect", redirect, "message", message);
    }

    /** Manual-track user who isn't (or is no longer) verified. */
    private boolean isPendingManualVerification(User user) {
        if (user.getRole() == UserRole.ADMIN) return false;
        if (user.getVerificationMethod() != User.VerificationMethod.MANUAL_ID_CARD) return false;
        // REVERIFY_REQUIRED users keep full access during the grace period.
        return !user.isIdVerified();
    }

    private User getUserAndHealIfVerified(User user) {
        if (user == null) return null;
        boolean needsSave = false;

        if (!hasCollegeDetails(user)) {
            String email = user.getEmail() != null ? user.getEmail().toLowerCase().trim() : "";
            int atIndex = email.indexOf('@');
            String domain = atIndex != -1 ? email.substring(atIndex + 1) : "";
            College matchedCollege = findMatchedCollege(domain);
            if (matchedCollege != null && updateCollegeDetailsIfMatched(user, matchedCollege)) {
                needsSave = true;
                log.info("Auto-healed college details for user: {} with college: {}", user.getUserId(), matchedCollege.getCollegeName());
                eventPublisher.publishEvent(new CollegeSelectedEvent(this, user));
            }
        }

        if (user.isIdVerified()) {
            if (user.getCaxId() == null || user.getCaxId().isEmpty()) {
                user.setCaxId(generateUniqueCaxId());
                needsSave = true;
            }
        }
        if (needsSave) {
            user = userRepository.save(user);
        }
        return user;
    }

    private String generateUniqueCaxId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.util.Random rnd = new java.util.Random();
        String caxId;
        do {
            StringBuilder sb = new StringBuilder("CX");
            for (int i = 0; i < 8; i++) {
                sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
            caxId = sb.toString();
        } while (userRepository.existsByCaxId(caxId));
        return caxId;
    }

    /**
     * Preview the college that would be mapped for a Google ID token — no account created.
     */
    public Map<String, Object> previewCollege(String googleIdTokenStr) {
        GoogleIdToken.Payload payload = verifyGoogleIdToken(googleIdTokenStr);
        String email = payload.getEmail().toLowerCase().trim();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");
        int atIndex = email.indexOf('@');
        String domain = atIndex != -1 ? email.substring(atIndex + 1) : "";

        String uid = payload.getSubject();
        User existingDbUser = userRepository.findByGoogleId(uid)
                .or(() -> userRepository.findByEmail(email))
                .orElse(null);
        boolean existingUser = existingDbUser != null;

        boolean isExistingAdmin = existingDbUser != null && existingDbUser.getRole() == UserRole.ADMIN;
        boolean existingIsCAXone = existingDbUser != null && isCAXoneUser(existingDbUser);
        boolean isPersonalDomain = isPersonalEmailDomain(domain);

        // If existing non-privileged user, also validate their stored domain
        if (existingDbUser != null && !isExistingAdmin && !existingIsCAXone) {
            String storedEmail = existingDbUser.getEmail() != null
                    ? existingDbUser.getEmail().toLowerCase().trim()
                    : email;
            int storedAt = storedEmail.indexOf('@');
            String storedDomain = storedAt != -1 ? storedEmail.substring(storedAt + 1) : "";
            if (isPersonalEmailDomain(storedDomain)) {
                isPersonalDomain = true;
            }
        }

        boolean hasPendingReport = collegeReportService.hasPendingReport(email);

        College matched;
        if (isPersonalDomain && !isExistingAdmin && systemSettingService.isPlayStoreTestingEnabled()) {
            matched = getOrCreateCAXoneCollege();
            isPersonalDomain = false;
        } else {
            matched = findMatchedCollege(domain);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("email", email);
        result.put("name", name);
        result.put("picture", picture);
        result.put("domain", domain);
        result.put("existingUser", existingUser);
        result.put("isAcademicDomain", !isPersonalDomain || isExistingAdmin || existingIsCAXone);
        result.put("hasPendingReport", hasPendingReport);
        result.put("collegeFound", matched != null);
        if (matched != null) {
            result.put("collegeId", matched.getId());
            result.put("collegeName", matched.getCollegeName());
            result.put("collegeCode", matched.getCollegeCode());
            result.put("location", matched.getLocation());
            result.put("university", matched.getUniversity());
            result.put("type", matched.getType());
            result.put("logoUrl", matched.getLogoUrl());
            result.put("studentCount", matched.getStudentCount());
        }
        return result;
    }

    /**
     * Save a wrong-college report — no account created.
     */
    public void reportWrongCollege(String googleIdTokenStr, String reason) {
        GoogleIdToken.Payload payload = verifyGoogleIdToken(googleIdTokenStr);
        String email = payload.getEmail().toLowerCase().trim();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");
        int atIndex = email.indexOf('@');
        String domain = atIndex != -1 ? email.substring(atIndex + 1) : "";

        College matched = findMatchedCollege(domain);
        String collegeName = matched != null ? matched.getCollegeName() : null;
        String collegeId = matched != null ? matched.getId() : null;

        collegeReportService.createReport(email, domain, name, picture, collegeName, collegeId, reason);
        log.info("Wrong-college report saved for email={}", email);
    }

    /**
     * Submit a student ID card URL for admin verification review.
     */
    public User submitVerification(String token, String studentIdUrl) {
        String userId = jwtUtil.extractUserId(token);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("User"));
        if (user.isIdVerified()) {
            throw new BusinessException.BadRequestException("Account is already verified");
        }
        if (studentIdUrl == null || studentIdUrl.isBlank()) {
            throw new BusinessException.BadRequestException("Student ID image URL is required");
        }
        user.setStudentIdUrl(studentIdUrl.trim());
        user.setVerificationSubmittedAt(Instant.now());
        return userRepository.save(user);
    }

    private GoogleIdToken.Payload verifyGoogleIdToken(String tokenStr) {
        try {
            GoogleIdToken idToken = googleIdTokenVerifier.verify(tokenStr);
            if (idToken == null) throw new AuthException.InvalidTokenException("Google ID token verification failed");
            return idToken.getPayload();
        } catch (AuthException.InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException.InvalidTokenException("Google ID token signature verification failed");
        }
    }

    private String generateUniqueNickname(String email, String name) {
        String letters = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String all = letters + digits;
        java.util.Random rnd = new java.util.Random();

        String nickname;
        do {
            int length = 8 + rnd.nextInt(5); // 8–12 chars
            char[] chars = new char[length];
            // Guarantee at least 2 letters and 2 digits
            chars[0] = letters.charAt(rnd.nextInt(letters.length()));
            chars[1] = letters.charAt(rnd.nextInt(letters.length()));
            chars[2] = digits.charAt(rnd.nextInt(digits.length()));
            chars[3] = digits.charAt(rnd.nextInt(digits.length()));
            for (int i = 4; i < length; i++) {
                chars[i] = all.charAt(rnd.nextInt(all.length()));
            }
            // Fisher-Yates shuffle
            for (int i = length - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                char tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp;
            }
            nickname = new String(chars);
        } while (userRepository.existsByDisplayName(nickname));

        return nickname;
    }
}
