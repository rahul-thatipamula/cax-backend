package com.cax.cax_backend.common.util;

import jakarta.servlet.http.HttpServletRequest;

/** Shared client-IP extraction, used by rate limiting. Trusts X-Forwarded-For as-is (no
 *  trusted-proxy allowlist) — fine behind a single reverse proxy/load balancer that sets
 *  it, but spoofable if the app were ever exposed directly to the internet. */
public final class ClientIpUtil {

    private ClientIpUtil() {}

    public static String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
