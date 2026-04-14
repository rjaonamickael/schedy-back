package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * V36: audit trail for kiosk PIN lifecycle events. Records every creation,
 * regeneration, print, and revocation of an employee's kiosk PIN, with the
 * triggering admin (when applicable), the source of the action, and SHA-256
 * hashes of the old and new PINs (never plaintext).
 *
 * <p>Required for Quebec Law 25 / PIPEDA (Canada) right-of-access requests:
 * an admin must be able to answer "who has touched this employee's
 * credentials, when, why, and on what authority".
 */
@Entity
@Table(name = "pin_audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PinAuditLog {

    @Id
    private String id;

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.timestamp == null) {
            this.timestamp = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @Column(name = "employe_id", nullable = false)
    private String employeId;

    /** NULL for system-triggered events (rotation scheduler, batch ops). */
    @Column(name = "admin_user_id")
    private String adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Source source;

    @Column(name = "old_pin_hash", length = 64)
    private String oldPinHash;

    @Column(name = "new_pin_hash", length = 64)
    private String newPinHash;

    @Column(columnDefinition = "TEXT")
    private String motif;

    @Column(nullable = false)
    private OffsetDateTime timestamp;

    @Column(name = "organisation_id", nullable = false)
    private String organisationId;

    public enum Action {
        /** First-ever PIN creation when an employee is added to the system. */
        GENERATE,
        /** Admin-triggered regeneration of a single employee PIN. */
        REGENERATE_INDIVIDUAL,
        /** System-triggered cascade regeneration on site code rotation. */
        REGENERATE_CASCADE,
        /** Admin printed the PIN to a physical card (no PIN value change). */
        PRINT,
        /** PIN explicitly revoked (logout, security incident). */
        REVOKE
    }

    public enum Source {
        /** Action initiated from the admin UI. */
        ADMIN_UI,
        /** Action initiated by the rotation scheduler. */
        AUTO_ROTATION,
        /** Action initiated as part of a batch operation. */
        BATCH_OP,
        /** Action initiated by an internal system process. */
        SYSTEM
    }
}
