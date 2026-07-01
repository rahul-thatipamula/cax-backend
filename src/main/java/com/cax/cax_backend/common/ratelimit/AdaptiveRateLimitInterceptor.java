package com.cax.cax_backend.common.ratelimit;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Runs after Spring Security's filter chain (so an authenticated request's JWT claims are
 *  already on the SecurityContext for USER-keyed rules) and before the controller method.
 *  Replaces the old global per-IP RateLimitingFilter. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdaptiveRateLimitInterceptor implements HandlerInterceptor {

    private final AdaptiveRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();
        if (path.equals("/health") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
            return true;
        }

        RateLimitResult result = rateLimiter.check(request, SecurityContextHolder.getContext().getAuthentication());
        response.setHeader("X-RateLimit-Rule", result.ruleName());
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (!result.allowed()) {
            log.debug("Rate limit exceeded: rule={}, path={}", result.ruleName(), path);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType("application/json");
            ApiResponse<Object> body = ApiResponse.error("Too many requests. Please slow down and try again shortly.", 429, 429);
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }
        return true;
    }
}
