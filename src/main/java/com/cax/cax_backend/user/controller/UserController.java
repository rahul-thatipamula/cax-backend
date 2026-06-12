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

import org.springframework.security.core.Authentication;
import io.jsonwebtoken.Claims;

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

    @GetMapping("/superstudents")
    public ResponseEntity<ApiResponse<List<User>>> getSuperStudents(@RequestParam String collegeId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getSuperStudentsByCollegeId(collegeId)));
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

    @PostMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<User>> blockUser(
            @PathVariable String userId,
            @RequestParam boolean blocked,
            @RequestParam(required = false) String reason,
            Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(userService.blockUser(userId, blocked, reason)));
    }

    @GetMapping("/premium")
    public ResponseEntity<ApiResponse<List<User>>> getActivePremiumUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getActivePremiumUsers()));
    }

    @PostMapping("/{userId}/role")
    public ResponseEntity<ApiResponse<User>> updateUserRole(
            @PathVariable String userId,
            @RequestParam String role,
            Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(userService.updateUserRole(userId, role)));
    }

    private void checkAdmin(Authentication auth) {
        if (auth == null || auth.getCredentials() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.AdminOnlyException();
        }
        Claims claims = (Claims) auth.getCredentials();
        Boolean isAdmin = claims.get("isAdmin", Boolean.class);
        if (!Boolean.TRUE.equals(isAdmin)) {
            throw new com.cax.cax_backend.common.exception.AuthException.AdminOnlyException();
        }
    }
}
