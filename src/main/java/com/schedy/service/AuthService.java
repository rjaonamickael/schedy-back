package com.schedy.service;

import com.schedy.config.JwtUtil;
import com.schedy.dto.request.AuthRequest;
import com.schedy.dto.request.ChangePasswordRequest;
import com.schedy.dto.request.InviteAdminRequest;
import com.schedy.dto.request.RefreshRequest;
import com.schedy.dto.request.RegisterRequest;
import com.schedy.dto.request.SetPasswordRequest;
import com.schedy.dto.request.UpdateProfileRequest;
import com.schedy.dto.response.AdminUserResponse;
import com.schedy.dto.response.AuthResponse;
import com.schedy.dto.response.UserProfileResponse;
import com.schedy.entity.User;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.EmployeRepository;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.UserRepository;
import com.schedy.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmployeRepository employeRepository;
    private final OrganisationRepository organisationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @Value("${schedy.invitation.expiry-hours:24}")
    private int invitationExpiryHours;

    // B-H16: Per-account lockout after repeated failed login attempts.
    // 5 failures within 15 minutes = account locked for 15 minutes.
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L; // 15 minutes
    // B-05: LIMITATION — This lockout map is in-memory only. It does NOT persist across restarts
    // and does NOT work in multi-instance deployments (horizontal scaling). Each JVM instance tracks
    // its own failed attempts independently, so an attacker can bypass lockout by cycling between
    // instances. A proper fix requires a distributed store (e.g., Redis with SETNX + TTL).
    // Accepted as-is for beta (single-instance). Must be replaced before scaling to multiple pods.
    private final Map<String, FailedLoginTracker> failedLogins = new ConcurrentHashMap<>();

    private record FailedLoginTracker(int attempts, Instant firstAttempt, Instant lockedUntil) {
        boolean isLocked() {
            return lockedUntil != null && Instant.now().isBefore(lockedUntil);
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
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

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getOrganisationId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        user.setRefreshToken(hashToken(refreshToken));

        userRepository.save(user);
        log.info("New user registered: {} with role {}", request.email(), role);

        return new AuthResponse(accessToken, refreshToken, user.getEmail(), user.getRole().name(), user.getEmployeId(), user.getOrganisationId(), resolveOrgPays(user.getOrganisationId()));
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        String email = request.email().toLowerCase().trim();

        // B-H16: Check per-account lockout before attempting authentication
        FailedLoginTracker tracker = failedLogins.get(email);
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

        // Successful login: clear failed attempts
        failedLogins.remove(email);

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getOrganisationId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        user.setRefreshToken(hashToken(refreshToken));
        userRepository.save(user);
        log.info("User logged in: {}", user.getEmail());

        return new AuthResponse(accessToken, refreshToken, user.getEmail(), user.getRole().name(), user.getEmployeId(), user.getOrganisationId(), resolveOrgPays(user.getOrganisationId()));
    }

    private void recordFailedAttempt(String email) {
        failedLogins.compute(email, (key, existing) -> {
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

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();

        if (!jwtUtil.isTokenValid(token) || !jwtUtil.isRefreshToken(token)) {
            throw new IllegalArgumentException("Refresh token invalide");
        }

        String email = jwtUtil.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));

        if (!hashToken(token).equals(user.getRefreshToken())) {
            throw new IllegalArgumentException("Refresh token ne correspond pas");
        }

        String newAccessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getOrganisationId());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        user.setRefreshToken(hashToken(newRefreshToken));
        userRepository.save(user);
        log.debug("Token refreshed for: {}", email);

        return new AuthResponse(newAccessToken, newRefreshToken, user.getEmail(), user.getRole().name(), user.getEmployeId(), user.getOrganisationId(), resolveOrgPays(user.getOrganisationId()));
    }

    @Transactional
    public void logout(String refreshToken) {
        String hashedToken = hashToken(refreshToken);
        userRepository.findByRefreshToken(hashedToken).ifPresent(user -> {
            user.setRefreshToken(null);
            userRepository.save(user);
            log.info("User logged out: {}", user.getEmail());
        });
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
        log.info("Password changed for user: {}", email);
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

        userRepository.save(user);
        log.info("Profile updated for user: {}", email);
        return toProfileResponse(user);
    }

    private UserProfileResponse toProfileResponse(User user) {
        String orgName = null;
        if (user.getOrganisationId() != null) {
            orgName = organisationRepository.findById(user.getOrganisationId())
                    .map(org -> org.getNom())
                    .orElse(null);
        }
        return new UserProfileResponse(
                user.getEmail(),
                user.getRole().name(),
                user.getNom(),
                user.getOrganisationId(),
                orgName,
                user.getEmployeId()
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
     * Returns all ADMIN users belonging to the current organisation.
     * Accessible only to authenticated ADMIN users.
     */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listAdminUsers() {
        String orgId = getCurrentOrgId();
        return userRepository.findAllByOrganisationId(orgId).stream()
                .filter(u -> u.getRole() == User.UserRole.ADMIN)
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
        String rawToken = generateSecureToken();
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
            boolean isFrench = resolveIsFrench(orgId);
            String orgName = organisationRepository.findById(orgId).map(o -> o.getNom()).orElse("Schedy");
            emailService.sendAdminInvitationEmail(request.email(), orgName, rawToken, isFrench);
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
        String rawToken = generateSecureToken();
        user.setInvitationToken(CryptoUtil.sha256(rawToken));
        user.setInvitationTokenExpiresAt(Instant.now().plus(Duration.ofHours(invitationExpiryHours)));
        user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        user.setPasswordSet(false);
        userRepository.save(user);
        String orgName = organisationRepository.findById(orgId).map(o -> o.getNom()).orElse("Schedy");
        boolean isFrench = resolveIsFrench(orgId);
        emailService.sendAdminInvitationEmail(user.getEmail(), orgName, rawToken, isFrench);
        log.info("Admin invitation resent to {} by {}", user.getEmail(),
                SecurityContextHolder.getContext().getAuthentication().getName());
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
     * Returns true if the organisation's country code corresponds to a French-speaking country.
     * Falls back to false when the organisation is not found or pays is null.
     */
    private boolean resolveIsFrench(String orgId) {
        return organisationRepository.findById(orgId)
                .map(org -> {
                    String pays = org.getPays();
                    if (pays == null) return false;
                    String p = pays.toUpperCase();
                    return p.startsWith("FR") || "MDG".equals(p) || "MG".equals(p)
                            || "BE".equals(p) || "CH".equals(p) || "CA".equals(p) || "CAN".equals(p);
                })
                .orElse(false);
    }

    /**
     * Generates a cryptographically secure random 64-character hex token.
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // B-16: Delegate to CryptoUtil to avoid duplicating SHA-256 logic with PointageCodeService
    private String hashToken(String token) {
        return CryptoUtil.sha256(token);
    }
}
