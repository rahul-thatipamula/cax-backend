package com.cax.cax_backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cax.cax_backend.common.exception.BaseException;
import com.cax.cax_backend.common.util.JwtUtil;
import com.cax.cax_backend.settings.service.SystemSettingService;
import com.cax.cax_backend.user.service.UserActivityService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT authentication filter — intercepts every request to validate Bearer tokens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserActivityService userActivityService;
    private final SystemSettingService systemSettingService;

    /** Extracts a JWT from the Bearer header, falling back to the access_token cookie.
     *  Bearer tokens that are blank or clearly not a JWT (no dots) are skipped so that
     *  "Authorization: Bearer null" sent by the Flutter web client doesn't block cookie auth. */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String candidate = authHeader.substring(7).trim();
            if (!candidate.isEmpty() && candidate.contains(".")) {
                return candidate;
            }
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("access_token".equals(cookie.getName())) {
                    String value = cookie.getValue();
                    if (value != null && !value.isBlank()) return value;
                }
            }
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtUtil.verifyToken(token);

            String tokenType = claims.get("type", String.class);
            if ("refresh".equals(tokenType) || "temp_2fa".equals(tokenType)) {
                log.debug("Rejecting refresh or temp_2fa token used as access token");
                filterChain.doFilter(request, response);
                return;
            }

            String userId = claims.get("userId", String.class);
            if (userId == null) {
                userId = claims.getSubject();
            }

            String role = claims.get("role", String.class);
            Boolean isAdmin = claims.get("isAdmin", Boolean.class);
            // Enforce academic email requirement on all authenticated requests (except public paths and bypasses)
            String path = request.getRequestURI();
            if (!isPublicPath(path)) {
                String email = claims.get("email", String.class);
                if (email != null && !email.isBlank()) {
                    int atIndex = email.indexOf('@');
                    String domain = atIndex != -1 ? email.substring(atIndex + 1) : "";

                    boolean isBypassEmail = "rahulthatipamula97@gmail.com".equalsIgnoreCase(email);
                    boolean isExistingAdmin = "admin".equalsIgnoreCase(role)
                            || Boolean.TRUE.equals(isAdmin);
                    boolean isPlayStoreTesting = systemSettingService.isPlayStoreTestingEnabled();

                    if (!isPlayStoreTesting && !isBypassEmail && !isExistingAdmin) {
                        boolean isAcademicDomain = domain.endsWith(".edu")
                                || domain.endsWith(".ac.in")
                                || domain.endsWith(".edu.in");
                        if (!isAcademicDomain) {
                            log.warn("Blocked request to {} from non-academic email: {} for user: {}", path, email, userId);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"message\":\"Only college email logins are permitted.\",\"errorCode\":403,\"statusCode\":403}");
                            return;
                        }
                    }
                }
            }
            String appVersion = request.getHeader("X-App-Version");
            String buildNumberStr = request.getHeader("X-Build-Number");
            int buildNumber = 0;
            if (buildNumberStr != null && !buildNumberStr.isBlank()) {
                try {
                    buildNumber = Integer.parseInt(buildNumberStr.trim());
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            
            // Record last seen activity asynchronously
            userActivityService.updateLastSeen(userId, appVersion, buildNumber);

            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + (role != null ? role.toUpperCase() : "STUDENT"))
            );
            if (Boolean.TRUE.equals(isAdmin) && role != null) {
                authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()),
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                );
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, claims, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (BaseException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            // Don't set auth — request will be rejected by security config if endpoint requires auth
        } catch (Exception e) {
            log.debug("JWT processing error: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    private boolean isPublicPath(String path) {
        return path.equals("/health")
                || path.equals("/api/auth/google")
                || path.equals("/api/auth/preview-college")
                || path.equals("/api/auth/report-wrong-college")
                || path.equals("/api/auth/generate-test-token")
                || path.equals("/api/auth/refresh")
                || path.startsWith("/api/auth/qr/")
                || path.equals("/api/auth/2fa/login-verify")
                || path.startsWith("/api/version/")
                || path.equals("/api/colleges")
                || path.equals("/api/newsletter/subscribe")
                || (path.startsWith("/api/ads/") && path.endsWith("/click"))
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/v3/api-docs")
                || path.equals("/ws/chat");
    }
}
