package com.schedy.dto.response;

import java.time.OffsetDateTime;

public record OrgSummaryResponse(
    String id,
    String nom,
    String status,
    String planTier,
    long employeeCount,
    long userCount,
    OffsetDateTime createdAt,
    String promoCode
) {}
