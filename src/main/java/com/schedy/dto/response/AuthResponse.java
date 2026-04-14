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
 *
 * <h3>SEC-20 / Sprint 11 : refreshToken is NOT in the body</h3>
 * Since Sprint 11, the refresh token is delivered via an HttpOnly cookie
 * ({@code Set-Cookie: refreshToken=...; HttpOnly; SameSite=Lax; Path=/api/v1/auth; Max-Age=604800})
 * and is never exposed to JavaScript. The {@code refreshToken} body field has
 * been removed to make the contract unambiguous — any frontend still reading
 * {@code response.refreshToken} will get a compile error (TS) or a silent
 * {@code undefined}, both of which surface the migration gap immediately.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
    String  accessToken,
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
            String email,
            String role,
            String employeId,
            String organisationId,
            String pays) {
        return new AuthResponse(accessToken, email, role,
                employeId, organisationId, pays, null, null, null);
    }

    public static AuthResponse pending2fa(String pendingToken, int codeExpirySeconds) {
        return new AuthResponse(null, null, null,
                null, null, null, true, pendingToken, codeExpirySeconds);
    }
}
