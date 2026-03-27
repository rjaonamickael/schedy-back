package com.schedy.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Shared IP resolution utility. Only trusts X-Forwarded-For when the direct
 * connection (remoteAddr) comes from a known trusted proxy. Prevents IP
 * spoofing via the header for rate limiting and audit logging.
 */
public final class IpUtils {

    private IpUtils() {
        // Utility class — no instances
    }

    /**
     * Resolves the real client IP from the request, validating X-Forwarded-For
     * only when the direct peer is a trusted proxy.
     *
     * @param request       the incoming HTTP request
     * @param trustedProxies set of trusted proxy IPs (e.g. 127.0.0.1, ::1)
     * @return the resolved client IP
     */
    public static String resolveClientIp(HttpServletRequest request, Set<String> trustedProxies) {
        String remoteAddr = request.getRemoteAddr();

        if (trustedProxies.contains(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }

        return remoteAddr;
    }
}
