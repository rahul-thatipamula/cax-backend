package com.cax.cax_backend.auth.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cax.cax_backend.auth.service.AuthService;
import com.cax.cax_backend.college.model.College;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.AuthException;
import com.cax.cax_backend.common.util.JwtUtil;
import com.cax.cax_backend.user.model.User;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @Value("${app.env:production}")
    private String appEnv;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    // 7-day fallback matches refresh token lifetime; access token expiry used when available
    private static final int COOKIE_MAX_AGE_FALLBACK = 7 * 24 * 60 * 60;

    private void setAuthCookie(HttpServletResponse response, String token) {
        int maxAge = jwtExpirationMs > 0 ? (int) (jwtExpirationMs / 1000) : COOKIE_MAX_AGE_FALLBACK;
        boolean secure = "production".equalsIgnoreCase(appEnv);
        String secureFlag = secure ? "; Secure" : "";
        response.addHeader("Set-Cookie",
                "access_token=" + token + "; HttpOnly" + secureFlag + "; SameSite=Lax; Path=/; Max-Age=" + maxAge);
    }

    private void clearAuthCookie(HttpServletResponse response) {
        boolean secure = "production".equalsIgnoreCase(appEnv);
        String secureFlag = secure ? "; Secure" : "";
        response.addHeader("Set-Cookie",
                "access_token=; HttpOnly" + secureFlag + "; SameSite=Lax; Path=/; Max-Age=0");
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> googleLogin(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Platform", required = false) String platform,
            HttpServletResponse response) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid token"));
        }
        String idToken = token.replaceFirst("(?i)^Bearer\\s+", "").trim();
        try {
            Map<String, Object> result = authService.handleGoogleLoginOrSignup(idToken);
            if ("web".equalsIgnoreCase(platform)) {
                String jwt = (String) result.get("token");
                if (jwt != null) setAuthCookie(response, jwt);
            }
            return ResponseEntity.ok(result);
        } catch (AuthException.InvalidTokenException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage(), "success", false));
        } catch (AuthException.ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Google login failed: " + e.getMessage(), "success", false));
        }
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
    public ResponseEntity<Map<String, Object>> refresh(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Platform", required = false) String platform,
            HttpServletResponse response) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "refreshToken is required"));
        }
        Map<String, Object> result = authService.refresh(refreshToken);
        if ("web".equalsIgnoreCase(platform)) {
            String jwt = (String) result.get("token");
            if (jwt != null) setAuthCookie(response, jwt);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader,
            HttpServletResponse response) {
        String token = JwtUtil.extractFromHeader(authHeader);
        authService.invalidateTokens(token);
        clearAuthCookie(response);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    @PostMapping("/preview-college")
    public ResponseEntity<Map<String, Object>> previewCollege(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing token"));
        }
        try {
            Map<String, Object> result = authService.previewCollege(token.replaceFirst("(?i)^Bearer\\s+", "").trim());
            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (AuthException.InvalidTokenException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage(), "success", false));
        }
    }

    @PostMapping("/report-wrong-college")
    public ResponseEntity<Map<String, Object>> reportWrongCollege(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing token"));
        }
        try {
            authService.reportWrongCollege(token.replaceFirst("(?i)^Bearer\\s+", "").trim(), body.get("reason"));
            return ResponseEntity.ok(Map.of("success", true, "message", "Report submitted. We'll review the mapping."));
        } catch (AuthException.InvalidTokenException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage(), "success", false));
        }
    }
}
