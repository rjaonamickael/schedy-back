package com.schedy.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * IP-based rate limiting for auth and public kiosk endpoints.
 * - /api/v1/auth/login: 10 requests per minute
 * - /api/v1/auth/register: 3 requests per minute
 * - /api/v1/pointage-codes/validate: 10 requests per minute
 * - All other paths: no limit
 *
 * Security: X-Forwarded-For is only trusted when the direct connection
 * comes from a known trusted proxy IP (configurable via
 * schedy.rate-limit.trusted-proxies). This prevents IP spoofing attacks
 * that could bypass rate limiting.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> validateBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> kioskAdminBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> invitationBuckets = new ConcurrentHashMap<>();

    private final Set<String> trustedProxies;

    public RateLimitFilter(
            @Value("${schedy.rate-limit.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}") List<String> trustedProxies) {
        this.trustedProxies = trustedProxies.stream()
                .map(String::trim)
                .collect(Collectors.toSet());
        log.info("RateLimitFilter initialized with trusted proxies: {}", this.trustedProxies);
    }

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
        } else if (path.startsWith("/api/v1/pointage-codes/kiosk/admin")) {
            bucket = kioskAdminBuckets.computeIfAbsent(ip, k -> createBucket(3, Duration.ofMinutes(1)));
        } else if (path.startsWith("/api/v1/pointage-codes/validate")) {
            bucket = validateBuckets.computeIfAbsent(ip, k -> createBucket(10, Duration.ofMinutes(1)));
        } else if (path.startsWith("/api/v1/auth/set-password") || path.startsWith("/api/v1/auth/validate-invitation")) {
            bucket = invitationBuckets.computeIfAbsent(ip, k -> createBucket(5, Duration.ofMinutes(1)));
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

    /**
     * Resolve the client IP securely. Only trusts X-Forwarded-For when the
     * direct connection (remoteAddr) comes from a configured trusted proxy.
     * This prevents attackers from spoofing IPs via the header to bypass rate limiting.
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (trustedProxies.contains(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                // Take the leftmost (client) IP from the chain
                return xff.split(",")[0].trim();
            }
        }

        return remoteAddr;
    }

    /**
     * Evict all rate limit buckets every 5 minutes to prevent unbounded memory growth.
     * Buckets auto-refill on creation, so clearing them is safe -- new requests
     * will simply create fresh buckets with full capacity.
     */
    @Scheduled(fixedRate = 300_000) // 5 minutes
    public void evictBuckets() {
        int total = loginBuckets.size() + registerBuckets.size() + validateBuckets.size()
                + kioskAdminBuckets.size() + invitationBuckets.size();
        if (total > 0) {
            loginBuckets.clear();
            registerBuckets.clear();
            validateBuckets.clear();
            kioskAdminBuckets.clear();
            invitationBuckets.clear();
            log.debug("Rate limit buckets evicted ({} entries cleared)", total);
        }
    }
}
