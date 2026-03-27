package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "app_user", indexes = {
    @Index(name = "idx_user_org", columnList = "organisationId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Email
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    private String nom;

    private String employeId;

    private String organisationId;

    private String refreshToken;

    @Column(name = "invitation_token", length = 64)
    private String invitationToken;

    @Column(name = "invitation_token_expires_at")
    private Instant invitationTokenExpiresAt;

    @Column(name = "password_set", nullable = false)
    @Builder.Default
    private boolean passwordSet = false;

    // ── TOTP 2FA ──

    @Column(name = "totp_secret_encrypted", length = 512)
    private String totpSecretEncrypted;

    @Column(name = "totp_enabled", nullable = false)
    @Builder.Default
    private boolean totpEnabled = false;

    /**
     * The last accepted TOTP code — persisted to prevent immediate replay attacks.
     * A valid code is rejected if it matches this value.
     */
    @Column(name = "totp_last_used_otp", length = 6)
    private String totpLastUsedOtp;

    // ── Email 2FA code ──

    @Column(name = "email_2fa_code_hash", length = 64)
    private String email2faCodeHash;

    @Column(name = "email_2fa_code_expires_at")
    private Instant email2faCodeExpiresAt;

    public enum UserRole {
        SUPERADMIN, ADMIN, MANAGER, EMPLOYEE
    }
}
