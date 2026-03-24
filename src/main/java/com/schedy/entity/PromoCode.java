package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "promo_code", indexes = {
    @Index(name = "idx_promo_code_code", columnList = "code")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(length = 500)
    private String description;

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "discount_months")
    private Integer discountMonths;

    @Column(name = "plan_override", length = 50)
    private String planOverride;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "current_uses", nullable = false)
    @Builder.Default
    private int currentUses = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "valid_from", nullable = false)
    @Builder.Default
    private OffsetDateTime validFrom = OffsetDateTime.now();

    @Column(name = "valid_to")
    private OffsetDateTime validTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
