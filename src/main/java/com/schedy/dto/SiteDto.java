package com.schedy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SiteDto(
    String id,
    @NotBlank @Size(max = 255) String nom,
    @Size(max = 255) String adresse,
    @Size(max = 50) String telephone,
    String organisationId,
    boolean actif
) {}
