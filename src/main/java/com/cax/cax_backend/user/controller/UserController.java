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
import com.cax.cax_backend.common.annotation.AdminActivityLog;
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
            @RequestParam(required = false) String collegeId,
            Authentication auth) {
        String callerId = getCallerId(auth);
        boolean isSysAdmin = isCallerAdmin(auth);
        boolean isAdmin = isSysAdmin || isCallerSuperStudent(auth);
        
        if (!isSysAdmin && callerId != null) {
            User caller = userService.getUserByUserId(callerId);
            if (caller.getCollegeDetails() != null) {
                collegeId = caller.getCollegeDetails().getCollegeId();
            } else {
                collegeId = "no_college_matched";
            }
        }
        
        List<User> users = userService.searchUsers(query, collegeId);
        return ResponseEntity.ok(ApiResponse.success(sanitizeUsersForPublic(users, callerId, isAdmin)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers(Authentication auth) {
        String callerId = getCallerId(auth);
        boolean isAdmin = isCallerAdmin(auth) || isCallerSuperStudent(auth);
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(sanitizeUsersForPublic(users, callerId, isAdmin)));
    }

    @GetMapping("/superstudents")
    public ResponseEntity<ApiResponse<List<User>>> getSuperStudents(
            @RequestParam(required = false) String collegeId,
            Authentication auth) {
        String callerId = getCallerId(auth);
        boolean isSysAdmin = isCallerAdmin(auth);
        boolean isAdmin = isSysAdmin || isCallerSuperStudent(auth);
        
        if (!isSysAdmin && callerId != null) {
            User caller = userService.getUserByUserId(callerId);
            if (caller.getCollegeDetails() != null) {
                collegeId = caller.getCollegeDetails().getCollegeId();
            } else {
                collegeId = "no_college_matched";
            }
        }
        
        List<User> users = userService.getSuperStudentsByCollegeId(collegeId);
        return ResponseEntity.ok(ApiResponse.success(sanitizeUsersForPublic(users, callerId, isAdmin)));
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<User>> getUserProfile(
            @PathVariable String userId,
            Authentication auth) {
        String callerId = getCallerId(auth);
        boolean isAdmin = isCallerAdmin(auth) || isCallerSuperStudent(auth);
        User user = userService.getUserByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(sanitizeUserForPublic(user, callerId, isAdmin)));
    }

    @PostMapping("/{userId}/block")
    @AdminActivityLog(action = "Block User", resourceIdParam = "userId")
    public ResponseEntity<ApiResponse<User>> blockUser(
            @PathVariable String userId,
            @RequestParam boolean blocked,
            @RequestParam(required = false) String reason,
            Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(userService.blockUser(userId, blocked, reason)));
    }

    @GetMapping("/premium")
    public ResponseEntity<ApiResponse<List<User>>> getActivePremiumUsers(Authentication auth) {
        String callerId = getCallerId(auth);
        boolean isAdmin = isCallerAdmin(auth) || isCallerSuperStudent(auth);
        List<User> users = userService.getActivePremiumUsers();
        return ResponseEntity.ok(ApiResponse.success(sanitizeUsersForPublic(users, callerId, isAdmin)));
    }

    @PostMapping("/{userId}/role")
    @AdminActivityLog(action = "Update User Role", resourceIdParam = "userId")
    public ResponseEntity<ApiResponse<User>> updateUserRole(
            @PathVariable String userId,
            @RequestParam String role,
            Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(userService.updateUserRole(userId, role)));
    }

    @PostMapping("/{userId}/water-reminder")
    public ResponseEntity<ApiResponse<User>> toggleWaterReminder(
            @PathVariable String userId,
            @RequestParam boolean subscribed,
            Authentication auth) {
        String authUserId = (String) auth.getPrincipal();
        if (!authUserId.equals(userId)) {
            try {
                checkAdmin(auth);
            } catch (Exception e) {
                throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("You can only modify your own settings");
            }
        }
        return ResponseEntity.ok(ApiResponse.success(userService.toggleWaterReminder(userId, subscribed)));
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

    private User sanitizeUserForPublic(User user, String callerId, boolean isAdmin) {
        if (user == null) return null;
        
        String decryptedName = null;
        try {
            if (user.getName() != null) {
                decryptedName = com.cax.cax_backend.common.util.EncryptionUtils.decrypt(user.getName());
            }
        } catch (Exception e) {
            decryptedName = user.getName();
        }

        if (isAdmin || user.getUserId().equals(callerId)) {
            user.setName(decryptedName);
            return user;
        }

        user.setName(user.getThoughtsDisplayName());
        user.setEmail(null);

        return user;
    }

    private List<User> sanitizeUsersForPublic(List<User> users, String callerId, boolean isAdmin) {
        if (users == null) return null;
        users.forEach(u -> sanitizeUserForPublic(u, callerId, isAdmin));
        return users;
    }

    private boolean isCallerAdmin(Authentication auth) {
        if (auth == null || auth.getCredentials() == null) {
            return false;
        }
        if (auth.getCredentials() instanceof Claims) {
            Claims claims = (Claims) auth.getCredentials();
            Boolean isAdmin = claims.get("isAdmin", Boolean.class);
            return Boolean.TRUE.equals(isAdmin);
        }
        return false;
    }

    private boolean isCallerSuperStudent(Authentication auth) {
        String callerId = getCallerId(auth);
        if (callerId == null) {
            return false;
        }
        try {
            User caller = userService.getUserByUserId(callerId);
            return caller != null && caller.getRole() == com.cax.cax_backend.common.enums.UserRole.SUPER_STUDENT;
        } catch (Exception e) {
            return false;
        }
    }

    private String getCallerId(Authentication auth) {
        return (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) 
                ? (String) auth.getPrincipal() 
                : null;
    }
}
