package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Stores email addresses of users who signed up for the PRO plan waitlist.
 * The email column carries a unique constraint so duplicate submissions are
 * naturally idempotent — the service layer checks existence before inserting
 * and returns 200 instead of 409 on a duplicate.
 */
@Entity
@Table(name = "pro_waitlist", indexes = {
    @Index(name = "idx_pro_waitlist_email", columnList = "email", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProWaitlist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** UI language at the time of sign-up: "fr" or "en". */
    @Column(length = 5)
    private String language;

    /**
     * CTA origin that triggered the sign-up.
     * Examples: "planb_cta", "pricing_pro", "landing_hero".
     */
    @Column(length = 50)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Set when the PRO-launch notification email is dispatched.
     * Null until then.
     */
    @Column(name = "notified_at")
    private OffsetDateTime notifiedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = OffsetDateTime.now();
    }
}
