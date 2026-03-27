package com.schedy.controller;

import com.schedy.dto.request.*;
import com.schedy.dto.response.ImpersonateResponse;
import com.schedy.dto.response.OrgSummaryResponse;
import com.schedy.dto.response.SuperAdminDashboardResponse;
import com.schedy.entity.*;
import com.schedy.service.SuperAdminService;
import com.schedy.service.TotpService;
import com.schedy.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/superadmin")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final TotpService totpService;

    @org.springframework.beans.factory.annotation.Value("${schedy.rate-limit.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}")
    private List<String> trustedProxiesList;

    private Set<String> getTrustedProxies() {
        return new HashSet<>(trustedProxiesList.stream().map(String::trim).toList());
    }

    // =========================================================================
    // DASHBOARD
    // =========================================================================

    @GetMapping("/dashboard")
    public ResponseEntity<SuperAdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(superAdminService.getDashboard());
    }

    // =========================================================================
    // ORGANISATIONS
    // =========================================================================

    @GetMapping("/organisations")
    public ResponseEntity<List<OrgSummaryResponse>> findAllOrganisations() {
        return ResponseEntity.ok(superAdminService.findAllOrganisations());
    }

    @GetMapping("/organisations/{id}")
    public ResponseEntity<OrgSummaryResponse> findOrganisation(@PathVariable String id) {
        return ResponseEntity.ok(superAdminService.findOrganisation(id));
    }

    @PostMapping("/organisations")
    public ResponseEntity<OrgSummaryResponse> createOrganisation(
            @Valid @RequestBody CreateOrgRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(superAdminService.createOrganisation(request));
    }

    @PostMapping("/organisations/{id}/resend-admin-invitation")
    public ResponseEntity<Void> resendAdminInvitation(@PathVariable String id) {
        superAdminService.resendAdminInvitation(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/organisations/{id}")
    public ResponseEntity<Void> deleteOrganisation(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String totpCode = body != null ? body.get("totpCode") : null;
        if (totpCode == null || totpCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le code 2FA est obligatoire pour supprimer une organisation.");
        }
        // Verify 2FA is enabled and code is valid
        TotpService.TwoFaStatusResponse status = totpService.getStatus();
        if (!status.enabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La double authentification doit être activée pour supprimer une organisation.");
        }
        String email = authentication.getName();
        if (!totpService.verify(email, totpCode)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Code 2FA invalide ou expiré.");
        }
        superAdminService.deleteOrganisation(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/organisations/{id}/status")
    public ResponseEntity<OrgSummaryResponse> updateOrgStatus(
            @PathVariable String id,
            @Valid @RequestBody OrgStatusRequest request) {
        return ResponseEntity.ok(superAdminService.updateOrgStatus(id, request.status()));
    }

    // =========================================================================
    // SUBSCRIPTIONS
    // =========================================================================

    @GetMapping("/organisations/{id}/subscription")
    public ResponseEntity<Subscription> getSubscription(@PathVariable String id) {
        return ResponseEntity.ok(superAdminService.getSubscription(id));
    }

    @PutMapping("/organisations/{id}/subscription")
    public ResponseEntity<Subscription> updateSubscription(
            @PathVariable String id,
            @RequestBody SubscriptionDto dto) {
        return ResponseEntity.ok(superAdminService.updateSubscription(id, dto));
    }

    @PostMapping("/organisations/{id}/apply-promo")
    public ResponseEntity<Subscription> applyPromoCode(
            @PathVariable String id,
            @Valid @RequestBody ApplyPromoRequest request) {
        return ResponseEntity.ok(superAdminService.applyPromoCode(id, request.promoCode()));
    }

    // =========================================================================
    // PROMO CODES
    // =========================================================================

    @GetMapping("/promo-codes")
    public ResponseEntity<List<PromoCode>> findAllPromoCodes() {
        return ResponseEntity.ok(superAdminService.findAllPromoCodes());
    }

    @PostMapping("/promo-codes")
    public ResponseEntity<PromoCode> createPromoCode(@Valid @RequestBody PromoCodeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(superAdminService.createPromoCode(dto));
    }

    @PutMapping("/promo-codes/{id}")
    public ResponseEntity<PromoCode> updatePromoCode(
            @PathVariable String id,
            @Valid @RequestBody PromoCodeDto dto) {
        return ResponseEntity.ok(superAdminService.updatePromoCode(id, dto));
    }

    @DeleteMapping("/promo-codes/{id}")
    public ResponseEntity<Void> deletePromoCode(@PathVariable String id) {
        superAdminService.deletePromoCode(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // FEATURE FLAGS
    // =========================================================================

    @GetMapping("/organisations/{id}/features")
    public ResponseEntity<List<FeatureFlag>> getFeatureFlags(@PathVariable String id) {
        return ResponseEntity.ok(superAdminService.getFeatureFlags(id));
    }

    @PutMapping("/organisations/{id}/features")
    public ResponseEntity<List<FeatureFlag>> updateFeatureFlags(
            @PathVariable String id,
            @RequestBody List<FeatureFlagDto> flags) {
        return ResponseEntity.ok(superAdminService.updateFeatureFlags(id, flags));
    }

    // =========================================================================
    // IMPERSONATION
    // =========================================================================

    @PostMapping("/impersonate/{orgId}")
    public ResponseEntity<ImpersonateResponse> impersonate(
            @PathVariable String orgId,
            @RequestBody(required = false) ImpersonateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String superadminEmail = authentication.getName();
        String reason = request != null ? request.reason() : null;
        String ipAddress = extractClientIp(httpRequest);

        return ResponseEntity.ok(
                superAdminService.generateImpersonationToken(orgId, superadminEmail, reason, ipAddress));
    }

    @GetMapping("/impersonation-log")
    public ResponseEntity<Page<ImpersonationLog>> getImpersonationLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(superAdminService.getImpersonationLog(page, size));
    }

    // =========================================================================
    // ANNOUNCEMENTS
    // =========================================================================

    @GetMapping("/announcements")
    public ResponseEntity<List<PlatformAnnouncement>> getAnnouncements() {
        return ResponseEntity.ok(superAdminService.getAnnouncements());
    }

    @PostMapping("/announcements")
    public ResponseEntity<PlatformAnnouncement> createAnnouncement(
            @Valid @RequestBody AnnouncementDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(superAdminService.createAnnouncement(dto));
    }

    @PutMapping("/announcements/{id}")
    public ResponseEntity<PlatformAnnouncement> updateAnnouncement(
            @PathVariable String id,
            @Valid @RequestBody AnnouncementDto dto) {
        return ResponseEntity.ok(superAdminService.updateAnnouncement(id, dto));
    }

    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<Void> deleteAnnouncement(@PathVariable String id) {
        superAdminService.deleteAnnouncement(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String extractClientIp(HttpServletRequest request) {
        return IpUtils.resolveClientIp(request, getTrustedProxies());
    }
}
