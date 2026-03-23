package com.schedy.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleDto(
    String id,
    @NotBlank String nom,
    int importance,
    String couleur,
    String organisationId
) {}
