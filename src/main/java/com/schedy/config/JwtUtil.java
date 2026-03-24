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

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
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

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
