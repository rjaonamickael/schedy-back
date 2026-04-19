package com.schedy.controller;

import com.schedy.config.JwtUtil;
import com.schedy.dto.request.AuthRequest;
import com.schedy.dto.request.ForgotPasswordRequest;
import com.schedy.dto.request.RegisterRequest;
import com.schedy.dto.request.ResetPasswordRequest;
import com.schedy.dto.request.SetPasswordRequest;
import com.schedy.dto.response.AuthResponse;
import com.schedy.service.AuthResult;
import com.schedy.service.AuthService;
import com.schedy.service.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * SEC-20 / Sprint 11 : cookie name for the refresh JWT. Scoped to
     * {@link #REFRESH_COOKIE_PATH} so it is only sent on auth endpoints.
     */
    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(7); // must match jwt.refresh-token-expiration

    private final AuthService authService;
    private final TotpService totpService;
    private final JwtUtil     jwtUtil;

    /**
     * SEC-20 : toggled by profile. {@code false} in dev (HTTP localhost) so the cookie
     * is accepted over plain HTTP. {@code true} in prod so the browser refuses to
     * send the cookie over HTTP and cannot be forced off HTTPS by a MITM.
     */
    @Value("${schedy.cookie.secure:false}")
    private boolean cookieSecure;

    /**
     * SEC-20 : empty in dev (host-only cookie on localhost). In prod set to
     * {@code .schedy.com} so the cookie is shared between the {@code app.} and
     * {@code api.} subdomains on the same registrable domain. Never set to a
     * wildcard — that would broaden the blast radius to all subdomains.
     */
    @Value("${schedy.cookie.domain:}")
    private String cookieDomain;

    /**
     * V33-bis BE / Sprint 12 : Origin-header allow-list for the defense-in-depth
     * CSRF check on {@code /refresh} and {@code /logout}. Mirrors the CORS config
     * (same env var) so operators maintain a single source of truth. In dev we
     * additionally allow any {@code localhost} / LAN origin to keep mobile testing
     * painless (the CorsConfig does the same).
     */
    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String allowedOriginsCsv;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    // ─────────────────────────────────────────────────────────────────
    // Core auth endpoints
    // ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/auth/login
     * Returns 200 with the access token body when 2FA is not enabled.
     * Returns 200 with {@code requires2fa=true} + {@code pendingToken} when 2FA is active.
     * On successful password verification (no 2FA) the refresh token is issued as an
     * HttpOnly {@code refreshToken} cookie — never in the JSON body.
     * Clients MUST check the {@code requires2fa} flag before proceeding to the dashboard.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request,
                                              HttpServletRequest httpReq) {
        AuthResult result = authService.login(request, httpReq);
        return buildAuthResponse(result, HttpStatus.OK);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpReq) {
        AuthResult result = authService.register(request, httpReq);
        return buildAuthResponse(result, HttpStatus.CREATED);
    }

    /**
     * POST /api/v1/auth/refresh
     * SEC-20 / Sprint 11 : reads the refresh JWT from the {@code refreshToken} HttpOnly
     * cookie instead of a request body. No body is accepted. Returns 401 when the
     * cookie is missing, expired, or mismatched with the server-side hash.
     * On success, a new cookie is issued (rotation) — it overwrites the old one in
     * the browser because of the shared name+path+domain.
     *
     * <p>V33-bis BE / Sprint 12 : defense-in-depth CSRF guard — the Origin header,
     * when present, must match the configured allow-list. Rejected with 403 otherwise.
     * This is belt-and-suspenders on top of {@code SameSite=Lax}, useful if the app
     * is ever deployed under a compromised/unexpected subdomain of the same site.</p>
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        enforceAllowedOrigin(request);
        AuthResult result = authService.refresh(refreshToken, request);
        return buildAuthResponse(result, HttpStatus.OK);
    }

    /**
     * POST /api/v1/auth/logout
     * SEC-20 / Sprint 11 : reads the refresh JWT from the HttpOnly cookie. The response
     * carries a clearing {@code Set-Cookie} header ({@code Max-Age=0}) with the exact
     * same Path/Domain/Secure/SameSite attributes so the browser deletes the cookie.
     * <p>Idempotent : if no cookie is present there is no session to invalidate and
     * the endpoint still returns 204.</p>
     *
     * <p>V33-bis BE / Sprint 12 : same Origin allow-list guard as /refresh.</p>
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        enforceAllowedOrigin(request);
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, buildClearingCookie().toString())
                .build();
    }

    @GetMapping("/validate-invitation")
    public ResponseEntity<Map<String, String>> validateInvitation(@RequestParam String token) {
        return ResponseEntity.ok(authService.validateInvitationToken(token));
    }

    @PostMapping("/set-password")
    public ResponseEntity<Void> setPassword(@Valid @RequestBody SetPasswordRequest request) {
        authService.setPasswordFromInvitation(request);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/v1/auth/forgot-password
     * Initiates the password-reset flow. Always returns 200 regardless of whether the
     * email is registered in order to prevent user enumeration.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.initiateForgotPassword(request.email());
        return ResponseEntity.ok(Map.of("message", "If an account exists, an email has been sent."));
    }

    /**
     * POST /api/v1/auth/reset-password
     * Validates the reset token and sets the new password.
     * Returns 400 when the token is invalid or expired.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully."));
    }

    // ─────────────────────────────────────────────────────────────────
    // 2FA verification endpoints (unauthenticated — use pendingToken)
    // ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/auth/2fa/verify
     * Second step of the 2FA login flow. Validates the TOTP code against the pending token
     * and issues full access + refresh tokens on success.
     * Body: {@code { "pendingToken": "...", "code": "123456" }}
     * Does NOT require a Bearer token — uses the {@code pendingToken} from the body.
     * On success the refresh JWT is issued as an HttpOnly cookie (same contract as /login).
     */
    @PostMapping("/2fa/verify")
    public ResponseEntity<AuthResponse> verify2fa(@RequestBody Map<String, String> body,
                                                  HttpServletRequest httpReq) {
        String pendingToken = extractRequired(body, "pendingToken");
        String code         = extractRequired(body, "code");

        validatePendingToken(pendingToken);

        String email = jwtUtil.extractEmail(pendingToken);
        // S-06: Try TOTP app code first. If TOTP succeeds, clear the outstanding
        // email code so it cannot be reused as a separate auth path.
        if (totpService.verify(email, code)) {
            authService.clearEmail2faCode(email);
            return buildAuthResponse(authService.completeLogin(email, httpReq), HttpStatus.OK);
        }
        // Fall back to email code verification (email code is cleared on success
        // inside verifyEmail2faCode itself).
        if (authService.verifyEmail2faCode(email, code)) {
            return buildAuthResponse(authService.completeLogin(email, httpReq), HttpStatus.OK);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            "Code invalide ou expir\u00e9.");
    }

    @PostMapping("/2fa/resend-code")
    public ResponseEntity<Void> resend2faCode(@RequestBody Map<String, String> body) {
        String pendingToken = extractRequired(body, "pendingToken");
        validatePendingToken(pendingToken);
        String email = jwtUtil.extractEmail(pendingToken);
        authService.resendEmail2faCode(email);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/v1/auth/2fa/recovery
     * Alternate second step: uses a recovery code instead of a TOTP code.
     * The recovery code is marked as used on success.
     * Body: {@code { "pendingToken": "...", "code": "XXXXX-XXXXX" }}
     * Does NOT require a Bearer token.
     */
    @PostMapping("/2fa/recovery")
    public ResponseEntity<AuthResponse> useRecoveryCode(@RequestBody Map<String, String> body,
                                                        HttpServletRequest httpReq) {
        String pendingToken = extractRequired(body, "pendingToken");
        String code         = extractRequired(body, "code");

        validatePendingToken(pendingToken);

        String email = jwtUtil.extractEmail(pendingToken);
        if (!totpService.verifyRecoveryCode(email, code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Code de récupération invalide ou déjà utilisé.");
        }

        return buildAuthResponse(authService.completeLogin(email, httpReq), HttpStatus.OK);
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * SEC-20 : packs the {@link AuthResult} into a {@link ResponseEntity} and, when a
     * raw refresh token is present, adds the {@code Set-Cookie} header. For the 2FA
     * challenge case ({@code rawRefreshToken == null}) only the body is sent.
     */
    private ResponseEntity<AuthResponse> buildAuthResponse(AuthResult result, HttpStatus status) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (result.rawRefreshToken() != null) {
            ResponseCookie cookie = buildRefreshCookie(result.rawRefreshToken());
            builder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return builder.body(result.response());
    }

    /**
     * SEC-20 : builds the refresh-token cookie with the flags required by the browser
     * to (a) keep it unreachable from JavaScript (HttpOnly), (b) refuse downgrade to
     * HTTP in prod (Secure), (c) send it only on top-level or same-site requests
     * (SameSite=Lax), (d) scope it to the auth endpoints (Path=/api/v1/auth).
     */
    private ResponseCookie buildRefreshCookie(String rawRefreshToken) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(REFRESH_COOKIE_MAX_AGE);
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            b.domain(cookieDomain);
        }
        return b.build();
    }

    /**
     * SEC-20 : builds the same cookie with {@code Max-Age=0} so the browser deletes it.
     * Must share the exact Path/Domain/Secure/SameSite attributes as the set cookie —
     * otherwise the browser will keep the old one.
     */
    private ResponseCookie buildClearingCookie() {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO);
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            b.domain(cookieDomain);
        }
        return b.build();
    }

    private void validatePendingToken(String pendingToken) {
        if (!jwtUtil.isTokenValid(pendingToken) || !jwtUtil.is2faPendingToken(pendingToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Token 2FA invalide ou expiré. Veuillez relancer la connexion.");
        }
    }

    /**
     * V33-bis BE / Sprint 12 : CSRF defense-in-depth. Called at the top of
     * {@code /refresh} and {@code /logout}. Blocks any request whose {@code Origin}
     * header is set to a host NOT in the configured allow-list (mirrors
     * {@code cors.allowed-origins}).
     *
     * <p>Rationale : {@code SameSite=Lax} already rejects the cookie on cross-site
     * POSTs. This helper is a second line of defense against attackers who find a
     * way to run JavaScript from an unexpected same-site context (subdomain
     * takeover, misconfigured reverse-proxy, etc.). It also catches requests from
     * rogue browser extensions that bypass SameSite semantics.</p>
     *
     * <p>An ABSENT {@code Origin} header is tolerated (returns silently) so that
     * server-side clients (curl, Postman on localhost, health checks) are not
     * broken. Browsers always send {@code Origin} on POST, so the null case is
     * exclusively non-browser traffic.</p>
     */
    private void enforceAllowedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || origin.isBlank()) {
            return; // non-browser client (curl, server-to-server, health check)
        }

        for (String allowed : allowedOriginsCsv.split(",")) {
            if (origin.equalsIgnoreCase(allowed.trim())) {
                return;
            }
        }

        // Dev profile : tolerate localhost and private-LAN origins for mobile testing.
        // Mirrors the patterns in CorsConfig.
        if ("dev".equalsIgnoreCase(activeProfile)) {
            if (origin.startsWith("http://localhost:")
                    || origin.matches("^http://192\\.168\\.[0-9]+\\.[0-9]+(:[0-9]+)?$")
                    || origin.matches("^http://10\\.[0-9.]+(:[0-9]+)?$")
                    || origin.matches("^http://172\\.16\\.[0-9.]+(:[0-9]+)?$")) {
                return;
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Origin non autorisée : " + origin);
    }

    private String extractRequired(Map<String, String> body, String key) {
        String value = body == null ? null : body.get(key);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Le champ '" + key + "' est obligatoire.");
        }
        return value.trim();
    }
}
