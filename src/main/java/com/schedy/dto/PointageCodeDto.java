package com.schedy.dto;

public record PointageCodeDto(
    String id,
    String siteId,
    String code,
    String pin,
    String frequence,
    String validFrom,
    String validTo,
    boolean actif
) {}
