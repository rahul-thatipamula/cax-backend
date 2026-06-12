package com.cax.cax_backend.user.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.ApplicationEventPublisher;

import com.cax.cax_backend.common.exception.AuthException;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private static final String UPLOAD_DIR = "uploads/id-cards/";

    public List<User> searchUsers(String query, String collegeId) {
        List<User> users = userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);
        if (collegeId != null && !collegeId.isBlank()) {
            users = users.stream()
                    .filter(u -> u.getCollegeDetails() != null && collegeId.equals(u.getCollegeDetails().getCollegeId()))
                    .toList();
        }
        return users;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getSuperStudentsByCollegeId(String collegeId) {
        if (collegeId == null || collegeId.isBlank()) {
            return List.of();
        }
        return userRepository.findByCollegeDetails_CollegeIdAndRoleAndBlocked(
                collegeId,
                com.cax.cax_backend.common.enums.UserRole.SUPER_STUDENT,
                false
        );
    }

    public User getUserByUserId(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new AuthException.UserNotFoundException(userId));
        
        boolean needsSave = false;
        if (user.isIdVerified()) {
            if (user.getCaxId() == null || user.getCaxId().isEmpty()) {
                user.setCaxId(generateUniqueCaxId());
                needsSave = true;
            }
            if (user.getIdCardExpiresAt() == null) {
                user.setIdCardExpiresAt(java.time.Instant.now().plus(180, java.time.temporal.ChronoUnit.DAYS));
                needsSave = true;
            }
        }

        if (user.isIdVerified() && user.getIdCardExpiresAt() != null && user.getIdCardExpiresAt().isBefore(java.time.Instant.now())) {
            user.setIdVerified(false);
            needsSave = true;
            log.info("User {} verification expired. Resetting idVerified to false.", userId);
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

    public User uploadIDCardImage(String userId, MultipartFile file) throws IOException {
        User user = getUserByUserId(userId);

        // Create uploads directory if not exists
        Path uploadDir = Paths.get(UPLOAD_DIR);
        Files.createDirectories(uploadDir);

        // Generate unique filename
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadDir.resolve(fileName);

        // Save file to disk
        Files.write(filePath, file.getBytes());

        // Update user with image path
        String imagePath = "id-cards/" + fileName;
        user.setIdCardImagePath(imagePath);
        boolean wasVerifiedAndActive = user.isIdVerified() && (user.getIdCardExpiresAt() == null || user.getIdCardExpiresAt().isAfter(java.time.Instant.now()));
        user.setIdVerified(wasVerifiedAndActive);

        // Save user to MongoDB
        return userRepository.save(user);
    }

    public User blockUser(String userId, boolean blocked, String reason) {
        User user = getUserByUserId(userId);
        user.setBlocked(blocked);
        if (blocked) {
            user.setBlockReason(reason);
            user.setBlockedAt(java.time.Instant.now());
        } else {
            user.setBlockReason(null);
            user.setBlockedAt(null);
        }
        return userRepository.save(user);
    }

    public List<User> getActivePremiumUsers() {
        return userRepository.findByPremiumExpiresAtAfterAndBlocked(java.time.Instant.now(), false);
    }

    public User updateUserRole(String userId, String roleStr) {
        User user = getUserByUserId(userId);
        com.cax.cax_backend.common.enums.UserRole previousRole = user.getRole();
        com.cax.cax_backend.common.enums.UserRole role = com.cax.cax_backend.common.enums.UserRole.fromValue(roleStr);
        if (previousRole != role) {
            user.setRole(role);
            if (role == com.cax.cax_backend.common.enums.UserRole.SUPER_STUDENT) {
                user.setIdVerified(true);
            }
            user.setUpdatedAt(java.time.Instant.now());
            User savedUser = userRepository.save(user);
            eventPublisher.publishEvent(new com.cax.cax_backend.user.event.UserRoleChangedEvent(this, savedUser, previousRole, role));
            return savedUser;
        }
        return user;
    }
}
