package com.schedy.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
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
 * - /api/v1/auth/2fa/*: 5 requests per minute
 * - /api/v1/auth/refresh: 10 requests per minute
 * - /api/v1/pointage-codes/validate: 10 requests per minute
 * - /api/v1/public/registration-requests: 5 requests per hour
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

    /** Stale bucket eviction threshold: 10 minutes of inactivity. */
    private static final long STALE_THRESHOLD_MS = 600_000;

    /**
     * Wraps a rate-limit Bucket with the timestamp of the last request,
     * so eviction can target only stale entries instead of clearing everything.
     */
    private record BucketEntry(Bucket bucket, long lastAccessMillis) {}

    private final Map<String, BucketEntry> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> registerBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> validateBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> kioskAdminBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> kioskPinClockBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> invitationBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> forgotPasswordBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> resetPasswordBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> registrationRequestBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> twoFaBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> refreshBuckets = new ConcurrentHashMap<>();

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

        BucketEntry entry = null;
        if (path.startsWith("/api/v1/auth/2fa/")) {
            entry = resolveBucket(twoFaBuckets, ip, 5, Duration.ofMinutes(1));
        } else if (path.startsWith("/api/v1/auth/refresh")) {
            entry = resolveBucket(refreshBuckets, ip, 10, Duration.ofMinutes(1));
        } else if (path.startsWith("/api/v1/auth/login")) {
            entry = resolveBucket(loginBuckets, ip, 10, Duration.ofMinutes(1));
        } else if (path.startsWith("/api/v1/auth/register")) {
            entry = resolveBucket(registerBuckets, ip, 3, Duration.ofMinutes(1));
        } else if (path.startsWith("/api/v1/pointage-codes/kiosk/admin")) {
            entry = resolveBucket(kioskAdminBuckets, ip, 3, Duration.ofMinutes(1));
        } else if (path.startsWith("/api/v1/pointage-codes/kiosk/pin-clock")) {
            entry = resolveBucket(kioskPinClockBuckets, ip, 5, Duration.ofMinutes(1));
        } else if (path.startsWith("/api/v1/pointage-codes/validate")) {
            entry = resolveBucket(validateBuckets, ip, 10, Duration.ofMinutes(1));
        } else if (path.startsWith("/api/v1/auth/forgot-password")) {
            entry = resolveBucket(forgotPasswordBuckets, ip, 3, Duration.ofHours(1));
        } else if (path.startsWith("/api/v1/auth/reset-password")) {
            entry = resolveBucket(resetPasswordBuckets, ip, 5, Duration.ofMinutes(1));
        } else if (path.startsWith("/api/v1/auth/set-password") || path.startsWith("/api/v1/auth/validate-invitation")) {
            entry = resolveBucket(invitationBuckets, ip, 5, Duration.ofMinutes(1));
        } else if (path.startsWith("/api/v1/public/registration-requests")) {
            entry = resolveBucket(registrationRequestBuckets, ip, 5, Duration.ofHours(1));
        }

        if (entry != null && !entry.bucket().tryConsume(1)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"Trop de requetes. Reessayez dans quelques instants.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolves (or creates) a BucketEntry for the given IP and updates its last-access
     * timestamp atomically. This ensures active IPs always carry a current timestamp so
     * the scheduled eviction never prematurely removes an in-use bucket.
     */
    private BucketEntry resolveBucket(Map<String, BucketEntry> buckets, String ip,
                                      int capacity, Duration refillDuration) {
        long now = System.currentTimeMillis();
        // Create entry if absent (first request from this IP)
        buckets.computeIfAbsent(ip, k -> new BucketEntry(createBucket(capacity, refillDuration), now));
        // Stamp current time on every access, preserving the existing Bucket instance
        return buckets.computeIfPresent(ip, (k, existing) -> new BucketEntry(existing.bucket(), now));
    }

    private Bucket createBucket(int capacity, Duration refillDuration) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillDuration)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        return com.schedy.util.IpUtils.resolveClientIp(request, trustedProxies);
    }

    /**
     * Evict STALE rate limit buckets every 5 minutes to prevent unbounded memory growth.
     *
     * Only entries whose last access is older than {@link #STALE_THRESHOLD_MS} (10 min) are
     * removed. Active IPs keep their depleted buckets, which closes the bypass window that
     * existed when all buckets were cleared indiscriminately -- an attacker who times a burst
     * right after eviction no longer gets a fresh bucket with full capacity.
     */
    @Scheduled(fixedRate = 300_000) // 5 minutes
    public void evictBuckets() {
        long threshold = System.currentTimeMillis() - STALE_THRESHOLD_MS;
        int evicted = 0;
        evicted += evictStaleEntries(loginBuckets, threshold);
        evicted += evictStaleEntries(registerBuckets, threshold);
        evicted += evictStaleEntries(validateBuckets, threshold);
        evicted += evictStaleEntries(kioskAdminBuckets, threshold);
        evicted += evictStaleEntries(kioskPinClockBuckets, threshold);
        evicted += evictStaleEntries(invitationBuckets, threshold);
        evicted += evictStaleEntries(forgotPasswordBuckets, threshold);
        evicted += evictStaleEntries(resetPasswordBuckets, threshold);
        evicted += evictStaleEntries(registrationRequestBuckets, threshold);
        evicted += evictStaleEntries(twoFaBuckets, threshold);
        evicted += evictStaleEntries(refreshBuckets, threshold);
        if (evicted > 0) {
            log.debug("Rate limit buckets evicted ({} stale entries removed)", evicted);
        }
    }

    /**
     * Removes entries from the given map whose last access time is before the threshold.
     * Returns the number of entries removed.
     */
    private int evictStaleEntries(Map<String, BucketEntry> buckets, long threshold) {
        int sizeBefore = buckets.size();
        buckets.entrySet().removeIf(e -> e.getValue().lastAccessMillis() < threshold);
        return sizeBefore - buckets.size();
    }
}
