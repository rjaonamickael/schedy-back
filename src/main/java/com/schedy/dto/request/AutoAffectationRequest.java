package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AutoAffectationRequest(
    @NotBlank String semaine,
    String siteId
) {}
