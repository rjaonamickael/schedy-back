package com.schedy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ExigenceDto(
    String id,
    @NotBlank String libelle,
    List<Integer> jours,
    @Min(0) @Max(23) double heureDebut,
    @Min(0) @Max(24) double heureFin,
    String role,
    @Min(1) int nombreRequis,
    String siteId,
    String organisationId
) {}
