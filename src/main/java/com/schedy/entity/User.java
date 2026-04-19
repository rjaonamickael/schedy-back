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

    /**
     * @deprecated V50 — replaced by the {@code user_session} table which supports
     * multiple concurrent devices per account. Left in place (NULL) for rollback
     * safety during the post-V50 stabilisation window; the column will be dropped
     * in a later Flyway migration once the multi-session path is confirmed stable.
     * Do not read or write this field from new code.
     */
    @Deprecated
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

    // ── Password reset ──

    @Column(name = "password_reset_token", length = 64)
    private String passwordResetToken;

    @Column(name = "password_reset_token_expires_at")
    private Instant passwordResetTokenExpiresAt;

    // Added by V49 migration — personal profile : photo + LinkedIn perso.
    // photo_url est l'URL publique R2 produite par /api/v1/user/profile/photo
    // (raster JPG/PNG/WEBP). linkedin_url est le profil LinkedIn personnel.
    // Ces 2 champs sont snapshotes dans Testimonial au submit (author_photo_url
    // et linkedin_url de Testimonial).
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    public enum UserRole {
        SUPERADMIN, ADMIN, MANAGER, EMPLOYEE
    }
}
