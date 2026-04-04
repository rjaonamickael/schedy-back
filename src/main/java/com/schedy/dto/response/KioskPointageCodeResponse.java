package com.schedy.dto.response;

/**
 * Public kiosk DTO. PIN is intentionally excluded — the kiosk display
 * only needs the site code. PIN is returned only on authenticated endpoints.
 * The internal {@code id} and {@code pinHash} fields are also excluded.
 */
public record KioskPointageCodeResponse(
    String siteId,
    String code,
    int rotationValeur,
    String rotationUnite,
    String validFrom,
    String validTo,
    boolean actif
) {}
