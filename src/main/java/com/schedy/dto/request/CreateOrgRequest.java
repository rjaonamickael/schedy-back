package com.schedy.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateOrgRequest(
    @NotBlank String name,
    @NotBlank @Email String adminEmail,
    @NotBlank String adminPassword,
    @Pattern(regexp = "^[A-Z]{3}$", message = "Pays must be ISO alpha-3 (e.g., USA, CAN, MDG)")
    String pays
) {}
