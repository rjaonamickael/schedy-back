package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Stores a single hashed TOTP recovery code for a user.
 * Recovery codes are one-time-use: once consumed, used=true and used_at is stamped.
 * The plain-text code is never persisted — only its SHA-256 hex digest.
 */
@Entity
@Table(
    name = "totp_recovery_code",
    indexes = @Index(name = "idx_recovery_user", columnList = "user_id")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TotpRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 hex digest of the plain-text recovery code. */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "used_at")
    private Instant usedAt;
}
