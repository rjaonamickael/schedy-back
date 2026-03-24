package com.schedy.dto.request;

public record SubscriptionDto(
    String planTier,
    int maxEmployees,
    int maxSites,
    String promoCode
) {}
