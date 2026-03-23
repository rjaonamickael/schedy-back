package com.schedy.dto;

import jakarta.validation.constraints.NotBlank;

public record SiteDto(
    String id,
    @NotBlank String nom,
    String adresse,
    String telephone,
    String organisationId,
    boolean actif
) {}
