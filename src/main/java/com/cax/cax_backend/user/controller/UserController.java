package com.cax.cax_backend.user.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<User>>> searchUsers(
            @RequestParam String query,
            @RequestParam(required = false) String collegeId) {
        return ResponseEntity.ok(ApiResponse.success(userService.searchUsers(query, collegeId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<User>> getUserProfile(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByUserId(userId)));
    }

    @PostMapping("/{userId}/upload-id-card")
    public ResponseEntity<ApiResponse<User>> uploadIDCard(
            @PathVariable String userId,
            @RequestParam("file") MultipartFile file) throws IOException {
        User updatedUser = userService.uploadIDCardImage(userId, file);
        return ResponseEntity.ok(ApiResponse.success(updatedUser));
    }
}
