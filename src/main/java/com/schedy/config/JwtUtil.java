package com.schedy.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    /**
     * Minimum required key length in bytes after base64 decoding.
     * HS256 requires 256 bits = 32 bytes minimum (RFC 7518 section 3.2).
     * We enforce this as a defensive check to fail fast at boot with a clear
     * message instead of letting Keys.hmacShaKeyFor throw a cryptic WeakKeyException
     * deep inside the bean wiring.
     */
    private static final int MIN_SECRET_BYTES = 32;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        // V33-04 SEC : defensive validation of the configured secret. Without this,
        // a missing/short/non-base64 JWT_SECRET env var crashes Spring with an
        // unhelpful IllegalArgumentException ("Illegal base64 character ...") that
        // doesn't tell the operator what to fix. We catch the common failure modes
        // and replace them with an explicit IllegalStateException at boot.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT secret is not configured. Set the JWT_SECRET environment variable to a "
                + "base64-encoded random string of at least " + MIN_SECRET_BYTES + " bytes "
                + "(after decoding). Generate one with: openssl rand -base64 64");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                "JWT_SECRET is not valid base64 (got " + secret.length() + " chars). "
                + "Hyphens, underscores or whitespace are NOT allowed in standard base64. "
                + "Generate a valid one with: openssl rand -base64 64", ex);
        }
        if (decoded.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "JWT_SECRET decodes to only " + decoded.length + " bytes; HS256 requires at "
                + "least " + MIN_SECRET_BYTES + " bytes (256 bits). Generate a stronger one "
                + "with: openssl rand -base64 64");
        }
        this.key = Keys.hmacShaKeyFor(decoded);
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        log.info("JwtUtil initialized with {} byte secret (HS{}).", decoded.length, decoded.length * 8);
    }

    public String generateAccessToken(String email, String role, String organisationId) {
        var claims = new java.util.HashMap<String, Object>();
        claims.put("role", role);
        if (organisationId != null) {
            claims.put("organisationId", organisationId);
        }
        return buildToken(email, claims, accessTokenExpiration);
    }

    /**
     * Generates a short-lived impersonation token (30 minutes).
     * The token carries role=ADMIN scoped to the target organisation and an
     * "impersonator" claim recording which SUPERADMIN initiated the session.
     */
    public String generateImpersonationToken(String targetAdminEmail, String organisationId, String impersonatorEmail) {
        var claims = new java.util.HashMap<String, Object>();
        claims.put("role", "ADMIN");
        claims.put("organisationId", organisationId);
        claims.put("impersonator", impersonatorEmail);
        long thirtyMinutes = 30 * 60 * 1000L;
        return buildToken(targetAdminEmail, claims, thirtyMinutes);
    }

    public String generateRefreshToken(String email) {
        return buildToken(email, Map.of("type", "refresh"), refreshTokenExpiration);
    }

    private String buildToken(String subject, Map<String, Object> claims, long expiration) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public String extractOrganisationId(String token) {
        return parseClaims(token).get("organisationId", String.class);
    }

    public String extractClaim(String token, String claimKey) {
        try {
            return parseClaims(token).get(claimKey, String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return "refresh".equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Generates a short-lived (5-minute) pending token issued after successful password
     * verification when 2FA is enabled. The token has no role claims and carries
     * {@code type=2fa_pending} to distinguish it from normal access tokens.
     *
     * @param email the authenticated user's email
     * @return a signed JWT valid for 5 minutes
     */
    public String generate2faPendingToken(String email) {
        return buildToken(email, Map.of("type", "2fa_pending"), 5 * 60 * 1000L);
    }

    /**
     * Returns true if the token is a valid, unexpired 2FA-pending token.
     *
     * @param token the JWT to inspect
     * @return true iff {@code type} claim equals {@code "2fa_pending"}
     */
    public boolean is2faPendingToken(String token) {
        try {
            return "2fa_pending".equals(parseClaims(token).get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
