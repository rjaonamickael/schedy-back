package com.schedy.dto.request;

import java.time.OffsetDateTime;

public record SubscriptionDto(
    String planTier,
    int maxEmployees,
    int maxSites,
    String promoCode,
    OffsetDateTime trialEndsAt
) {}
