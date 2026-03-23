package com.schedy.dto;

import jakarta.validation.constraints.NotBlank;

public record OrganisationDto(
    String id,
    @NotBlank String nom,
    String domaine,
    String adresse,
    String telephone
) {}
