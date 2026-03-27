package com.schedy.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified response for all authentication flows.
 *
 * <h3>Normal login (2FA not enabled or not required)</h3>
 * All fields except {@code requires2fa} / {@code pendingToken} are populated.
 * {@code requires2fa} is {@code false}.
 *
 * <h3>2FA challenge (2FA enabled)</h3>
 * {@code requires2fa=true}, {@code pendingToken} is set.
 * All other fields are {@code null} — the client must complete the 2FA step.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
    String  accessToken,
    String  refreshToken,
    String  email,
    String  role,
    String  employeId,
    String  organisationId,
    String  pays,

    // 2FA challenge fields — null when requires2fa=false
    Boolean requires2fa,
    String  pendingToken,
    Integer codeExpirySeconds
) {
    public static AuthResponse authenticated(
            String accessToken,
            String refreshToken,
            String email,
            String role,
            String employeId,
            String organisationId,
            String pays) {
        return new AuthResponse(accessToken, refreshToken, email, role,
                employeId, organisationId, pays, null, null, null);
    }

    public static AuthResponse pending2fa(String pendingToken, int codeExpirySeconds) {
        return new AuthResponse(null, null, null, null,
                null, null, null, true, pendingToken, codeExpirySeconds);
    }
}
