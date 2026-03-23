package com.schedy.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiting for auth and public kiosk endpoints.
 * - /api/v1/auth/login: 10 requests per minute
 * - /api/v1/auth/register: 3 requests per minute
 * - /api/v1/pointage-codes/validate: 10 requests per minute
 * - All other paths: no limit
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> validateBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = getClientIp(request);

        Bucket bucket = null;
        if (path.startsWith("/api/v1/auth/login")) {
            bucket = loginBuckets.computeIfAbsent(ip, k -> createBucket(10, Duration.ofMinutes(1)));
        } else if (path.startsWith("/api/v1/auth/register")) {
            bucket = registerBuckets.computeIfAbsent(ip, k -> createBucket(3, Duration.ofMinutes(1)));
        } else if (path.startsWith("/api/v1/pointage-codes/validate")) {
            bucket = validateBuckets.computeIfAbsent(ip, k -> createBucket(10, Duration.ofMinutes(1)));
        }

        if (bucket != null && !bucket.tryConsume(1)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"Trop de requetes. Reessayez dans quelques instants.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Bucket createBucket(int capacity, Duration refillDuration) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(capacity, refillDuration));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
