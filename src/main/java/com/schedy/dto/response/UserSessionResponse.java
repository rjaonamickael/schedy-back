package com.schedy.dto.response;

import java.time.Instant;

/**
 * S18-BE-04 — Public projection of a {@link com.schedy.entity.UserSession}
 * for the {@code /api/v1/user/sessions} endpoint family. Exposes device
 * metadata (user-agent, IP), timestamps, and the {@code isCurrent} flag so
 * the UI can show "this device" and disable self-revoke on the current row.
 *
 * <p>The {@code tokenHash} is deliberately NOT exposed — it would let an
 * attacker correlate a leaked session list with an intercepted refresh
 * cookie.</p>
 */
public record UserSessionResponse(
        Long id,
        String userAgent,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean isCurrent
) {}
