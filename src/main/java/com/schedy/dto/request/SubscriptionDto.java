package com.schedy.dto.request;

import java.time.OffsetDateTime;

public record SubscriptionDto(
    String planTier,
    Integer maxEmployees,
    Integer maxSites,
    String promoCode,
    OffsetDateTime trialEndsAt
) {}
