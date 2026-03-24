package com.schedy.dto.response;

/**
 * Public kiosk DTO that intentionally excludes the PIN.
 * The kiosk display only needs the code (for QR) and validity window.
 */
public record KioskPointageCodeResponse(
    String siteId,
    String code,
    String frequence,
    String validFrom,
    String validTo,
    boolean actif
) {}
