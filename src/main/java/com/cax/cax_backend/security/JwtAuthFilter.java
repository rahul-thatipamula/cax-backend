package com.cax.cax_backend.security;

import com.cax.cax_backend.common.exception.BaseException;
import com.cax.cax_backend.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter — intercepts every request to validate Bearer tokens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);
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

            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + (role != null ? role.toUpperCase() : "STUDENT"))
            );
            if (Boolean.TRUE.equals(isAdmin)) {
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
