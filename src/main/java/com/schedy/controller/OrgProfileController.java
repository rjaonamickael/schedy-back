package com.schedy.controller;

import com.schedy.config.TenantContext;
import com.schedy.dto.request.UpdateOrgProfileRequest;
import com.schedy.dto.response.OrgProfileResponse;
import com.schedy.service.OrgProfileService;
import com.schedy.service.R2StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Admin self-service: view and update the admin's own organisation. Sits
 * under {@code /api/v1/organisation/me} (singular) to mirror the existing
 * {@code /api/v1/user/profile} "me" pattern. The legacy plural
 * {@code /api/v1/organisations/{id}} controller stays as-is for the
 * superadmin path-param-based flow.
 *
 * <p>V48 : ajout endpoint {@code POST /logo} (multipart SVG). Le logo est
 * uploade sur R2 puis son URL est persistee dans {@code Organisation.logoUrl}
 * — source de verite pour le snapshot au submit temoignage.
 */
@RestController
@RequestMapping("/api/v1/organisation/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OrgProfileController {

    private final OrgProfileService orgProfileService;
    private final R2StorageService r2StorageService;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<OrgProfileResponse> getMe() {
        return ResponseEntity.ok(orgProfileService.getCurrentOrgProfile());
    }

    @PatchMapping
    public ResponseEntity<OrgProfileResponse> updateMe(@Valid @RequestBody UpdateOrgProfileRequest request) {
        return ResponseEntity.ok(orgProfileService.updateCurrentOrgProfile(request));
    }

    /**
     * V48 — POST /api/v1/organisation/me/logo (multipart/form-data)
     * Upload du logo entreprise (SVG sanitise). L'URL publique R2 est
     * immediatement persistee dans {@code Organisation.logoUrl} et
     * l'ancienne URL (si presente et non-referencee par un temoignage)
     * est supprimee best-effort.
     */
    @PostMapping(value = "/logo", consumes = "multipart/form-data")
    public CompletableFuture<ResponseEntity<Map<String, String>>> uploadLogo(
            @RequestParam("file") MultipartFile file) {
        // Capture l'orgId sur le thread HTTP avant de passer dans le pool AWS.
        // TenantContext est @RequestScope (proxy Spring) : il sera inaccessible
        // sur le thread du CompletableFuture, causant une AccessDeniedException mappee en 422.
        String orgId = tenantContext.requireOrganisationId();
        return r2StorageService.uploadOrgLogoAsync(file)
                .thenApply(publicUrl -> {
                    orgProfileService.setLogoUrl(orgId, publicUrl);
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", publicUrl));
                });
    }

    /**
     * V48 — DELETE /api/v1/organisation/me/logo
     * Clear le logo (nullify) + cleanup R2 best-effort.
     */
    @DeleteMapping("/logo")
    public ResponseEntity<Void> deleteLogo() {
        orgProfileService.clearLogo();
        return ResponseEntity.noContent().build();
    }
}
