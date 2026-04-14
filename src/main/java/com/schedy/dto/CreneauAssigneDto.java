package com.schedy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Sprint 16 / Feature 2 : {@code role} is the role being filled by the
 * employee on this specific creneau. For multi-role employees, this captures
 * "cook on Tuesday" vs "dishwasher on Thursday". Nullable : legacy creneaux
 * from before V33 have no role context.
 */
public record CreneauAssigneDto(
    String id,
    @NotBlank String employeId,
    @Min(0) @Max(6) int jour,
    @Min(0) @Max(23) double heureDebut,
    @Min(0) @Max(24) double heureFin,
    @NotBlank String semaine,
    String siteId,
    String organisationId,
    String role
) {}
