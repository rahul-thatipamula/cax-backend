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
import com.cax.cax_backend.reward.model.UserReferral;
import com.cax.cax_backend.reward.repository.UserReferralRepository;
import com.cax.cax_backend.user.event.CollegeSelectedEvent;
import com.cax.cax_backend.user.event.UserSignupEvent;
import com.cax.cax_backend.user.model.AcademicDetails;
import com.cax.cax_backend.user.model.CollegeDetails;
import com.cax.cax_backend.user.model.SocialLinks;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final JwtUtil jwtUtil;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final ApplicationEventPublisher eventPublisher;
    private final UserReferralRepository userReferralRepository;

    public AuthService(UserRepository userRepository, CollegeRepository collegeRepository, JwtUtil jwtUtil, GoogleIdTokenVerifier googleIdTokenVerifier, ApplicationEventPublisher eventPublisher, UserReferralRepository userReferralRepository) {
        this.userRepository = userRepository;
        this.collegeRepository = collegeRepository;
        this.jwtUtil = jwtUtil;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.eventPublisher = eventPublisher;
        this.userReferralRepository = userReferralRepository;
    }

    /**
     * Google login/signup via Google ID token verification.
     */
    public Map<String, Object> handleGoogleLoginOrSignup(String googleIdTokenStr, boolean acceptedTerms) {
        try {
            // Verify Google ID token
            GoogleIdToken decodedToken = googleIdTokenVerifier.verify(googleIdTokenStr);
            if (decodedToken == null) {
                throw new AuthException.InvalidTokenException("Google ID token verification failed");
            }

            GoogleIdToken.Payload payload = decodedToken.getPayload();
            String uid = payload.getSubject(); // Google subject id (unique Google User ID)
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            if (email == null || email.isBlank()) {
                throw new AuthException.InvalidTokenException("Email not found in token");
            }

            String emailLower = email.toLowerCase().trim();
            // boolean isCollegeEmail = emailLower.endsWith(".edu.in") || 
            //                          emailLower.endsWith(".ac.in") || 
            //                          emailLower.endsWith(".edu");
            // 
            // if (!isCollegeEmail) {
            //     log.warn("Blocked login attempt from non-college email: {}", email);
            //     throw new AuthException.ForbiddenException("College mail only");
            // }

            // Check if user exists by googleId (which is the Google sub)
            User user = userRepository.findByGoogleId(uid).orElse(null);
            boolean isNewUser = false;

            if (user == null) {
                // If not found by googleId, check by email (linking accounts)
                user = userRepository.findByEmail(email).orElse(null);
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

                    // Generate unique referral code
                    String referralCode = "CAX-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    while (userReferralRepository.findByReferralCode(referralCode).isPresent()) {
                        referralCode = "CAX-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    }
                    UserReferral userReferral = UserReferral.builder()
                            .id(uid)
                            .referralCode(referralCode)
                            .createdAt(Instant.now())
                            .build();
                    userReferralRepository.save(userReferral);

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
            String token = jwtUtil.generateToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), isAdmin);

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
            result.put("userId", user.getUserId());
            result.put("message", message);
            result.put("redirect", redirect);
            result.put("user", user);
            result.put("isNewUser", isNewUser);
            return result;

        } catch (AuthException.InvalidTokenException | AuthException.ForbiddenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google login/signup failed — real cause: {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            throw new AuthException.InvalidTokenException("Google login failed: " + e.getMessage());
        }
    }

    /**
     * Get user by JWT token.
     */
    public User getUser(String token) {
        String userId = jwtUtil.extractUserId(token);
        return userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);
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
                    
                    // Generate unique referral code for test user
                    if (userReferralRepository.findById(userId).isEmpty()) {
                        String referralCode = "CAX-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                        UserReferral userReferral = UserReferral.builder()
                                .id(userId)
                                .referralCode(referralCode)
                                .createdAt(Instant.now())
                                .build();
                        userReferralRepository.save(userReferral);
                    }
                    
                    return savedUser;
                });

        boolean isAdmin = user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified());
        String token = jwtUtil.generateToken(user.getUserId(), user.getEmail(), user.getRole().getValue(), isAdmin);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("token", token);
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
}
