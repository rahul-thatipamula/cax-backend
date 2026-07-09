package com.cax.cax_backend.auth.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cax.cax_backend.auth.service.AuthService;
import com.cax.cax_backend.common.exception.AuthException;
import com.cax.cax_backend.user.model.User;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Dedicated auth routes for the cax-admin-web panel. Kept separate from
 * {@link AuthController} (used by the mobile/public clients) so the admin
 * login flow can be hardened independently: the JWT is delivered only via
 * an HttpOnly cookie and never appears in a JS-readable response body,
 * closing off token theft via XSS. Mobile's /api/auth/google is untouched.
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private static final String COOKIE_NAME = "access_token";
    // 7-day fallback matches refresh token lifetime; access token expiry used when available
    private static final int COOKIE_MAX_AGE_FALLBACK = 7 * 24 * 60 * 60;

    private final AuthService authService;

    @Value("${app.env:production}")
    private String appEnv;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    private void setAuthCookie(HttpServletResponse response, String token) {
        int maxAge = jwtExpirationMs > 0 ? (int) (jwtExpirationMs / 1000) : COOKIE_MAX_AGE_FALLBACK;
        boolean secure = "production".equalsIgnoreCase(appEnv);
        String secureFlag = secure ? "; Secure" : "";
        response.addHeader("Set-Cookie",
                COOKIE_NAME + "=" + token + "; HttpOnly" + secureFlag + "; SameSite=Lax; Path=/; Max-Age=" + maxAge);
    }

    private void clearAuthCookie(HttpServletResponse response) {
        boolean secure = "production".equalsIgnoreCase(appEnv);
        String secureFlag = secure ? "; Secure" : "";
        response.addHeader("Set-Cookie",
                COOKIE_NAME + "=; HttpOnly" + secureFlag + "; SameSite=Lax; Path=/; Max-Age=0");
    }

    private String extractCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> adminGoogleLogin(
            @RequestBody Map<String, String> body,
            HttpServletResponse response) {
        String idToken = body.get("token");
        if (idToken == null || idToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid token", "success", false));
        }
        try {
            Map<String, Object> result = authService.handleGoogleLoginOrSignup(idToken);
            User user = (User) result.get("user");
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication failed", "success", false));
            }
            // Role/org-leadership gating for panel access happens client-side, same as
            // the pre-existing flow (admins and org leaders — President/VP — both get in).
            String jwt = (String) result.get("token");
            setAuthCookie(response, jwt);
            // Deliberately omit the raw JWT from the response body — it lives only in the
            // HttpOnly cookie, so it's never reachable from JS on the frontend.
            return ResponseEntity.ok(Map.of("success", true, "user", user));
        } catch (AuthException.InvalidTokenException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage(), "success", false));
        } catch (AuthException.ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Admin login failed: " + e.getMessage(), "success", false));
        }
    }

    /** Dev-only bypass, mirrors /api/auth/generate-test-token but delivers the token via cookie only. */
    @PostMapping("/dev-login")
    public ResponseEntity<Map<String, Object>> devLogin(@RequestParam String userId, HttpServletResponse response) {
        if ("production".equalsIgnoreCase(appEnv)) {
            return ResponseEntity.status(403).body(Map.of("error", "Bypass login is disabled in production.", "success", false));
        }
        Map<String, Object> result = authService.generateTestToken(userId);
        String jwt = (String) result.get("token");
        if (jwt == null) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to generate token", "success", false));
        }
        setAuthCookie(response, jwt);
        return ResponseEntity.ok(Map.of("success", true, "userId", userId));
    }

    /** Restores the admin session from the HttpOnly cookie on page load/refresh. */
    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(Authentication auth, HttpServletRequest request) {
        String token = extractCookieToken(request);
        if (auth == null || !auth.isAuthenticated() || token == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated", "success", false));
        }
        try {
            User user = authService.getUser(token);
            return ResponseEntity.ok(Map.of("success", true, "user", user));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Session invalid", "success", false));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = extractCookieToken(request);
        if (token != null) {
            authService.invalidateTokens(token);
        }
        clearAuthCookie(response);
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
    }
}
