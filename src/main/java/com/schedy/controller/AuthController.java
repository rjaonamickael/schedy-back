package com.schedy.controller;

import com.schedy.config.JwtUtil;
import com.schedy.dto.request.AuthRequest;
import com.schedy.dto.request.ForgotPasswordRequest;
import com.schedy.dto.request.RefreshRequest;
import com.schedy.dto.request.RegisterRequest;
import com.schedy.dto.request.ResetPasswordRequest;
import com.schedy.dto.request.SetPasswordRequest;
import com.schedy.dto.response.AuthResponse;
import com.schedy.service.AuthService;
import com.schedy.service.TotpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TotpService totpService;
    private final JwtUtil     jwtUtil;

    // ─────────────────────────────────────────────────────────────────
    // Core auth endpoints
    // ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/auth/login
     * Returns 200 with full tokens when 2FA is not enabled.
     * Returns 200 with {@code requires2fa=true} + {@code pendingToken} when 2FA is active.
     * Clients MUST check the {@code requires2fa} flag before proceeding to the dashboard.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
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
    // 2FA setup/management endpoints (require full authentication)
    // ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/auth/2fa/setup
     * Generates a new TOTP secret for the authenticated user and returns
     * the secret, otpauth URI, and a QR-code PNG as a data URI.
     * Requires: full JWT (isAuthenticated), 2FA must NOT already be enabled.
     */
    // ─────────────────────────────────────────────────────────────────
    // 2FA setup/disable/status moved to UserController (/api/v1/user/2fa/*)
    // because /api/v1/auth/** is permitAll() in SecurityConfig.
    // ─────────────────────────────────────────────────────────────────
    // 2FA verification endpoints (unauthenticated — use pendingToken)
    // ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/auth/2fa/verify
     * Second step of the 2FA login flow. Validates the TOTP code against the pending token
     * and issues full access + refresh tokens on success.
     * Body: {@code { "pendingToken": "...", "code": "123456" }}
     * Does NOT require a Bearer token — uses the {@code pendingToken} from the body.
     */
    @PostMapping("/2fa/verify")
    public ResponseEntity<AuthResponse> verify2fa(@RequestBody Map<String, String> body) {
        String pendingToken = extractRequired(body, "pendingToken");
        String code         = extractRequired(body, "code");

        validatePendingToken(pendingToken);

        String email = jwtUtil.extractEmail(pendingToken);
        // Try TOTP app code first, then email code
        if (totpService.verify(email, code) || authService.verifyEmail2faCode(email, code)) {
            return ResponseEntity.ok(authService.completeLogin(email));
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
    public ResponseEntity<AuthResponse> useRecoveryCode(@RequestBody Map<String, String> body) {
        String pendingToken = extractRequired(body, "pendingToken");
        String code         = extractRequired(body, "code");

        validatePendingToken(pendingToken);

        String email = jwtUtil.extractEmail(pendingToken);
        if (!totpService.verifyRecoveryCode(email, code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Code de récupération invalide ou déjà utilisé.");
        }

        return ResponseEntity.ok(authService.completeLogin(email));
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    private void validatePendingToken(String pendingToken) {
        if (!jwtUtil.isTokenValid(pendingToken) || !jwtUtil.is2faPendingToken(pendingToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Token 2FA invalide ou expiré. Veuillez relancer la connexion.");
        }
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
