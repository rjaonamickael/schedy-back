package com.schedy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleDto(
    String id,
    @NotBlank String nom,
    int importance,
    String couleur,
    @Size(max = 50)
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]*[a-z0-9]$", message = "L'icone doit etre un identifiant en minuscules avec tirets (ex: chef-hat)")
    String icone,
    String organisationId
) {}
