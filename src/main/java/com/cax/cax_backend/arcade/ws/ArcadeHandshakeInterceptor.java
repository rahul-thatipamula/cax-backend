package com.cax.cax_backend.arcade.ws;

import com.cax.cax_backend.common.exception.BaseException;
import com.cax.cax_backend.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Authenticates the STOMP handshake for /ws-arcade. The upgrade request is a plain HTTP GET,
 * so it can't carry an Authorization header the way app clients normally send one over
 * WebSocket — the JWT is passed as a "token" query param instead and validated with the same
 * JwtUtil used by JwtAuthFilter for REST requests, so there's one source of truth for token
 * validity. Session attributes carry the resolved identity into the STOMP session so it's
 * available to any @MessageMapping / channel interceptor downstream.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArcadeHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_CAX_ID = "caxId";

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) {
            log.debug("Arcade WS handshake rejected: no token");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = jwtUtil.verifyToken(token);
            String tokenType = claims.get("type", String.class);
            if ("refresh".equals(tokenType) || "temp_2fa".equals(tokenType)) {
                log.debug("Arcade WS handshake rejected: wrong token type");
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
            String userId = claims.get("userId", String.class);
            if (userId == null) {
                userId = claims.getSubject();
            }
            attributes.put(ATTR_USER_ID, userId);
            String caxId = claims.get("caxId", String.class);
            if (caxId != null) {
                attributes.put(ATTR_CAX_ID, caxId);
            }
            return true;
        } catch (BaseException e) {
            log.debug("Arcade WS handshake rejected: {}", e.getMessage());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        } catch (Exception e) {
            log.debug("Arcade WS handshake error: {}", e.getMessage());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String candidate = authHeader.substring(7).trim();
            if (!candidate.isEmpty() && candidate.contains(".")) {
                return candidate;
            }
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            List<String> tokenParams = UriComponentsBuilder
                    .fromUri(servletRequest.getURI())
                    .build()
                    .getQueryParams()
                    .get("token");
            if (tokenParams != null && !tokenParams.isEmpty()) {
                String value = tokenParams.get(0);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
