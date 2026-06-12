package com.cax.cax_backend.common.util;

import com.cax.cax_backend.common.exception.AuthException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT utility — generates and validates tokens. Encapsulates all token logic.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;
    private final long adminExpiration;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration,
            @Value("${jwt.admin-expiration}") long adminExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.adminExpiration = adminExpiration;
    }

    public String generateToken(String userId, String email, String role, boolean isAdmin) {
        long exp = isAdmin ? adminExpiration : expiration;
        return Jwts.builder()
                .subject(userId)
                .claims(Map.of(
                        "userId", userId,
                        "email", email,
                        "role", role,
                        "isAdmin", isAdmin,
                        "type", "access"
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + exp))
                .signWith(key)
                .compact();
    }

    public String generateTemp2FaToken(String userId, String email, String role, boolean isAdmin) {
        long exp = 5 * 60 * 1000; // 5 minutes
        return Jwts.builder()
                .subject(userId)
                .claims(Map.of(
                        "userId", userId,
                        "email", email,
                        "role", role,
                        "isAdmin", isAdmin,
                        "type", "temp_2fa"
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + exp))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String userId, String email, String role, boolean isAdmin) {
        long exp = 7L * 24 * 60 * 60 * 1000; // 7 days
        return Jwts.builder()
                .subject(userId)
                .claims(Map.of(
                        "userId", userId,
                        "email", email,
                        "role", role,
                        "isAdmin", isAdmin,
                        "type", "refresh"
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + exp))
                .signWith(key)
                .compact();
    }

    public Claims verifyToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new AuthException.TokenExpiredException();
        } catch (JwtException e) {
            throw new AuthException.InvalidTokenException("Invalid token: " + e.getMessage());
        }
    }

    public String extractUserId(String token) {
        return verifyToken(token).get("userId", String.class);
    }

    public String extractRole(String token) {
        return verifyToken(token).get("role", String.class);
    }

    public String extractEmail(String token) {
        return verifyToken(token).get("email", String.class);
    }

    public boolean isAdmin(String token) {
        Boolean admin = verifyToken(token).get("isAdmin", Boolean.class);
        return admin != null && admin;
    }

    /**
     * Extract token from "Bearer xxx" header.
     */
    public static String extractFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AuthException.UnauthorizedException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }
}
