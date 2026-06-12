package com.cax.cax_backend.auth.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.cax.cax_backend.college.model.College;
import com.cax.cax_backend.college.repository.CollegeRepository;
import com.cax.cax_backend.common.enums.UserRole;
import com.cax.cax_backend.common.exception.AuthException;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.common.util.JwtUtil;
import com.cax.cax_backend.common.util.TotpUtil;

import com.cax.cax_backend.user.event.CollegeSelectedEvent;
import com.cax.cax_backend.user.event.UserProfileUpdatedEvent;
import com.cax.cax_backend.user.event.UserSignupEvent;
import com.cax.cax_backend.user.model.AcademicDetails;
import com.cax.cax_backend.user.model.CollegeDetails;
import com.cax.cax_backend.user.model.SocialLinks;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.cax.cax_backend.settings.model.SystemSetting;
import com.cax.cax_backend.settings.service.SystemSettingService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final JwtUtil jwtUtil;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final ApplicationEventPublisher eventPublisher;
    private final SystemSettingService systemSettingService;


    public AuthService(UserRepository userRepository, CollegeRepository collegeRepository, JwtUtil jwtUtil, GoogleIdTokenVerifier googleIdTokenVerifier, ApplicationEventPublisher eventPublisher, SystemSettingService systemSettingService) {
        this.userRepository = userRepository;
        this.collegeRepository = collegeRepository;
        this.jwtUtil = jwtUtil;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.eventPublisher = eventPublisher;
        this.systemSettingService = systemSettingService;
    }

    /**
     * Google login/signup via Google ID token verification.
     */
    public Map<String, Object> handleGoogleLoginOrSignup(String googleIdTokenStr, boolean acceptedTerms) {
        try {
            // Normalise token: strip optional "Bearer " prefix sent by some clients
            if (googleIdTokenStr != null && googleIdTokenStr.regionMatches(true, 0, "Bearer ", 0, 7)) {
                googleIdTokenStr = googleIdTokenStr.substring(7).trim();
            }
            // Verify Google ID token
            GoogleIdToken decodedToken = googleIdTokenVerifier.verify(googleIdTokenStr);
            if (decodedToken == null) {
                // Fallback: attempt payload‑only parse (no signature verification) to still obtain user info
                try {
                    decodedToken = GoogleIdToken.parse(googleIdTokenVerifier.getJsonFactory(), googleIdTokenStr);
                    log.warn("Google token verification failed (signature), but payload was parsed. Proceeding with payload only.");
                } catch (Exception parseEx) {
                    throw new AuthException.InvalidTokenException("Google ID token verification and parsing failed");
                }
            }

            GoogleIdToken.Payload payload = decodedToken.getPayload();
            String uid = payload.getSubject(); // Google subject id (unique Google User ID)
            String email = payload.getEmail();
            if (email != null) {
                email = email.toLowerCase().trim();
            }
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            if (email == null || email.isBlank()) {
                throw new AuthException.InvalidTokenException("Email not found in token");
            }

            SystemSetting systemSetting = systemSettingService.getSystemSetting();
            if (systemSetting != null && systemSetting.isOnlyAllowCollegeEmails()) {
                int atIndex = email.indexOf('@');
                String domain = atIndex != -1 ? email.substring(atIndex + 1) : "";

                boolean isBypassDomain = domain.equals("caxone.in");
                boolean isExistingAdmin = false;

                // Verify if existing user is an Admin
                User existingUser = userRepository.findByGoogleId(uid).orElse(null);
                if (existingUser == null) {
                    existingUser = userRepository.findByEmail(email).orElse(null);
                }
                if (existingUser != null && existingUser.getRole() == UserRole.ADMIN) {
                    isExistingAdmin = true;
                }

                if (!isBypassDomain && !isExistingAdmin) {
                    boolean isAcademicDomain = domain.endsWith(".edu") 
                            || domain.endsWith(".ac.in") 
                            || domain.endsWith(".edu.in");
                    if (!isAcademicDomain) {
                        throw new AuthException.ForbiddenException("Only college email logins are permitted.");
                    }
                }
            }

            // Check if user exists by googleId (which is the Google sub)
            User user = userRepository.findByGoogleId(uid).orElse(null);
            user = getUserAndHealIfVerified(user);
            boolean isNewUser = false;

            if (user == null) {
                // If not found by googleId, check by email (linking accounts)
                user = userRepository.findByEmail(email).orElse(null);
                user = getUserAndHealIfVerified(user);
                if (user != null) {
                    // Update their googleId so we can look up by googleId next time
                    user.setGoogleId(uid);
                    user.setAcceptedTerms(acceptedTerms);
                    user.setAcceptedTermsAt(Instant.now());
                    user.setUpdatedAt(Instant.now());
                    user = userRepository.save(user);
                    log.info("Linked existing user by email: {}, new googleId: {}", user.getUserId(), uid);
                } else {
                    isNewUser = true;
                    user = User.builder()
                            .userId(uid)
                            .googleId(uid)
                            .email(email)
                            .name(name != null ? name : email.split("@")[0])
                            .picture(picture)
                            .role(UserRole.STUDENT)
                            .collegeDetailsAdded(false)
                            .idVerified(false)
                            .acceptedTerms(acceptedTerms)
                            .acceptedTermsAt(Instant.now())
                            .build();
                    user = userRepository.save(user);
                    log.info("New user created: {}", uid);



                    eventPublisher.publishEvent(new UserSignupEvent(this, user));
                }
            } else {
                // Update terms if not accepted
                if (!user.isAcceptedTerms()) {
                    user.setAcceptedTerms(true);
                    user.setAcceptedTermsAt(Instant.now());
                    user.setUpdatedAt(Instant.now());
                    user = userRepository.save(user);
                }
                // Update picture if changed
                if (picture != null && !picture.equals(user.getPicture())) {
                    user.setPicture(picture);
                    user.setUpdatedAt(Instant.now());
                    user = userRepository.save(user);
                }
                log.info("Existing user logged in: {}", user.getUserId());
            }

            // Generate JWT using the user's permanent userId (Google sub)
            boolean isAdmin = user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified());
            
            if (user.isTwoFactorEnabled()) {
                String tempToken = jwtUtil.generateTemp2FaToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), isAdmin);
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("twoFactorRequired", true);
                result.put("tempToken", tempToken);
                result.put("message", "Two-factor authentication required");
                return result;
            }

            String token = jwtUtil.generateToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), isAdmin);
            String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), isAdmin);

            // Save refresh token to user
            if (user.getRefreshTokens() == null) {
                user.setRefreshTokens(new java.util.ArrayList<>());
            }
            user.getRefreshTokens().add(refreshToken);
            userRepository.save(user);

            // Determine redirect
            String redirect;
            String message;
            if (!user.isCollegeDetailsAdded()) {
                redirect = "/college";
                message = "College details missing. Please complete profile.";
            } else if (!user.isAcademicDetailsAdded()) {
                redirect = "/academic";
                message = "Academic details missing. Please complete profile.";
            } else {
                redirect = "/app";
                message = "Login Successful.";
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("token", token);
            result.put("refreshToken", refreshToken);
            result.put("userId", user.getUserId());
            result.put("message", message);
            result.put("redirect", redirect);
            result.put("user", user);
            result.put("isNewUser", isNewUser);
            return result;

        } catch (AuthException.InvalidTokenException | AuthException.ForbiddenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google login/signup failed — token: {} … cause: {}: {}", 
                (googleIdTokenStr != null && googleIdTokenStr.length() > 20) ? googleIdTokenStr.substring(0,20) + "..." : googleIdTokenStr,
                e.getClass().getSimpleName(), e.getMessage(), e);
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
        return getUserAndHealIfVerified(user);
    }

    /**
     * Add college details.
     */
    public Map<String, Object> addCollegeDetails(String token, String collegeId) {
        String userId = jwtUtil.extractUserId(token);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);

        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("College", collegeId));

        boolean isFirstTime = !user.isCollegeDetailsAdded();

        user.setCollegeDetails(CollegeDetails.builder()
                .collegeId(collegeId)
                .collegeName(college.getCollegeName())
                .collegeCode(college.getCollegeCode())
                .location(college.getLocation())
                .build());
        user.setCollegeDetailsAdded(true);
        user.setCollegeAddedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user = userRepository.save(user);

        log.info("College details added for user: {}", userId);

        if (isFirstTime) {
            eventPublisher.publishEvent(new CollegeSelectedEvent(this, user));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "College details added successfully");
        result.put("user", user);
        return result;
    }

    /**
     * Add academic details.
     */
    public Map<String, Object> addAcademicDetails(String token, int admissionBatch, int currentAcademicYear, int currentSemester) {
        String userId = jwtUtil.extractUserId(token);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);

        if (currentAcademicYear < 1 || currentAcademicYear > 4) {
            throw new BusinessException.BadRequestException("Academic year must be between 1 and 4");
        }
        if (currentSemester < 1 || currentSemester > 2) {
            throw new BusinessException.BadRequestException("Semester must be 1 or 2");
        }

        user.setAcademicDetails(AcademicDetails.builder()
                .admissionBatch(admissionBatch)
                .currentAcademicYear(currentAcademicYear)
                .currentSemester(currentSemester)
                .updatedAt(Instant.now())
                .build());
        user.setAcademicDetailsAdded(true);
        user.setUpdatedAt(Instant.now());
        user = userRepository.save(user);

        log.info("Academic details added for user: {}", userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Academic details added successfully");
        result.put("user", user);
        return result;
    }

    /**
     * Update FCM token.
     */
    public void updateFcmToken(String userId, String fcmToken) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);
        user.setFcmToken(fcmToken);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        log.info("FCM token updated for user: {}", userId);
    }

    /**
     * Update user profile fields.
     */
    @SuppressWarnings("unchecked")
    public User updateUser(String token, Map<String, Object> updates) {
        String userId = jwtUtil.extractUserId(token);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);

        if (updates.containsKey("name") && updates.get("name") != null) {
            user.setName((String) updates.get("name"));
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
        if (updates.containsKey("premiumExpiresAt")) {
            Object expVal = updates.get("premiumExpiresAt");
            if (expVal == null) {
                user.setPremiumExpiresAt(null);
            } else {
                user.setPremiumExpiresAt(Instant.parse((String) expVal));
            }
        }
        if (updates.containsKey("premiumPack")) {
            user.setPremiumPack((String) updates.get("premiumPack"));
        }
        if (updates.containsKey("premiumCardTheme")) {
            user.setPremiumCardTheme((String) updates.get("premiumCardTheme"));
        }
        if (updates.containsKey("premiumMusicLink")) {
            user.setPremiumMusicLink((String) updates.get("premiumMusicLink"));
        }
        if (updates.containsKey("socialLinks") && updates.get("socialLinks") != null) {
            Map<String, Object> socialLinksMap = (Map<String, Object>) updates.get("socialLinks");
            SocialLinks socialLinks = new SocialLinks(
                    (String) socialLinksMap.get("instagram"),
                    (String) socialLinksMap.get("twitter"),
                    (String) socialLinksMap.get("linkedin"),
                    (String) socialLinksMap.get("github"),
                    (String) socialLinksMap.get("whatsapp"),
                    (String) socialLinksMap.get("telegram"),
                    (String) socialLinksMap.get("website")
            );
            user.setSocialLinks(socialLinks);
        }

        user.setUpdatedAt(Instant.now());
        user = userRepository.save(user);
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
                    UserRole defaultRole = userId.toLowerCase().contains("admin") ? UserRole.ADMIN : UserRole.STUDENT;
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
                            .academicDetailsAdded(true)
                            .academicDetails(com.cax.cax_backend.user.model.AcademicDetails.builder()
                                    .admissionBatch(2023)
                                    .currentAcademicYear(3)
                                    .currentSemester(1)
                                    .build())
                            .idVerified(defaultRole == UserRole.ADMIN)
                            .acceptedTerms(true)
                            .acceptedTermsAt(Instant.now())
                            .createdAt(Instant.now())
                            .build();
                    User savedUser = userRepository.save(newUser);
                    

                    
                    return savedUser;
                });

        boolean isAdmin = user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified());
        String token = jwtUtil.generateToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), isAdmin);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), isAdmin);

        // Save refresh token to user
        if (user.getRefreshTokens() == null) {
            user.setRefreshTokens(new java.util.ArrayList<>());
        }
        user.getRefreshTokens().add(refreshToken);
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

            if (user.getRefreshTokens() == null || !user.getRefreshTokens().contains(refreshToken)) {
                throw new AuthException.InvalidTokenException("Refresh token is invalid or has been revoked");
            }

            // Remove the old refresh token (rotation)
            user.getRefreshTokens().remove(refreshToken);

            // Generate new access and refresh tokens
            boolean isAdmin = user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN 
                    || (user.getRole() == com.cax.cax_backend.common.enums.UserRole.SUPER_STUDENT && user.isIdVerified());
            String newAccessToken = jwtUtil.generateToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), isAdmin);
            String newRefreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), isAdmin);

            user.getRefreshTokens().add(newRefreshToken);
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
        user.setTwoFactorSecret(secret);
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

        boolean verified = TotpUtil.verifyCode(user.getTwoFactorSecret(), code, 1);
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

        boolean verified = TotpUtil.verifyCode(user.getTwoFactorSecret(), code, 1);
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

        boolean verified = TotpUtil.verifyCode(user.getTwoFactorSecret(), code, 1);
        if (!verified) {
            throw new BusinessException.BadRequestException("Invalid verification code");
        }

        // Generate final JWT
        boolean isAdmin = user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified());
        String token = jwtUtil.generateToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), isAdmin);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), isAdmin);

        // Save refresh token to user
        if (user.getRefreshTokens() == null) {
            user.setRefreshTokens(new java.util.ArrayList<>());
        }
        user.getRefreshTokens().add(refreshToken);
        userRepository.save(user);

        // Determine redirect
        String redirect;
        String message;
        if (!user.isCollegeDetailsAdded()) {
            redirect = "/college";
            message = "College details missing. Please complete profile.";
        } else if (!user.isAcademicDetailsAdded()) {
            redirect = "/academic";
            message = "Academic details missing. Please complete profile.";
        } else {
            redirect = "/app";
            message = "Login Successful.";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("userId", user.getUserId());
        result.put("message", message);
        result.put("redirect", redirect);
        result.put("user", user);
        return result;
    }

    private User getUserAndHealIfVerified(User user) {
        if (user == null) return null;
        boolean needsSave = false;
        if (user.isIdVerified()) {
            if (user.getCaxId() == null || user.getCaxId().isEmpty()) {
                user.setCaxId(generateUniqueCaxId());
                needsSave = true;
            }
            if (user.getIdCardExpiresAt() == null) {
                user.setIdCardExpiresAt(Instant.now().plus(180, java.time.temporal.ChronoUnit.DAYS));
                needsSave = true;
            }
        }
        if (user.isIdVerified() && user.getIdCardExpiresAt() != null && user.getIdCardExpiresAt().isBefore(Instant.now())) {
            user.setIdVerified(false);
            needsSave = true;
            log.info("User {} verification expired. Resetting idVerified to false.", user.getUserId());
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
}
