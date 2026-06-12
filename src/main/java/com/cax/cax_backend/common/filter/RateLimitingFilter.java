package com.cax.cax_backend.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements Filter {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    private static class TokenBucket {
        private final long capacity = 30;
        private double tokens = 30.0;
        private long lastRefillTime = System.currentTimeMillis();

        public synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            double refill = (now - lastRefillTime) * 0.003; // 3 tokens per second (0.003 per ms)
            if (refill > 0) {
                tokens = Math.min(capacity, tokens + refill);
                lastRefillTime = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String ip = getClientIP(httpRequest);
            TokenBucket bucket = buckets.computeIfAbsent(ip, k -> new TokenBucket());

            if (!bucket.tryConsume()) {
                httpResponse.setStatus(429);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"status\":429,\"message\":\"Too Many Requests. Please slow down.\",\"data\":null}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}
