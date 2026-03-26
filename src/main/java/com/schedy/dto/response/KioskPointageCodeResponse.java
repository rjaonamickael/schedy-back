package com.schedy.dto.response;

/**
 * Public kiosk DTO. PIN is intentionally excluded — the PIN belongs to the
 * authenticated GET /site/{siteId} endpoint (PointageCodeDto) so it is never
 * exposed on the unauthenticated kiosk endpoint.
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
