package com.schedy.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PointageCodeConfigRequest(
    @NotBlank String siteId,
    @Min(1)   int    rotationValeur,
    @NotBlank String rotationUnite
) {}
