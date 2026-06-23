package com.cax.cax_backend.auth.controller;

import com.cax.cax_backend.auth.service.AuthService;
import com.cax.cax_backend.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorAuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @Value("${app.env:production}")
    private String appEnv;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    private void setAuthCookie(HttpServletResponse response, String token) {
        int maxAge = jwtExpirationMs > 0 ? (int) (jwtExpirationMs / 1000) : 86400;
        boolean secure = "production".equalsIgnoreCase(appEnv);
        String secureFlag = secure ? "; Secure" : "";
        response.addHeader("Set-Cookie",
                "access_token=" + token + "; HttpOnly" + secureFlag + "; SameSite=Lax; Path=/; Max-Age=" + maxAge);
    }

    @GetMapping("/setup")
    public ResponseEntity<Map<String, Object>> get2FaSetup(@RequestHeader("Authorization") String authHeader) {
        String token = JwtUtil.extractFromHeader(authHeader);
        String userId = jwtUtil.extractUserId(token);
        return ResponseEntity.ok(authService.generate2FaSetup(userId));
    }

    @PostMapping("/enable")
    public ResponseEntity<Map<String, Object>> enable2Fa(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        String token = JwtUtil.extractFromHeader(authHeader);
        String userId = jwtUtil.extractUserId(token);
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code is required"));
        }
        return ResponseEntity.ok(authService.enable2Fa(userId, code.trim()));
    }

    @PostMapping("/disable")
    public ResponseEntity<Map<String, Object>> disable2Fa(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        String token = JwtUtil.extractFromHeader(authHeader);
        String userId = jwtUtil.extractUserId(token);
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code is required"));
        }
        return ResponseEntity.ok(authService.disable2Fa(userId, code.trim()));
    }

    @PostMapping("/login-verify")
    public ResponseEntity<Map<String, Object>> loginVerify(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Platform", required = false) String platform,
            HttpServletResponse response) {
        String tempToken = body.get("tempToken");
        String code = body.get("code");
        if (tempToken == null || tempToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "tempToken is required"));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code is required"));
        }
        Map<String, Object> result = authService.verify2FaLogin(tempToken.trim(), code.trim());
        if ("web".equalsIgnoreCase(platform)) {
            String jwt = (String) result.get("token");
            if (jwt != null) setAuthCookie(response, jwt);
        }
        return ResponseEntity.ok(result);
    }
}
