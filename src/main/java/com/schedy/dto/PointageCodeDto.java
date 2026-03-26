package com.schedy.dto;

public record PointageCodeDto(
    String id,
    String siteId,
    String code,
    String pin,
    int rotationValeur,
    String rotationUnite,
    String validFrom,
    String validTo,
    boolean actif
) {}
