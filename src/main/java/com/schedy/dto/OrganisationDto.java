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
    String pays,
    @Pattern(regexp = "^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$",
             message = "dateRenouvellementConges must be MM-DD (e.g., 01-01)")
    String dateRenouvellementConges
) {}
