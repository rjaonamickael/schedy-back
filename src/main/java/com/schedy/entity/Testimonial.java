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

    /**
     * Optional headline shown bold above the quote on the public card.
     * Acts as a "catchy title" that sums the testimonial up in one line.
     */
    @Column(name = "quote_title", length = 200)
    private String quoteTitle;

    // ── V41 : rich testimonial fields ──────────────────────────────

    /** LinkedIn profile URL of the author. Validated client + server side. */
    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    /** Public website of the author's organisation. */
    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    /** Facebook page / profile URL. */
    @Column(name = "facebook_url", length = 500)
    private String facebookUrl;

    /** Instagram profile URL. */
    @Column(name = "instagram_url", length = 500)
    private String instagramUrl;

    /** X (Twitter) profile URL. Column kept {@code twitter_url} for migration friendliness. */
    @Column(name = "twitter_url", length = 500)
    private String twitterUrl;

    /**
     * Public HTTPS URL of the sanitized logo stored on Cloudflare R2.
     * Computed server-side during upload — the client never sets this directly.
     */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** Optional: "problem faced" bullet in the structured testimonial template. */
    @Column(name = "text_probleme", columnDefinition = "TEXT")
    private String textProbleme;

    /** Optional: "solution adopted" bullet. */
    @Column(name = "text_solution", columnDefinition = "TEXT")
    private String textSolution;

    /** Optional: "measurable impact" bullet. */
    @Column(name = "text_impact", columnDefinition = "TEXT")
    private String textImpact;

    /**
     * V44 — denormalized subscription tier of the submitting organisation,
     * stamped at submit time and never mutated. Preserves the original plan
     * even if the org later upgrades or downgrades. Nullable for legacy rows
     * and orgs without a subscription record.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", length = 20)
    private Subscription.PlanTier planTier;

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
