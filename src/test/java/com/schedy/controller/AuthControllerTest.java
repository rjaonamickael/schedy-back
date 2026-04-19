package com.schedy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schedy.config.JwtAuthFilter;
import com.schedy.config.JwtUtil;
import com.schedy.dto.request.AuthRequest;
import com.schedy.dto.request.ForgotPasswordRequest;
import com.schedy.dto.request.RegisterRequest;
import com.schedy.dto.request.ResetPasswordRequest;
import com.schedy.dto.response.AuthResponse;
import com.schedy.service.AuthResult;
import com.schedy.service.AuthService;
import com.schedy.service.TotpService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-05 / Sprint 10 : {@code @WebMvcTest} slice for {@link AuthController}.
 * <br>SEC-20 / Sprint 11 : migrated to the HttpOnly cookie contract.
 *
 * <p>Covers the core auth flows that reach production untouched by a framework
 * test : login (200 + 2FA challenge), register (201 + 409 email conflict),
 * refresh (200 + 401), logout (204), forgot/reset password, and the 2FA verify
 * endpoint. Security filters are disabled at the slice level — this is about
 * routing, validation, exception mapping and the new cookie contract, not
 * authentication.</p>
 *
 * <p>The {@code @PreAuthorize} annotations on this controller are a no-op because
 * {@code /api/v1/auth/**} is {@code permitAll()} in production. They are therefore
 * out of scope here.</p>
 *
 * @see PlanningControllerTest for the pattern this test mirrors
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:4200,https://app.schedy.com",
        "spring.profiles.active=dev"
})
@DisplayName("AuthController @WebMvcTest")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;
    @MockitoBean private TotpService totpService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    private static final String RAW_REFRESH = "raw-refresh-jwt-abc.def.ghi";

    private AuthResponse authenticatedResponse() {
        return AuthResponse.authenticated(
                "access-tok", "alice@example.com",
                "ADMIN", "emp-1", "org-1", "FR");
    }

    private AuthResult authenticatedResult() {
        return AuthResult.withRefresh(authenticatedResponse(), RAW_REFRESH);
    }

    // ── POST /login ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login returns 200 with body + refreshToken HttpOnly cookie")
    void login_returns200WithCookieOnHappyPath() throws Exception {
        when(authService.login(any(AuthRequest.class), any(HttpServletRequest.class))).thenReturn(authenticatedResult());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthRequest("alice@example.com", "Hunter2!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-tok"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                // SEC-20 : refresh token is NEVER in the body — only in the Set-Cookie header
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=" + RAW_REFRESH)))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=604800")));
    }

    @Test
    @DisplayName("POST /login returns 200 with 2FA challenge and NO cookie when 2FA is enabled")
    void login_returns200WithPendingTokenAndNoCookieWhen2faEnabled() throws Exception {
        when(authService.login(any(AuthRequest.class), any(HttpServletRequest.class)))
                .thenReturn(AuthResult.bodyOnly(AuthResponse.pending2fa("pending-tok", 300)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthRequest("alice@example.com", "Hunter2!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requires2fa").value(true))
                .andExpect(jsonPath("$.pendingToken").value("pending-tok"))
                .andExpect(jsonPath("$.codeExpirySeconds").value(300))
                // No refresh cookie at the 2FA challenge step — issued only after /2fa/verify succeeds
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("POST /login returns 401 when service throws unauthorized ResponseStatusException")
    void login_returns401OnInvalidCredentials() throws Exception {
        when(authService.login(any(AuthRequest.class), any(HttpServletRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthRequest("alice@example.com", "bad"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Email ou mot de passe incorrect"));
    }

    @Test
    @DisplayName("POST /login returns 400 when email is missing (no service call)")
    void login_returns400OnBlankEmail() throws Exception {
        AuthRequest invalid = new AuthRequest("", "Hunter2!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists());

        verify(authService, never()).login(any(), any());
    }

    // ── POST /register ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /register returns 201 Created on valid payload with refresh cookie")
    void register_returns201WithCookie() throws Exception {
        when(authService.register(any(RegisterRequest.class), any(HttpServletRequest.class))).thenReturn(authenticatedResult());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("bob@example.com", "Hunter2!", null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")));
    }

    @Test
    @DisplayName("POST /register returns 409 Conflict when email already exists")
    void register_returns409WhenEmailExists() throws Exception {
        when(authService.register(any(RegisterRequest.class), any(HttpServletRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Un utilisateur avec cet email existe deja"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("bob@example.com", "Hunter2!", null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /register returns 400 when password violates @Pattern (no service call)")
    void register_returns400OnWeakPassword() throws Exception {
        // "onlyletters" violates the Pattern (needs digit + special char)
        RegisterRequest invalid = new RegisterRequest(
                "bob@example.com", "onlyletters", null, null, null);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());

        verify(authService, never()).register(any(), any());
    }

    // ── POST /refresh (SEC-20 cookie-based) ─────────────────────────────

    @Test
    @DisplayName("POST /refresh returns 200 with rotated cookie when request carries a valid cookie")
    void refresh_returns200WithRotatedCookie() throws Exception {
        String newRawRefresh = "new-raw-refresh-xyz";
        AuthResult rotated = AuthResult.withRefresh(authenticatedResponse(), newRawRefresh);
        when(authService.refresh(eq(RAW_REFRESH), any(HttpServletRequest.class))).thenReturn(rotated);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", RAW_REFRESH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-tok"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                // Rotation : new cookie value overwrites the old one (same name + path + domain)
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=" + newRawRefresh)))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")));
    }

    @Test
    @DisplayName("POST /refresh returns 401 when no refreshToken cookie is present")
    void refresh_returns401WhenCookieMissing() throws Exception {
        when(authService.refresh(isNull(), any(HttpServletRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expiree : refresh token manquant"));

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("POST /refresh returns 401 when cookie is present but invalid")
    void refresh_returns401WhenCookieInvalid() throws Exception {
        when(authService.refresh(eq("bad"), any(HttpServletRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expire"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "bad")))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /logout (SEC-20 cookie-based) ──────────────────────────────

    @Test
    @DisplayName("POST /logout returns 204 and issues a clearing cookie (Max-Age=0)")
    void logout_returns204WithClearingCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", RAW_REFRESH)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")));

        verify(authService).logout(RAW_REFRESH);
    }

    @Test
    @DisplayName("POST /logout is idempotent : returns 204 even with no cookie, no service call")
    void logout_returns204WhenCookieAbsent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                // Still emits the clearing cookie so any stale client cookie is deleted
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(authService, never()).logout(anyString());
    }

    // ── POST /forgot-password + /reset-password ──────────────────────────

    @Test
    @DisplayName("POST /forgot-password always returns 200 (user enumeration guard)")
    void forgotPassword_alwaysReturns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ForgotPasswordRequest("ghost@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(authService).initiateForgotPassword("ghost@example.com");
    }

    @Test
    @DisplayName("POST /forgot-password returns 400 when email is blank")
    void forgotPassword_returns400OnBlankEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists());

        verify(authService, never()).initiateForgotPassword(anyString());
    }

    @Test
    @DisplayName("POST /reset-password returns 200 on success")
    void resetPassword_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest("reset-tok", "Hunter2!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(authService).resetPassword(any(ResetPasswordRequest.class));
    }

    @Test
    @DisplayName("POST /reset-password returns 400 when new password violates @Pattern")
    void resetPassword_returns400OnWeakPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest("reset-tok", "tooshort"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword").exists());

        verify(authService, never()).resetPassword(any());
    }

    // ── GET /validate-invitation ─────────────────────────────────────────

    @Test
    @DisplayName("GET /validate-invitation returns 200 with email for valid token")
    void validateInvitation_returns200() throws Exception {
        when(authService.validateInvitationToken("inv-tok"))
                .thenReturn(Map.of("email", "charlie@example.com", "nom", "Charlie"));

        mockMvc.perform(get("/api/v1/auth/validate-invitation").param("token", "inv-tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("charlie@example.com"))
                .andExpect(jsonPath("$.nom").value("Charlie"));
    }

    // ── POST /2fa/verify ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /2fa/verify returns 200 with body + refresh cookie on valid TOTP code")
    void verify2fa_returns200WithCookieOnValidTotpCode() throws Exception {
        when(jwtUtil.isTokenValid("pending-tok")).thenReturn(true);
        when(jwtUtil.is2faPendingToken("pending-tok")).thenReturn(true);
        when(jwtUtil.extractEmail("pending-tok")).thenReturn("alice@example.com");
        when(totpService.verify("alice@example.com", "123456")).thenReturn(true);
        when(authService.completeLogin(eq("alice@example.com"), any(HttpServletRequest.class))).thenReturn(authenticatedResult());

        mockMvc.perform(post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("pendingToken", "pending-tok", "code", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-tok"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));
    }

    @Test
    @DisplayName("POST /2fa/verify returns 401 when pending token is invalid")
    void verify2fa_returns401WhenPendingTokenInvalid() throws Exception {
        when(jwtUtil.isTokenValid("bad-tok")).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("pendingToken", "bad-tok", "code", "123456"))))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).completeLogin(anyString(), any());
    }

    @Test
    @DisplayName("POST /2fa/verify returns 401 when both TOTP and email codes fail")
    void verify2fa_returns401WhenAllCodesInvalid() throws Exception {
        when(jwtUtil.isTokenValid("pending-tok")).thenReturn(true);
        when(jwtUtil.is2faPendingToken("pending-tok")).thenReturn(true);
        when(jwtUtil.extractEmail("pending-tok")).thenReturn("alice@example.com");
        when(totpService.verify(eq("alice@example.com"), anyString())).thenReturn(false);
        when(authService.verifyEmail2faCode(eq("alice@example.com"), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("pendingToken", "pending-tok", "code", "999999"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /2fa/verify returns 400 when body is missing required fields")
    void verify2fa_returns400OnMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pendingToken", "pending-tok"))))
                .andExpect(status().isBadRequest());
    }

    // ── V33-bis BE : Origin-header allow-list on /refresh and /logout ───────

    @Test
    @DisplayName("POST /refresh returns 403 when Origin header is cross-site (evil.com)")
    void refresh_returns403OnDisallowedOrigin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", RAW_REFRESH))
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("Origin non autorisée")));

        // Service must never be reached on Origin rejection
        verify(authService, never()).refresh(anyString(), any());
    }

    @Test
    @DisplayName("POST /refresh accepts an allowed Origin (cors.allowed-origins)")
    void refresh_acceptsAllowedOrigin() throws Exception {
        AuthResult rotated = AuthResult.withRefresh(authenticatedResponse(), "rotated-token");
        when(authService.refresh(eq(RAW_REFRESH), any(HttpServletRequest.class))).thenReturn(rotated);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", RAW_REFRESH))
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /refresh tolerates a missing Origin header (non-browser client)")
    void refresh_tolerateMissingOrigin() throws Exception {
        AuthResult rotated = AuthResult.withRefresh(authenticatedResponse(), "rotated-token");
        when(authService.refresh(eq(RAW_REFRESH), any(HttpServletRequest.class))).thenReturn(rotated);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", RAW_REFRESH)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /logout returns 403 when Origin is cross-site")
    void logout_returns403OnDisallowedOrigin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", RAW_REFRESH))
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                .andExpect(status().isForbidden());

        verify(authService, never()).logout(anyString());
    }

    // ── Sanity guard : refresh token must NOT leak into the body on any auth flow ─

    @Test
    @DisplayName("SEC-20 guard : login response body must not contain 'refreshToken' at all")
    void sec20_login_bodyMustNotContainRefreshTokenField() throws Exception {
        when(authService.login(any(AuthRequest.class), any(HttpServletRequest.class))).thenReturn(authenticatedResult());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthRequest("alice@example.com", "Hunter2!"))))
                .andExpect(status().isOk())
                // Hamcrest guard : even if a future regression brought the field back with a
                // null value, this assertion would fail because the JSON path would exist.
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }
}
