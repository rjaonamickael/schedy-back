package com.schedy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OrganisationDto(
    String id,
    @NotBlank String nom,
    String domaine,
    String adresse,
    String telephone,
    @Pattern(regexp = "^[A-Z]{3}$", message = "Pays must be ISO alpha-3 (e.g., USA, CAN, MDG)")
    String pays
) {}
