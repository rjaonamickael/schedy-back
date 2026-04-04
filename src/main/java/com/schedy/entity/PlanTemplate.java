package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "plan_template")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlanTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(length = 500)
    private String description;

    @Column(name = "max_employees", nullable = false)
    @Builder.Default
    private int maxEmployees = 15;

    @Column(name = "max_sites", nullable = false)
    @Builder.Default
    private int maxSites = 1;

    @Column(name = "price_monthly", precision = 10, scale = 2)
    private BigDecimal priceMonthly;

    @Column(name = "price_annual", precision = 10, scale = 2)
    private BigDecimal priceAnnual;

    @Column(name = "trial_days", nullable = false)
    @Builder.Default
    private int trialDays = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    /**
     * Feature flags for this plan template.
     * MUST be EAGER — Jackson serializes after Hibernate session closes
     * (open-in-view: false). @BatchSize mitigates N+1 when loading many plans.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 50)
    @CollectionTable(
        name = "plan_template_feature",
        joinColumns = @JoinColumn(name = "plan_template_id")
    )
    @MapKeyColumn(name = "feature_key")
    @Column(name = "enabled")
    @Builder.Default
    private Map<String, Boolean> features = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
