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

    public List<User> searchUsers(String query, String collegeId) {
        List<User> users = userRepository.findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);
        if (collegeId != null && !collegeId.isBlank()) {
            users = users.stream()
                    .filter(u -> u.getCollegeDetails() != null && collegeId.equals(u.getCollegeDetails().getCollegeId()))
                    .toList();
        }
        return decryptUsers(users);
    }

    public List<User> getAllUsers() {
        return decryptUsers(userRepository.findAll());
    }

    public List<User> getSuperStudentsByCollegeId(String collegeId) {
        if (collegeId == null || collegeId.isBlank()) {
            return List.of();
        }
        return decryptUsers(userRepository.findByCollegeDetails_CollegeIdAndRoleAndBlocked(
                collegeId,
                com.cax.cax_backend.common.enums.UserRole.SUPER_STUDENT,
                false
        ));
    }

    public User getUserByUserId(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new AuthException.UserNotFoundException(userId));
        decryptUser(user);
        
        boolean needsSave = false;
        if (user.isIdVerified()) {
            if (user.getCaxId() == null || user.getCaxId().isEmpty()) {
                user.setCaxId(generateUniqueCaxId());
                needsSave = true;
            }
        }

        if (needsSave) {
            user = saveUser(user);
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
        return saveUser(user);
    }

    public List<User> getActivePremiumUsers() {
        return decryptUsers(userRepository.findByPremiumExpiresAtAfterAndBlocked(java.time.Instant.now(), false));
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
            User savedUser = saveUser(user);
            eventPublisher.publishEvent(new com.cax.cax_backend.user.event.UserRoleChangedEvent(this, savedUser, previousRole, role));
            return savedUser;
        }
        return user;
    }

    public User saveUser(User user) {
        if (user.getName() != null) {
            user.setName(com.cax.cax_backend.common.util.EncryptionUtils.encryptIfNeeded(user.getName()));
        }
        User saved = userRepository.save(user);
        return decryptUser(saved);
    }

    public java.util.Optional<User> getUserOptByUserId(String userId) {
        java.util.Optional<User> opt = userRepository.findByUserId(userId);
        opt.ifPresent(this::decryptUser);
        return opt;
    }

    public User toggleWaterReminder(String userId, boolean subscribed) {
        User user = getUserByUserId(userId);
        user.setWaterReminderSubscribed(subscribed);
        user.setUpdatedAt(java.time.Instant.now());
        User saved = saveUser(user);
        eventPublisher.publishEvent(new com.cax.cax_backend.user.event.WaterReminderToggledEvent(this, saved, subscribed));
        return saved;
    }

    private User decryptUser(User user) {
        if (user == null) return null;
        if (user.getName() != null) {
            try {
                user.setName(com.cax.cax_backend.common.util.EncryptionUtils.decrypt(user.getName()));
            } catch (Exception e) {
                // If decryption fails (e.g. legacy plain text), keep as-is
                log.warn("Failed to decrypt user name for {}: {}", user.getUserId(), e.getMessage());
            }
        }
        return user;
    }

    private List<User> decryptUsers(List<User> users) {
        if (users == null) return null;
        users.forEach(this::decryptUser);
        return users;
    }
}
