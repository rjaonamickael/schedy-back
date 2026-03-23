package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PointageCodeConfigRequest(
    @NotBlank String siteId,
    @NotBlank String frequence
) {}
