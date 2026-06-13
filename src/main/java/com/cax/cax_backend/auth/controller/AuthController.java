package com.cax.cax_backend.auth.controller;

import com.cax.cax_backend.auth.service.AuthService;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.util.JwtUtil;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.college.model.College;
import com.cax.cax_backend.common.exception.AuthException;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @Value("${app.env:production}")
    private String appEnv;

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> googleLogin(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid token"));
        }
        boolean acceptedTerms = Boolean.parseBoolean(body.get("acceptedTerms"));
        if (!acceptedTerms) {
            return ResponseEntity.badRequest().body(Map.of("error", "Acceptance of the Terms of Service and Privacy Policy is required to log in."));
        }
        String idToken = token.replaceFirst("(?i)^Bearer\\s+", "").trim();
        try {
            Map<String, Object> result = authService.handleGoogleLoginOrSignup(idToken, acceptedTerms);
            return ResponseEntity.ok(result);
        } catch (AuthException.InvalidTokenException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage(), "success", false));
        } catch (AuthException.ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Google login failed: " + e.getMessage(), "success", false));
        }
    }

    @PostMapping("/web-login")
    public ResponseEntity<Map<String, Object>> webLogin(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Google ID token is required"));
        }
        String idToken = token.replaceFirst("(?i)^Bearer\\s+", "").trim();
        try {
            Map<String, Object> result = authService.handleWebGoogleLogin(idToken);
            return ResponseEntity.ok(result);
        } catch (AuthException.InvalidTokenException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage(), "success", false));
        } catch (AuthException.ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage(), "success", false));
        } catch (AuthException.UserNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Login failed: " + e.getMessage(), "success", false));
        }
    }


    @PostMapping("/college")
    public ResponseEntity<Map<String, Object>> addCollegeDetails(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        String token = JwtUtil.extractFromHeader(authHeader);
        String collegeId = body.get("id");
        if (collegeId == null || collegeId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "collegeId is required"));
        }
        return ResponseEntity.ok(authService.addCollegeDetails(token, collegeId));
    }

    @GetMapping("/college")
    public ResponseEntity<ApiResponse<College>> getSelectedCollege(@RequestHeader("Authorization") String authHeader) {
        String token = JwtUtil.extractFromHeader(authHeader);
        College college = authService.getSelectedCollege(token);
        if (college == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("College selection required", 1301, 404));
        }
        return ResponseEntity.ok(ApiResponse.success(college));
    }

    @PostMapping("/academic")
    public ResponseEntity<Map<String, Object>> addAcademicDetails(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        String token = JwtUtil.extractFromHeader(authHeader);
        int admissionBatch = ((Number) body.get("admissionBatch")).intValue();
        int currentAcademicYear = ((Number) body.get("currentAcademicYear")).intValue();
        int currentSemester = ((Number) body.get("currentSemester")).intValue();
        return ResponseEntity.ok(authService.addAcademicDetails(token, admissionBatch, currentAcademicYear, currentSemester));
    }

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<User>> getUser(@RequestHeader("Authorization") String authHeader) {
        String token = JwtUtil.extractFromHeader(authHeader);
        User user = authService.getUser(token);
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));
    }

    @PutMapping("/user")
    public ResponseEntity<ApiResponse<User>> updateUser(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        String token = JwtUtil.extractFromHeader(authHeader);
        User user = authService.updateUser(token, body);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", user));
    }

    @PostMapping("/fcm-token")
    public ResponseEntity<ApiResponse<Void>> updateFcmToken(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        String token = JwtUtil.extractFromHeader(authHeader);
        String fcmToken = body.get("fcmToken");
        if (fcmToken == null || fcmToken.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("fcmToken is required", 1200, 400));
        }
        String userId = jwtUtil.extractUserId(token);
        authService.updateFcmToken(userId, fcmToken.trim());
        return ResponseEntity.ok(ApiResponse.success("FCM token updated"));
    }

    @GetMapping("/generate-test-token")
    public ResponseEntity<Map<String, Object>> generateTestToken(@RequestParam String userId) {
        if ("production".equalsIgnoreCase(appEnv)) {
            return ResponseEntity.status(403).body(Map.of("error", "Bypass token generation is disabled in production.", "success", false));
        }
        return ResponseEntity.ok(authService.generateTestToken(userId));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "refreshToken is required"));
        }
        return ResponseEntity.ok(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader("Authorization") String authHeader) {
        String token = JwtUtil.extractFromHeader(authHeader);
        authService.invalidateTokens(token);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }
}
