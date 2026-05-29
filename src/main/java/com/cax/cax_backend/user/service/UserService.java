package com.cax.cax_backend.user.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    public User getUserByUserId(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new AuthException.UserNotFoundException(userId));
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
        user.setIdVerified(false); // Reset verification status when new ID uploaded

        // Save user to MongoDB
        return userRepository.save(user);
    }
}
