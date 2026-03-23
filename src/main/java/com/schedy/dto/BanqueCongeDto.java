package com.schedy.dto;

import java.time.LocalDate;

public record BanqueCongeDto(
    String id,
    String employeId,
    String typeCongeId,
    Double quota,
    double utilise,
    double enAttente,
    LocalDate dateDebut,
    LocalDate dateFin,
    String organisationId
) {}
