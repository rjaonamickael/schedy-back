package com.schedy.dto.response;

import com.schedy.entity.FeatureFlag;

public record FeatureFlagResponse(
        String id,
        String organisationId,
        String featureKey,
        boolean enabled
) {
    public static FeatureFlagResponse from(FeatureFlag entity) {
        return new FeatureFlagResponse(
                entity.getId(),
                entity.getOrganisationId(),
                entity.getFeatureKey(),
                entity.isEnabled()
        );
    }
}
