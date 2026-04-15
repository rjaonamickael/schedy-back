package com.schedy.controller;

import com.schedy.dto.request.UpdateOrgProfileRequest;
import com.schedy.dto.response.OrgProfileResponse;
import com.schedy.service.OrgProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin self-service: view and update the admin's own organisation. Sits
 * under {@code /api/v1/organisation/me} (singular) to mirror the existing
 * {@code /api/v1/user/profile} "me" pattern. The legacy plural
 * {@code /api/v1/organisations/{id}} controller stays as-is for the
 * superadmin path-param-based flow.
 */
@RestController
@RequestMapping("/api/v1/organisation/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OrgProfileController {

    private final OrgProfileService orgProfileService;

    @GetMapping
    public ResponseEntity<OrgProfileResponse> getMe() {
        return ResponseEntity.ok(orgProfileService.getCurrentOrgProfile());
    }

    @PatchMapping
    public ResponseEntity<OrgProfileResponse> updateMe(@Valid @RequestBody UpdateOrgProfileRequest request) {
        return ResponseEntity.ok(orgProfileService.updateCurrentOrgProfile(request));
    }
}
