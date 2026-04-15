package com.schedy.dto.response;

/**
 * Public kiosk DTO. The {@code pin} is included because the kiosk screen
 * displays it to employees so they can clock in via the PIN pad. The kiosk
 * is a trusted public display — the PIN is already visible on-screen, so
 * hiding it from the API while showing it in the UI would be inconsistent.
 * Only the internal {@code id} and {@code pinHash} fields are excluded.
 */
public record KioskPointageCodeResponse(
    String siteId,
    String code,
    String pin,
    int rotationValeur,
    String rotationUnite,
    String validFrom,
    String validTo,
    boolean actif
) {}
