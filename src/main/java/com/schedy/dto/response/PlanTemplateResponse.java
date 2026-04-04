package com.schedy.dto.response;

import com.schedy.entity.PlanTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Read projection of a PlanTemplate.
 * All collections are loaded eagerly inside the service layer
 * (open-in-view: false — never rely on lazy loading at serialization time).
 */
public record PlanTemplateResponse(

    String id,
    String code,
    String displayName,
    String description,
    int maxEmployees,
    int maxSites,
    BigDecimal priceMonthly,
    BigDecimal priceAnnual,
    int trialDays,
    boolean active,
    int sortOrder,
    Map<String, Boolean> features,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt

) {
    /** Factory method — entity must already have its features collection loaded. */
    public static PlanTemplateResponse from(PlanTemplate entity) {
        return new PlanTemplateResponse(
            entity.getId(),
            entity.getCode(),
            entity.getDisplayName(),
            entity.getDescription(),
            entity.getMaxEmployees(),
            entity.getMaxSites(),
            entity.getPriceMonthly(),
            entity.getPriceAnnual(),
            entity.getTrialDays(),
            entity.isActive(),
            entity.getSortOrder(),
            Map.copyOf(entity.getFeatures()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
