package com.schedy.controller;

import com.schedy.dto.request.ChangePasswordRequest;
import com.schedy.dto.request.InviteAdminRequest;
import com.schedy.dto.request.UpdateProfileRequest;
import com.schedy.dto.response.AdminUserResponse;
import com.schedy.dto.response.UserProfileResponse;
import com.schedy.dto.response.UserSessionResponse;
import com.schedy.service.AuthService;
import com.schedy.service.R2StorageService;
import com.schedy.service.TotpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final AuthService authService;
    private final TotpService totpService;
    private final R2StorageService r2StorageService;

    /**
     * GET /api/v1/user/profile
     * Returns the authenticated user's profile (email, role, nom, organisationId, employeId).
     * Available to all roles: EMPLOYEE, MANAGER, ADMIN, SUPERADMIN.
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile() {
        return ResponseEntity.ok(authService.getCurrentProfile());
    }

    /**
     * PUT /api/v1/user/profile
     * Updates the authenticated user's display name.
     * If the user is linked to an employee record, the employee name is also updated.
     * Available to all roles.
     */
    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(request));
    }

    /**
     * POST /api/v1/user/change-password
     * Changes the authenticated user's password.
     * The current password must be supplied for verification.
     * Available to all roles.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * V49 — POST /api/v1/user/profile/photo (multipart/form-data)
     * Upload d'une photo personnelle (JPG/PNG/WEBP, 2 Mo max). L'URL R2
     * est persistee dans {@code User.photoUrl} — source de verite pour le
     * snapshot {@code Testimonial.authorPhotoUrl} au submit.
     */
    @PostMapping(value = "/profile/photo", consumes = "multipart/form-data")
    public CompletableFuture<ResponseEntity<Map<String, String>>> uploadPhoto(
            @RequestParam("file") MultipartFile file) {
        // Capture l'email sur le thread HTTP avant de passer dans le pool AWS.
        // SecurityContextHolder est ThreadLocal : il sera null sur le thread du CompletableFuture.
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return r2StorageService.uploadUserPhotoAsync(file)
                .thenApply(publicUrl -> {
                    authService.setPhotoUrl(email, publicUrl);
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", publicUrl));
                });
    }

    /**
     * V49 — DELETE /api/v1/user/profile/photo
     * Clear la photo (nullify) + cleanup R2 best-effort.
     */
    @DeleteMapping("/profile/photo")
    public ResponseEntity<UserProfileResponse> deletePhoto() {
        return ResponseEntity.ok(authService.clearPhotoUrl());
    }

    /**
     * S18-BE-04 — GET /api/v1/user/sessions
     * Returns all active refresh-token sessions for the authenticated user,
     * most-recently-used first. The row whose token hash matches the presented
     * {@code refreshToken} cookie is flagged {@code isCurrent=true} so the UI
     * can disable self-revoke on the current device.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<UserSessionResponse>> listSessions(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {
        return ResponseEntity.ok(authService.listCurrentUserSessions(refreshToken));
    }

    /**
     * S18-BE-04 — DELETE /api/v1/user/sessions/{id}
     * Revokes a single session. 404 if the session id does not exist, 403 if
     * it belongs to another user.
     */
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable Long id) {
        authService.revokeSession(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * S18-BE-04 — DELETE /api/v1/user/sessions
     * Revokes every session EXCEPT the current one (identified by the
     * {@code refreshToken} cookie). Returns the count of revoked sessions.
     */
    @DeleteMapping("/sessions")
    public ResponseEntity<Map<String, Integer>> revokeAllOtherSessions(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {
        int revoked = authService.revokeAllOtherSessions(refreshToken);
        return ResponseEntity.ok(Map.of("revoked", revoked));
    }

    /**
     * GET /api/v1/user/admin/users
     * Lists all admin users in the current organisation.
     * Restricted to ADMIN role.
     */
    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminUserResponse>> listAdminUsers() {
        return ResponseEntity.ok(authService.listAdminUsers());
    }

    /**
     * POST /api/v1/user/admin/users/invite
     * Invites a new admin user to the current organisation.
     * Restricted to ADMIN role.
     */
    @PostMapping("/admin/users/invite")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserResponse> inviteAdmin(@Valid @RequestBody InviteAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.inviteAdmin(request));
    }

    /**
     * POST /api/v1/user/admin/users/{id}/resend-invitation
     * Resends the invitation email to an admin user who has not yet set their password.
     * Restricted to ADMIN role.
     */
    @PostMapping("/admin/users/{id}/resend-invitation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resendAdminInvitation(@PathVariable Long id) {
        authService.resendAdminUserInvitation(id);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /api/v1/user/admin/users/{id}
     * Deletes an admin user from the current organisation.
     * An admin cannot delete their own account.
     * Restricted to ADMIN role.
     */
    @DeleteMapping("/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAdminUser(@PathVariable Long id) {
        authService.deleteAdminUser(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────
    // 2FA setup / management (requires full authentication)
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/2fa/status")
    public ResponseEntity<TotpService.TwoFaStatusResponse> get2faStatus() {
        return ResponseEntity.ok(totpService.getStatus());
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<TotpService.SetupResponse> setup2fa() {
        return ResponseEntity.ok(totpService.setup());
    }

    @PostMapping("/2fa/setup/confirm")
    public ResponseEntity<Map<String, List<String>>> confirm2faSetup(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        return ResponseEntity.ok(Map.of("recoveryCodes", totpService.confirmSetup(code)));
    }

    @DeleteMapping("/2fa")
    public ResponseEntity<Void> disable2fa(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        totpService.disable(code);
        return ResponseEntity.noContent().build();
    }
}
