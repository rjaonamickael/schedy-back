package com.schedy.dto.request;

public record FeatureFlagDto(
    String featureKey,
    boolean enabled
) {}
