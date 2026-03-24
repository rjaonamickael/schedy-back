package com.schedy.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public record PointageDto(
    String id,
    String employeId,
    @NotBlank String type,
    OffsetDateTime horodatage,
    @NotBlank String methode,
    @NotBlank String statut,
    String anomalie,
    String siteId,
    String organisationId
) {}
