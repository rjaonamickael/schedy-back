package com.schedy.dto;

import com.schedy.entity.DisponibilitePlage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record EmployeDto(
    String id,
    @NotBlank @Size(max = 255) String nom,
    @Size(max = 255) String role,
    @Size(max = 50) String telephone,
    @Size(max = 255) String email,
    LocalDate dateNaissance,
    LocalDate dateEmbauche,
    @Size(max = 10) String pin,
    String organisationId,
    List<DisponibilitePlage> disponibilites,
    List<String> siteIds
) {}
