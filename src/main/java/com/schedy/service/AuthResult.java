package com.schedy.service;

import com.schedy.dto.response.AuthResponse;

/**
 * SEC-20 / Sprint 11 : internal tuple returned by {@link AuthService} issuing flows
 * (login, register, refresh rotation, completeLogin).
 *
 * <p>The service stays a pure POJO service — it does not touch the Servlet API.
 * {@link com.schedy.controller.AuthController} consumes this tuple, builds a
 * {@code ResponseCookie} from {@link #rawRefreshToken()} and emits a
 * {@code Set-Cookie} header alongside the {@link AuthResponse} body.</p>
 *
 * <p>{@code rawRefreshToken} is the JWT in clear text (not the SHA-256 hash
 * stored in {@code User.refreshToken}) — the controller must hand it to the
 * browser as-is so the browser can send it back on the next /refresh call.</p>
 *
 * @param response       the JSON body served to the client (never contains the refresh token)
 * @param rawRefreshToken the refresh JWT to be packed into the HttpOnly cookie, or {@code null}
 *                        when the flow does not issue a refresh token (e.g. 2FA challenge).
 */
public record AuthResult(AuthResponse response, String rawRefreshToken) {

    public static AuthResult withRefresh(AuthResponse response, String rawRefreshToken) {
        return new AuthResult(response, rawRefreshToken);
    }

    public static AuthResult bodyOnly(AuthResponse response) {
        return new AuthResult(response, null);
    }
}
