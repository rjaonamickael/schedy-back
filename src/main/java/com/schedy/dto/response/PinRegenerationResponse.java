package com.schedy.dto.response;

import java.time.OffsetDateTime;

/**
 * V36: response returned to the admin UI after a PIN regeneration. Contains
 * the decrypted PIN (intended for immediate display + printing) plus the new
 * generation metadata so the frontend can show the updated state without
 * re-fetching the employee.
 */
public record PinRegenerationResponse(
        String employeId,
        String pinClair,
        OffsetDateTime pinGeneratedAt,
        Integer pinVersion
) {}
