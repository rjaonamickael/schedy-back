package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "subscription", indexes = {
    @Index(name = "idx_subscription_org",    columnList = "organisation_id"),
    @Index(name = "idx_subscription_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "organisation_id", nullable = false, unique = true)
    private String organisationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false)
    @Builder.Default
    private PlanTier planTier = PlanTier.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.TRIAL;

    @Column(name = "trial_ends_at")
    private OffsetDateTime trialEndsAt;

    @Column(name = "max_employees", nullable = false)
    @Builder.Default
    private int maxEmployees = 15;

    @Column(name = "max_sites", nullable = false)
    @Builder.Default
    private int maxSites = 1;

    @Column(name = "promo_code_id")
    private String promoCodeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public enum PlanTier {
        FREE, STARTER, PRO
    }

    public enum SubscriptionStatus {
        TRIAL, ACTIVE, SUSPENDED, CANCELLED
    }
}
