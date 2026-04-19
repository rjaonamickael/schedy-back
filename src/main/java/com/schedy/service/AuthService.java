package com.schedy.service;

import com.schedy.config.JwtUtil;
import com.schedy.dto.request.AuthRequest;
import com.schedy.dto.request.ChangePasswordRequest;
import com.schedy.dto.request.ForgotPasswordRequest;
import com.schedy.dto.request.InviteAdminRequest;
import com.schedy.dto.request.RegisterRequest;
import com.schedy.dto.request.ResetPasswordRequest;
import com.schedy.dto.request.SetPasswordRequest;
import com.schedy.dto.request.UpdateProfileRequest;
import com.schedy.dto.response.AdminUserResponse;
import com.schedy.dto.response.AuthResponse;
import com.schedy.dto.response.UserProfileResponse;
import com.schedy.entity.User;
import com.schedy.entity.UserSession;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.EmployeRepository;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.SubscriptionRepository;
import com.schedy.repository.TestimonialRepository;
import com.schedy.repository.UserRepository;
import com.schedy.repository.UserSessionRepository;
import com.schedy.util.CryptoUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final EmployeRepository employeRepository;
    private final OrganisationRepository organisationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    // V49 — snapshot guard + R2 cleanup pour photo perso user.
    private final TestimonialRepository testimonialRepository;
    private final R2StorageService r2StorageService;

    /**
     * V50 — per-user cap on concurrent refresh-token sessions. A typical Schedy
     * user has laptop pro + laptop perso + mobile = 3 devices, so 5 gives
     * breathing room without becoming a vector for session-table bloat. When
     * the cap is reached, {@link #createSession} evicts the oldest session
     * (FIFO by row id).
     */
    private static final int MAX_SESSIONS_PER_USER = 5;

    /** Must match {@code jwt.refresh-token-expiration} and the cookie Max-Age. */
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    @Value("${schedy.invitation.expiry-hours:24}")
    private int invitationExpiryHours;

    @Value("${schedy.email-2fa.code-expiry-seconds:300}")
    private int email2faCodeExpirySeconds;

    // B-H16: Per-account lockout after repeated failed login attempts.
    // 5 failures within 15 minutes = account locked for 15 minutes.
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L; // 15 minutes
    // B-05: LIMITATION — This lockout map is in-memory only. It does NOT persist across restarts
    // and does NOT work in multi-instance deployments (horizontal scaling). Each JVM instance tracks
    // its own failed attempts independently, so an attacker can bypass lockout by cycling between
    // instances. A proper fix requires a distributed store (e.g., Redis with SETNX + TTL).
    // Accepted as-is for beta (single-instance). Must be replaced before scaling to multiple pods.
    private final Cache<String, FailedLoginTracker> failedLogins = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    private record FailedLoginTracker(int attempts, Instant firstAttempt, Instant lockedUntil) {
        boolean isLocked() {
            return lockedUntil != null && Instant.now().isBefore(lockedUntil);
        }
    }

    @Transactional
    public AuthResult register(RegisterRequest request, HttpServletRequest httpReq) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un utilisateur avec cet email existe d\u00e9j\u00e0 / A user with this email already exists");
        }

        User.UserRole role = User.UserRole.EMPLOYEE;
        if (request.role() != null) {
            try {
                role = User.UserRole.valueOf(request.role().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "R\u00f4le invalide : " + request.role());
            }
        }
        if (role == User.UserRole.SUPERADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "L'inscription en tant que SUPERADMIN n'est pas autoris\u00e9e.");
        }
        if (role == User.UserRole.ADMIN || role == User.UserRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "L'inscription en tant qu'" + role + " n'est pas autoris\u00e9e. Contactez un administrateur.");
        }

        // Self-registration must NOT allow joining an arbitrary organisation.
        // Only admins can assign users to organisations (prevents multi-tenant data leak).
        // organisationId and employeId from the request are ignored for self-registration.
        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(role)
                .employeId(null)
                .organisationId(null)
                .build();

        userRepository.save(user); // assign id so createSession can reference it
        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getOrganisationId());
        String refreshToken = createSession(user, httpReq);
        log.info("New user registered: {} with role {}", request.email(), role);

        AuthResponse response = AuthResponse.authenticated(accessToken, user.getEmail(), user.getRole().name(), user.getEmployeId(), user.getOrganisationId(), resolveOrgPays(user.getOrganisationId()));
        return AuthResult.withRefresh(response, refreshToken);
    }

    @Transactional
    public AuthResult login(AuthRequest request, HttpServletRequest httpReq) {
        String email = request.email().toLowerCase().trim();

        // B-H16: Check per-account lockout before attempting authentication
        FailedLoginTracker tracker = failedLogins.getIfPresent(email);
        if (tracker != null && tracker.isLocked()) {
            log.warn("Locked account login attempt: {}", email);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Compte temporairement verrouille apres trop de tentatives. Reessayez dans quelques minutes.");
        }

        // B-C8: Generic 401 for both unknown email and wrong password to prevent user enumeration.
        // Timing: passwordEncoder.matches() is called even on missing user to prevent timing side-channel.
        User user = userRepository.findByEmail(email).orElse(null);
        boolean authFailed = false;

        if (user == null) {
            passwordEncoder.matches(request.password(), "$2a$12$000000000000000000000uGbLI4Ryhzb3WU65Hm0n6/UlBfzLHPXi");
            authFailed = true;
        } else if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            authFailed = true;
        }

        if (authFailed) {
            recordFailedAttempt(email);
            log.warn("Failed login attempt for: {}", email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiant ou mot de passe incorrect");
        }

        // Successful password verification: clear failed attempts
        failedLogins.invalidate(email);

        // If 2FA is enabled, issue a short-lived pending token and send email code.
        // SEC-20: no refresh cookie is issued at this step — the browser only gets the pending token body.
        if (user.isTotpEnabled()) {
            String pendingToken = jwtUtil.generate2faPendingToken(user.getEmail());
            // Generate and send email 2FA code
            sendEmail2faCode(user);
            log.info("2FA challenge issued for: {}", user.getEmail());
            return AuthResult.bodyOnly(AuthResponse.pending2fa(pendingToken, email2faCodeExpirySeconds));
        }

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getOrganisationId());
        String refreshToken = createSession(user, httpReq);
        log.info("User logged in: {}", user.getEmail());

        AuthResponse response = AuthResponse.authenticated(accessToken, user.getEmail(), user.getRole().name(), user.getEmployeId(), user.getOrganisationId(), resolveOrgPays(user.getOrganisationId()));
        return AuthResult.withRefresh(response, refreshToken);
    }

    /**
     * Issues full access + refresh tokens after a successful 2FA verification step.
     * Called by {@link com.schedy.controller.AuthController} once TotpService confirms
     * the TOTP code or recovery code is valid.
     *
     * <p>SEC-20 / Sprint 11 : returns an {@link AuthResult} so the controller can pack
     * the raw refresh token into an HttpOnly cookie. The JSON body no longer carries it.</p>
     *
     * @param email   the user's email (extracted from the validated pendingToken)
     * @param httpReq current servlet request, used to record user-agent / IP on the new session
     * @return an {@link AuthResult} containing the access-token body + the raw refresh JWT
     */
    @Transactional
    public AuthResult completeLogin(String email, HttpServletRequest httpReq) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getOrganisationId());
        String refreshToken = createSession(user, httpReq);
        log.info("2FA login completed for: {}", user.getEmail());

        AuthResponse response = AuthResponse.authenticated(accessToken, user.getEmail(), user.getRole().name(),
                user.getEmployeId(), user.getOrganisationId(), resolveOrgPays(user.getOrganisationId()));
        return AuthResult.withRefresh(response, refreshToken);
    }

    private void recordFailedAttempt(String email) {
        failedLogins.asMap().compute(email, (key, existing) -> {
            Instant now = Instant.now();
            if (existing == null || now.toEpochMilli() - existing.firstAttempt().toEpochMilli() > LOCKOUT_DURATION_MS) {
                // First failure or window expired: start fresh
                return new FailedLoginTracker(1, now, null);
            }
            int newCount = existing.attempts() + 1;
            if (newCount >= MAX_FAILED_ATTEMPTS) {
                log.warn("Account locked due to {} failed attempts: {}", newCount, email);
                return new FailedLoginTracker(newCount, existing.firstAttempt(), now.plusMillis(LOCKOUT_DURATION_MS));
            }
            return new FailedLoginTracker(newCount, existing.firstAttempt(), null);
        });
    }

    /**
     * SEC-20 / Sprint 11 : takes the raw refresh JWT directly (read from the
     * {@code refreshToken} HttpOnly cookie by the controller) instead of a body DTO.
     * Throws 401 on missing/invalid/mismatched tokens — consistent with the rest
     * of the auth contract so the frontend interceptor can drop the session.
     *
     * <p>V50 : session lookup is keyed by {@link UserSession#getTokenHash()} instead
     * of the legacy {@code app_user.refresh_token} column, so the same account can
     * refresh independently on multiple devices. Rotation creates the new session
     * row <b>before</b> deleting the presented one so a crash between the two steps
     * at worst leaves one extra session (swept by the scheduled cleanup) — never a
     * gap that would force the user to log in again.</p>
     */
    @Transactional
    public AuthResult refresh(String token, HttpServletRequest httpReq) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expiree : refresh token manquant");
        }

        if (!jwtUtil.isTokenValid(token) || !jwtUtil.isRefreshToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expire");
        }

        String email = jwtUtil.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        UserSession oldSession = sessionRepository.findByTokenHash(hashToken(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Session invalide ou revoquee"));

        if (!oldSession.getUserId().equals(user.getId())) {
            // Defensive: the cookie's JWT email somehow maps to a different user's
            // session row. Should never happen but never hand back a token either.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session incoherente");
        }

        String newAccessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getOrganisationId());
        String newRefreshToken = createSession(user, httpReq);
        sessionRepository.delete(oldSession);
        log.debug("Token refreshed for: {}", email);

        AuthResponse response = AuthResponse.authenticated(newAccessToken, user.getEmail(), user.getRole().name(), user.getEmployeId(), user.getOrganisationId(), resolveOrgPays(user.getOrganisationId()));
        return AuthResult.withRefresh(response, newRefreshToken);
    }

    /**
     * V50 — logs out only the device whose refresh cookie was presented. Other
     * active sessions on the same account are untouched. Idempotent when the
     * token is already unknown (returning from a stale tab after logout-all).
     */
    @Transactional
    public void logout(String refreshToken) {
        sessionRepository.findByTokenHash(hashToken(refreshToken)).ifPresent(session -> {
            sessionRepository.delete(session);
            log.info("Session {} logged out (user id: {})", session.getId(), session.getUserId());
        });
    }

    /**
     * V50 — creates a fresh {@link UserSession} for the given user, enforcing
     * the per-user cap with FIFO eviction (oldest {@code id} first). Records
     * the user-agent and IP address when a servlet request is available so an
     * eventual "active sessions" screen can label devices meaningfully.
     *
     * @return the raw refresh JWT that must be sent to the browser as an
     *         HttpOnly cookie — only the SHA-256 hash is persisted server-side.
     */
    private String createSession(User user, HttpServletRequest httpReq) {
        long count = sessionRepository.countByUserId(user.getId());
        if (count >= MAX_SESSIONS_PER_USER) {
            sessionRepository.findByUserIdOrderByIdAsc(user.getId()).stream()
                    .findFirst()
                    .ifPresent(oldest -> {
                        sessionRepository.delete(oldest);
                        log.info("Evicted oldest session {} for user {} (cap {})",
                                oldest.getId(), user.getEmail(), MAX_SESSIONS_PER_USER);
                    });
        }

        String rawToken = jwtUtil.generateRefreshToken(user.getEmail());
        UserSession session = UserSession.builder()
                .userId(user.getId())
                .tokenHash(hashToken(rawToken))
                .userAgent(truncate(headerOrNull(httpReq, HttpHeaders.USER_AGENT), 512))
                .ipAddress(extractIp(httpReq))
                .expiresAt(Instant.now().plus(REFRESH_TOKEN_TTL))
                .build();
        sessionRepository.save(session);
        return rawToken;
    }

    private static String headerOrNull(HttpServletRequest req, String name) {
        return req == null ? null : req.getHeader(name);
    }

    private static String extractIp(HttpServletRequest req) {
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return truncate(comma > 0 ? xff.substring(0, comma).trim() : xff.trim(), 45);
        }
        return truncate(req.getRemoteAddr(), 45);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Returns the profile of the currently authenticated user.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return toProfileResponse(user);
    }

    /**
     * Changes the password of the currently authenticated user.
     * The current password must be provided for verification.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessRuleException("Le mot de passe actuel est incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordSet(true);
        userRepository.save(user);
        // V50 — logout every active device, including the one that just changed
        // its password. The frontend is expected to redirect to /login right
        // after this call so the user reconnects with fresh credentials.
        sessionRepository.deleteByUserId(user.getId());
        log.info("Password changed for user: {} — all sessions invalidated", email);
    }

    /**
     * Updates the display name of the currently authenticated user.
     * If the user is linked to an employee record, the employee's name is also synced.
     */
    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (request.nom() != null && !request.nom().isBlank()) {
            user.setNom(request.nom().trim());
            // Keep the linked employee record in sync if one exists
            if (user.getEmployeId() != null) {
                employeRepository.findById(user.getEmployeId()).ifPresent(emp -> {
                    emp.setNom(request.nom().trim());
                    employeRepository.save(emp);
                });
            }
        }

        // V49 — LinkedIn perso. null = pas de changement, "" = clear explicite.
        if (request.linkedinUrl() != null) {
            String trimmed = request.linkedinUrl().trim();
            user.setLinkedinUrl(trimmed.isEmpty() ? null : trimmed);
        }

        userRepository.save(user);
        log.info("Profile updated for user: {}", email);
        return toProfileResponse(user);
    }

    /**
     * V49 — persiste l'URL de la photo perso fraichement uploadee sur R2.
     * Cleanup best-effort de l'ancienne photo si aucun temoignage ne la snapshote.
     */
    @Transactional
    public UserProfileResponse setPhotoUrl(String email, String newPhotoUrl) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        String oldPhotoUrl = user.getPhotoUrl();
        user.setPhotoUrl(newPhotoUrl);
        userRepository.save(user);
        log.info("User photo updated user={} newUrl={}", email, newPhotoUrl);

        if (oldPhotoUrl != null && !oldPhotoUrl.isBlank() && !oldPhotoUrl.equals(newPhotoUrl)) {
            if (!testimonialRepository.existsByAuthorPhotoUrl(oldPhotoUrl)) {
                r2StorageService.deleteBlob(oldPhotoUrl);
            }
        }
        return toProfileResponse(user);
    }

    /** V49 — clear photo perso : nullify + cleanup R2. */
    @Transactional
    public UserProfileResponse clearPhotoUrl() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        String oldPhotoUrl = user.getPhotoUrl();
        if (oldPhotoUrl == null || oldPhotoUrl.isBlank()) return toProfileResponse(user);
        user.setPhotoUrl(null);
        userRepository.save(user);
        log.info("User photo cleared user={}", email);
        if (!testimonialRepository.existsByAuthorPhotoUrl(oldPhotoUrl)) {
            r2StorageService.deleteBlob(oldPhotoUrl);
        }
        return toProfileResponse(user);
    }

    private UserProfileResponse toProfileResponse(User user) {
        String orgName = null;
        String planTier = null;
        if (user.getOrganisationId() != null) {
            orgName = organisationRepository.findById(user.getOrganisationId())
                    .map(org -> org.getNom())
                    .orElse(null);
            planTier = subscriptionRepository.findByOrganisationId(user.getOrganisationId())
                    .map(s -> s.getPlanTier().name())
                    .orElse(null);
        }
        return new UserProfileResponse(
                user.getEmail(),
                user.getRole().name(),
                user.getNom(),
                user.getOrganisationId(),
                orgName,
                user.getEmployeId(),
                planTier,
                user.getPhotoUrl(),
                user.getLinkedinUrl()
        );
    }

    /**
     * Loads the ISO alpha-2 country code for the given organisation.
     * Returns null for SUPERADMIN (no organisation) or if not set.
     */
    private String resolveOrgPays(String organisationId) {
        if (organisationId == null) return null;
        return organisationRepository.findById(organisationId)
                .map(org -> org.getPays())
                .orElse(null);
    }

    /**
     * Validates an invitation token and returns the employee name and email.
     * Used by the frontend to verify the token before showing the password form.
     */
    @Transactional(readOnly = true)
    public Map<String, String> validateInvitationToken(String rawToken) {
        String hashedToken = CryptoUtil.sha256(rawToken);
        User user = userRepository.findByInvitationToken(hashedToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token invalide ou expire"));

        if (user.getInvitationTokenExpiresAt() == null || user.getInvitationTokenExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token invalide ou expire");
        }

        return Map.of(
                "name", user.getNom() != null ? user.getNom() : "",
                "email", user.getEmail()
        );
    }

    /**
     * Sets the password for a user using a valid invitation token.
     * The token is single-use and cleared after successful password set.
     */
    @Transactional
    public void setPasswordFromInvitation(SetPasswordRequest request) {
        String hashedToken = CryptoUtil.sha256(request.token());
        User user = userRepository.findByInvitationToken(hashedToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token invalide ou expire"));

        if (user.getInvitationTokenExpiresAt() == null || user.getInvitationTokenExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token invalide ou expire");
        }

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setInvitationToken(null);
        user.setInvitationTokenExpiresAt(null);
        user.setPasswordSet(true);
        userRepository.save(user);
        log.info("Password set via invitation token for user: {}", user.getEmail());
    }

    /**
     * Initiates the forgot-password flow for the given email address.
     * Generates a secure token, stores its SHA-256 hash on the user, and sends a reset
     * link by email containing the raw (unhashed) token.
     * <p>
     * If no account exists for the supplied email the method returns silently to prevent
     * user enumeration — callers must always return the same 200 response regardless.
     */
    @Transactional
    public void initiateForgotPassword(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            // Anti-enumeration: log and return silently — never reveal whether an account exists.
            log.info("Password reset requested for unknown email (silently ignored): {}", normalizedEmail);
            return;
        }

        String rawToken = CryptoUtil.generateSecureToken();
        user.setPasswordResetToken(CryptoUtil.sha256(rawToken));
        user.setPasswordResetTokenExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        userRepository.save(user);

        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getNom(), rawToken);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
        log.info("Password reset token issued for user: {}", user.getEmail());
    }

    /**
     * Validates the password-reset token and sets the new password.
     * The token is single-use and cleared on success together with the stored refresh token
     * so all existing sessions are invalidated after a password reset.
     *
     * @throws ResourceNotFoundException when no user matches the hashed token
     * @throws BusinessRuleException     when the token has expired
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hashedToken = CryptoUtil.sha256(request.token());
        User user = userRepository.findByPasswordResetToken(hashedToken)
                .orElseThrow(() -> new ResourceNotFoundException("PasswordResetToken", request.token()));

        if (user.getPasswordResetTokenExpiresAt() == null
                || Instant.now().isAfter(user.getPasswordResetTokenExpiresAt())) {
            throw new BusinessRuleException("Le lien de r\u00e9initialisation a expir\u00e9. Veuillez en demander un nouveau. / The reset link has expired. Please request a new one.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiresAt(null);
        user.setPasswordSet(true);
        userRepository.save(user);
        // V50 — invalidate ALL active sessions (every device) on password reset.
        sessionRepository.deleteByUserId(user.getId());
        log.info("Password reset successfully for user: {} — all sessions invalidated", user.getEmail());
    }

    /**
     * Returns all ADMIN users belonging to the current organisation.
     * Accessible only to authenticated ADMIN users.
     */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listAdminUsers() {
        String orgId = getCurrentOrgId();
        return userRepository.findByOrganisationIdAndRole(orgId, User.UserRole.ADMIN).stream()
                .map(u -> new AdminUserResponse(u.getId(), u.getEmail(), u.getRole().name(), u.getNom(), u.isPasswordSet(), u.getEmployeId()))
                .toList();
    }

    /**
     * Creates a new ADMIN user for the current organisation and sends them an invitation email.
     * The new user will not have a password until they follow the invitation link.
     */
    @Transactional
    public AdminUserResponse inviteAdmin(InviteAdminRequest request) {
        String orgId = getCurrentOrgId();
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un utilisateur avec cet email existe déjà");
        }
        String rawToken = CryptoUtil.generateSecureToken();
        String hashedToken = CryptoUtil.sha256(rawToken);
        User newAdmin = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                .role(User.UserRole.ADMIN)
                .nom(request.nom())
                .organisationId(orgId)
                .invitationToken(hashedToken)
                .invitationTokenExpiresAt(Instant.now().plus(Duration.ofHours(invitationExpiryHours)))
                .build();
        userRepository.save(newAdmin);
        try {
            String orgName = organisationRepository.findById(orgId).map(o -> o.getNom()).orElse("Schedy");
            emailService.sendAdminInvitationEmail(request.email(), orgName, rawToken);
        } catch (Exception e) {
            log.error("Failed to send admin invitation to {}: {}", request.email(), e.getMessage());
        }
        return new AdminUserResponse(newAdmin.getId(), newAdmin.getEmail(), newAdmin.getRole().name(), newAdmin.getNom(), newAdmin.isPasswordSet(), newAdmin.getEmployeId());
    }

    /**
     * Regenerates the invitation token for an existing ADMIN user and resends the invitation email.
     * Resets passwordSet to false so the user must go through the invitation flow again.
     */
    @Transactional
    public void resendAdminUserInvitation(Long userId) {
        String orgId = getCurrentOrgId();
        User user = userRepository.findById(userId)
                .filter(u -> orgId.equals(u.getOrganisationId()) && u.getRole() == User.UserRole.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));
        String rawToken = CryptoUtil.generateSecureToken();
        user.setInvitationToken(CryptoUtil.sha256(rawToken));
        user.setInvitationTokenExpiresAt(Instant.now().plus(Duration.ofHours(invitationExpiryHours)));
        user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        user.setPasswordSet(false);
        userRepository.save(user);
        String orgName = organisationRepository.findById(orgId).map(o -> o.getNom()).orElse("Schedy");
        try {
            emailService.sendAdminInvitationEmail(user.getEmail(), orgName, rawToken);
            log.info("Admin invitation resent to {} by {}", user.getEmail(),
                    SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (Exception e) {
            log.error("Failed to resend admin invitation email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    /**
     * Permanently deletes an ADMIN user from the current organisation.
     * An admin cannot delete their own account, and only ADMIN-role users may be deleted via this method.
     */
    @Transactional
    public void deleteAdminUser(Long userId) {
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));
        String orgId = getCurrentOrgId();
        if (!orgId.equals(user.getOrganisationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé");
        }
        if (user.getEmail().equals(currentEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas supprimer votre propre compte");
        }
        if (user.getRole() != User.UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cet utilisateur n'est pas un administrateur");
        }
        userRepository.delete(user);
        log.info("Admin user {} deleted by {}", user.getEmail(), currentEmail);
    }

    /**
     * Resolves the organisationId of the currently authenticated user.
     * Throws 401 if the user is not found and 403 if they have no organisation.
     */
    private String getCurrentOrgId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (currentUser.getOrganisationId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Aucune organisation associée");
        }
        return currentUser.getOrganisationId();
    }

    /**
     * Generates a 6-digit code, stores its hash with expiry, and sends it by email.
     */
    private void sendEmail2faCode(User user) {
        String code = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        user.setEmail2faCodeHash(CryptoUtil.sha256(code));
        user.setEmail2faCodeExpiresAt(Instant.now().plusSeconds(email2faCodeExpirySeconds));
        userRepository.save(user);
        try {
            emailService.send2faCodeEmail(user.getEmail(), user.getNom(), code, email2faCodeExpirySeconds);
        } catch (Exception e) {
            log.error("Failed to send 2FA email code to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    /**
     * Verifies the 6-digit code sent by email during login.
     */
    @Transactional
    public boolean verifyEmail2faCode(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getEmail2faCodeHash() == null || user.getEmail2faCodeExpiresAt() == null) return false;
        if (Instant.now().isAfter(user.getEmail2faCodeExpiresAt())) return false;
        boolean valid = CryptoUtil.sha256(code).equals(user.getEmail2faCodeHash());
        if (valid) {
            user.setEmail2faCodeHash(null);
            user.setEmail2faCodeExpiresAt(null);
            userRepository.save(user);
        }
        return valid;
    }

    /**
     * Clears any outstanding email 2FA code for the given user.
     * Called after a successful TOTP verification so the email code
     * cannot be reused as an alternate authentication path (S-06).
     */
    @Transactional
    public void clearEmail2faCode(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null && user.getEmail2faCodeHash() != null) {
            user.setEmail2faCodeHash(null);
            user.setEmail2faCodeExpiresAt(null);
            userRepository.save(user);
        }
    }

    /**
     * Resends the email 2FA code (generates a new one).
     */
    @Transactional
    public void resendEmail2faCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        sendEmail2faCode(user);
    }

    // B-16: Delegate to CryptoUtil to avoid duplicating SHA-256 logic with PointageCodeService
    private String hashToken(String token) {
        return CryptoUtil.sha256(token);
    }
}
