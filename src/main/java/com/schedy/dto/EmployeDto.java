package com.schedy.dto;

import com.schedy.entity.DisponibilitePlage;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public record EmployeDto(
    String id,
    @NotBlank String nom,
    String role,
    String telephone,
    String email,
    LocalDate dateNaissance,
    LocalDate dateEmbauche,
    String pin,
    String organisationId,
    List<DisponibilitePlage> disponibilites,
    List<String> siteIds
) {}
