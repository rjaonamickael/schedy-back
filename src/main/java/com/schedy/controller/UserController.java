package com.schedy.controller;

import com.schedy.dto.request.ChangePasswordRequest;
import com.schedy.dto.request.UpdateProfileRequest;
import com.schedy.dto.response.UserProfileResponse;
import com.schedy.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final AuthService authService;

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
}
