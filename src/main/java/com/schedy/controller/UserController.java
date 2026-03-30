package com.schedy.controller;

import com.schedy.dto.request.ChangePasswordRequest;
import com.schedy.dto.request.InviteAdminRequest;
import com.schedy.dto.request.UpdateProfileRequest;
import com.schedy.dto.response.AdminUserResponse;
import com.schedy.dto.response.UserProfileResponse;
import com.schedy.service.AuthService;
import com.schedy.service.TotpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final AuthService authService;
    private final TotpService totpService;

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
