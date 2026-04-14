package com.schedy.dto;

import com.schedy.entity.DisponibilitePlage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 16 / Feature 2 : {@code role : String} has been replaced by
 * {@code roles : List<String>}. The list is ORDERED : index 0 is the role
 * principal (primary), index 1 is secondaire, etc. Display and scoring
 * reference this hierarchy explicitly.
 */
public record EmployeDto(
    String id,
    @NotBlank @Size(max = 255) String nom,
    List<String> roles,
    @Size(max = 50) String telephone,
    @Size(max = 255) String email,
    LocalDate dateNaissance,
    LocalDate dateEmbauche,
    @Size(min = 4, max = 10) String pin,
    String organisationId,
    List<DisponibilitePlage> disponibilites,
    List<String> siteIds
) {}
