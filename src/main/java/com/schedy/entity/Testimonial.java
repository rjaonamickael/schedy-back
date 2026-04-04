package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Stores organisation testimonials submitted by org admins and moderated by superadmin.
 *
 * Lifecycle:
 *   PENDING  — submitted by org, awaiting superadmin review
 *   APPROVED — visible on the public landing page, ordered by displayOrder
 *   REJECTED — hidden from public display
 */
@Entity
@Table(name = "testimonial", indexes = {
    @Index(name = "idx_testimonial_status",  columnList = "status"),
    @Index(name = "idx_testimonial_org",     columnList = "organisation_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Testimonial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** FK to organisation.id — stored as plain VARCHAR, not a JPA relationship. */
    @Column(name = "organisation_id", nullable = false, length = 255)
    private String organisationId;

    @Column(name = "author_name", nullable = false, length = 100)
    private String authorName;

    @Column(name = "author_role", nullable = false, length = 100)
    private String authorRole;

    /** Optional — e.g. "Montréal" or "Antananarivo". */
    @Column(name = "author_city", length = 100)
    private String authorCity;

    @Column(name = "quote", nullable = false, columnDefinition = "TEXT")
    private String quote;

    /** Star rating 1–5. */
    @Column(name = "stars", nullable = false)
    @Builder.Default
    private int stars = 5;

    /** UI language: "fr" or "en". */
    @Column(name = "language", nullable = false, length = 5)
    @Builder.Default
    private String language = "fr";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TestimonialStatus status = TestimonialStatus.PENDING;

    /** Superadmin-controlled display position for the public carousel. Default 0 = unordered. */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Set when the superadmin approves or rejects the testimonial. */
    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    /** Email of the superadmin who reviewed this testimonial. */
    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @PrePersist
    void prePersist() {
        this.createdAt = OffsetDateTime.now();
    }

    public enum TestimonialStatus {
        PENDING, APPROVED, REJECTED
    }
}
