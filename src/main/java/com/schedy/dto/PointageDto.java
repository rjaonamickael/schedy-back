package com.schedy.dto;

import java.time.OffsetDateTime;

public record PointageDto(
    String id,
    String employeId,
    String type,
    OffsetDateTime horodatage,
    String methode,
    String statut,
    String anomalie,
    String siteId,
    String organisationId
) {}
