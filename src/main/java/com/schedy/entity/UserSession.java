package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Active refresh-token session for a given {@link User} — one row per logged-in
 * device. Replaces the legacy single-slot {@code app_user.refresh_token} column
 * (V50 migration) so the same account can stay authenticated on several devices
 * simultaneously.
 *
 * <p>The stored {@code tokenHash} is the SHA-256 hash of the raw refresh JWT
 * that was issued to the browser via the {@code refreshToken} HttpOnly cookie.
 * The raw token never lives in the database, only on the client.</p>
 *
 * <p>Rotation policy (enforced by {@code AuthService.refresh}) — each successful
 * refresh creates a new {@link UserSession} and deletes the presented one, so a
 * given {@code tokenHash} is valid exactly once. Cap is enforced in the service
 * layer with FIFO eviction ordered by {@code id}.</p>
 *
 * <p>No {@code @ManyToOne} association to {@link User}: the foreign-key cascade
 * is declared at the schema level (V50) and avoiding the bidirectional mapping
 * keeps us clear of {@code LazyInitializationException} in JSON serialisation
 * paths (project-wide convention in {@code CLAUDE.md}).</p>
 */
@Entity
@Table(name = "user_session")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (lastUsedAt == null) lastUsedAt = now;
    }
}
