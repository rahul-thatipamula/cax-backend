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
            // Personal-email accounts are no longer blocked here: they sign in
            // through the manual ID-card verification track, and access gating
            // is enforced via user verification state (idVerified /
            // manualVerificationStatus), not by email domain.
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

}
