package com.schedy.dto.response;

/**
 * Public kiosk DTO. PIN is included because the kiosk display is the
 * authorised surface for showing the PIN to employees clocking in.
 * The internal {@code id} and {@code pinHash} fields are intentionally excluded.
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
