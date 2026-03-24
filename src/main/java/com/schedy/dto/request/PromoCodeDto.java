package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

public record PromoCodeDto(
    @NotBlank String code,
    String description,
    Integer discountPercent,
    Integer discountMonths,
    String planOverride,
    Integer maxUses,
    OffsetDateTime validFrom,
    OffsetDateTime validTo
) {}
