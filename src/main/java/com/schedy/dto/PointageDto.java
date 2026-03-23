package com.schedy.dto;

import java.time.LocalDateTime;

public record PointageDto(
    String id,
    String employeId,
    String type,
    LocalDateTime horodatage,
    String methode,
    String statut,
    String anomalie,
    String siteId,
    String organisationId
) {}
