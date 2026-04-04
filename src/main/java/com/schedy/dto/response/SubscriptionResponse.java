package com.schedy.dto.response;

import com.schedy.entity.Subscription;
import com.schedy.entity.Subscription.PlanTier;
import com.schedy.entity.Subscription.SubscriptionStatus;

import java.time.OffsetDateTime;

public record SubscriptionResponse(
        String id,
        String organisationId,
        PlanTier planTier,
        SubscriptionStatus status,
        int maxEmployees,
        int maxSites,
        OffsetDateTime trialEndsAt,
        String promoCodeId,
        OffsetDateTime createdAt
) {
    public static SubscriptionResponse from(Subscription entity) {
        return new SubscriptionResponse(
                entity.getId(),
                entity.getOrganisationId(),
                entity.getPlanTier(),
                entity.getStatus(),
                entity.getMaxEmployees(),
                entity.getMaxSites(),
                entity.getTrialEndsAt(),
                entity.getPromoCodeId(),
                entity.getCreatedAt()
        );
    }
}
