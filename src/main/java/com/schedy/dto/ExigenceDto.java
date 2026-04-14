package com.schedy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 16 / Feature 1 : variable staffing needs by period.
 *
 * <p>{@code dateDebut} and {@code dateFin} are optional. When both null,
 * the exigence applies year-round (base requirement). When both set, the
 * exigence applies only when the week's Monday falls inside the range.
 * {@code priorite} decides the winner when two exigences cover the same
 * slot (higher priorite wins; base exigences stay at 0).</p>
 */
public record ExigenceDto(
    String id,
    @NotBlank String libelle,
    List<Integer> jours,
    @Min(0) @Max(23) double heureDebut,
    @Min(0) @Max(24) double heureFin,
    String role,
    @Min(1) int nombreRequis,
    String siteId,
    String organisationId,
    LocalDate dateDebut,
    LocalDate dateFin,
    Integer priorite
) {}
