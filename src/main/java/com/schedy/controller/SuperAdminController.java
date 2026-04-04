package com.schedy.controller;

import com.schedy.dto.request.*;
import com.schedy.dto.response.AnnouncementResponse;
import com.schedy.dto.response.FeatureFlagResponse;
import com.schedy.dto.response.ImpersonateResponse;
import com.schedy.dto.response.ImpersonationLogResponse;
import com.schedy.dto.response.OrgSummaryResponse;
import com.schedy.dto.response.PlanTemplateResponse;
import com.schedy.dto.response.PromoCodeResponse;
import com.schedy.dto.response.RegistrationRequestResponse;
import com.schedy.dto.response.SubscriptionResponse;
import com.schedy.dto.response.SuperAdminDashboardResponse;
import com.schedy.service.RegistrationRequestService;
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

    private final SuperAdminService             superAdminService;
    private final TotpService                   totpService;
    private final RegistrationRequestService    registrationRequestService;

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

    @PutMapping("/organisations/{id}/pays")
    public ResponseEntity<OrgSummaryResponse> updateOrgPays(
            @PathVariable String id,
            @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(superAdminService.updateOrgPays(id, body.get("pays")));
    }

    // =========================================================================
    // SUBSCRIPTIONS
    // =========================================================================

    @GetMapping("/organisations/{id}/subscription")
    public ResponseEntity<SubscriptionResponse> getSubscription(@PathVariable String id) {
        return ResponseEntity.ok(superAdminService.getSubscription(id));
    }

    @PutMapping("/organisations/{id}/subscription")
    public ResponseEntity<SubscriptionResponse> updateSubscription(
            @PathVariable String id,
            @RequestBody SubscriptionDto dto) {
        return ResponseEntity.ok(superAdminService.updateSubscription(id, dto));
    }

    @PostMapping("/organisations/{id}/apply-promo")
    public ResponseEntity<SubscriptionResponse> applyPromoCode(
            @PathVariable String id,
            @Valid @RequestBody ApplyPromoRequest request) {
        return ResponseEntity.ok(superAdminService.applyPromoCode(id, request.promoCode()));
    }

    // =========================================================================
    // PROMO CODES
    // =========================================================================

    @GetMapping("/promo-codes")
    public ResponseEntity<List<PromoCodeResponse>> findAllPromoCodes() {
        return ResponseEntity.ok(superAdminService.findAllPromoCodes());
    }

    @PostMapping("/promo-codes")
    public ResponseEntity<PromoCodeResponse> createPromoCode(@Valid @RequestBody PromoCodeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(superAdminService.createPromoCode(dto));
    }

    @PutMapping("/promo-codes/{id}")
    public ResponseEntity<PromoCodeResponse> updatePromoCode(
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
    public ResponseEntity<List<FeatureFlagResponse>> getFeatureFlags(@PathVariable String id) {
        return ResponseEntity.ok(superAdminService.getFeatureFlags(id));
    }

    @PutMapping("/organisations/{id}/features")
    public ResponseEntity<List<FeatureFlagResponse>> updateFeatureFlags(
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

        String totpCode = request != null ? request.totpCode() : null;
        if (totpCode == null || totpCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le code 2FA est obligatoire pour effectuer une impersonation.");
        }
        TotpService.TwoFaStatusResponse status = totpService.getStatus();
        if (!status.enabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La double authentification doit être activée pour effectuer une impersonation.");
        }
        String superadminEmail = authentication.getName();
        if (!totpService.verify(superadminEmail, totpCode)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Code 2FA invalide ou expiré.");
        }

        String reason = request.reason();
        String ipAddress = extractClientIp(httpRequest);

        return ResponseEntity.ok(
                superAdminService.generateImpersonationToken(orgId, superadminEmail, reason, ipAddress));
    }

    @GetMapping("/impersonation-log")
    public ResponseEntity<Page<ImpersonationLogResponse>> getImpersonationLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(superAdminService.getImpersonationLog(page, size));
    }

    // =========================================================================
    // ANNOUNCEMENTS
    // =========================================================================

    @GetMapping("/announcements")
    public ResponseEntity<List<AnnouncementResponse>> getAnnouncements() {
        return ResponseEntity.ok(superAdminService.getAnnouncements());
    }

    @PostMapping("/announcements")
    public ResponseEntity<AnnouncementResponse> createAnnouncement(
            @Valid @RequestBody AnnouncementDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(superAdminService.createAnnouncement(dto));
    }

    @PutMapping("/announcements/{id}")
    public ResponseEntity<AnnouncementResponse> updateAnnouncement(
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
    // REGISTRATION REQUESTS
    // =========================================================================

    /**
     * GET /api/v1/superadmin/registration-requests?status=PENDING
     * List all registration requests, optionally filtered by status.
     */
    @GetMapping("/registration-requests")
    public ResponseEntity<List<RegistrationRequestResponse>> findAllRegistrationRequests(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(registrationRequestService.findAll(status));
    }

    /**
     * GET /api/v1/superadmin/registration-requests/{id}
     * Get details of a single registration request.
     */
    @GetMapping("/registration-requests/{id}")
    public ResponseEntity<RegistrationRequestResponse> findRegistrationRequest(
            @PathVariable String id) {
        return ResponseEntity.ok(registrationRequestService.findById(id));
    }

    /**
     * POST /api/v1/superadmin/registration-requests/{id}/approve
     * Approve the request: create org + send invitation email.
     */
    @PostMapping("/registration-requests/{id}/approve")
    public ResponseEntity<RegistrationRequestResponse> approveRegistrationRequest(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String superadminEmail = authentication.getName();
        String planTier = (body != null && body.containsKey("planTier")) ? body.get("planTier") : null;
        String adminEmail = (body != null && body.containsKey("adminEmail")) ? body.get("adminEmail") : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationRequestService.approve(id, superadminEmail, planTier, adminEmail));
    }

    /**
     * POST /api/v1/superadmin/registration-requests/{id}/reject
     * Reject the request with a mandatory reason + send rejection email.
     */
    @PostMapping("/registration-requests/{id}/reject")
    public ResponseEntity<RegistrationRequestResponse> rejectRegistrationRequest(
            @PathVariable String id,
            @Valid @RequestBody RejectRegistrationRequest request,
            Authentication authentication) {
        String superadminEmail = authentication.getName();
        return ResponseEntity.ok(
                registrationRequestService.reject(id, request.reason(), superadminEmail));
    }

    // =========================================================================
    // PLAN TEMPLATES
    // =========================================================================

    @GetMapping("/plan-templates")
    public ResponseEntity<List<PlanTemplateResponse>> findAllPlanTemplates() {
        return ResponseEntity.ok(superAdminService.findAllPlanTemplates());
    }

    @GetMapping("/plan-templates/{id}")
    public ResponseEntity<PlanTemplateResponse> findPlanTemplate(@PathVariable String id) {
        return ResponseEntity.ok(superAdminService.findPlanTemplate(id));
    }

    @PostMapping("/plan-templates")
    public ResponseEntity<PlanTemplateResponse> createPlanTemplate(
            @Valid @RequestBody PlanTemplateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(superAdminService.createPlanTemplate(dto));
    }

    @PutMapping("/plan-templates/{id}")
    public ResponseEntity<PlanTemplateResponse> updatePlanTemplate(
            @PathVariable String id,
            @Valid @RequestBody PlanTemplateDto dto) {
        return ResponseEntity.ok(superAdminService.updatePlanTemplate(id, dto));
    }

    /**
     * DELETE /api/v1/superadmin/plan-templates/{id}
     * Returns 409 Conflict when any organisation is still subscribed to this plan.
     */
    @DeleteMapping("/plan-templates/{id}")
    public ResponseEntity<Void> deletePlanTemplate(@PathVariable String id) {
        superAdminService.deletePlanTemplate(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String extractClientIp(HttpServletRequest request) {
        return IpUtils.resolveClientIp(request, getTrustedProxies());
    }
}
