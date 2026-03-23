package com.schedy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreneauAssigneDto(
    String id,
    @NotBlank String employeId,
    @Min(0) @Max(6) int jour,
    @Min(0) @Max(23) double heureDebut,
    @Min(0) @Max(24) double heureFin,
    @NotBlank String semaine,
    String siteId,
    String organisationId
) {}
