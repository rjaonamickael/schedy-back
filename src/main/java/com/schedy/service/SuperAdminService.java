package com.schedy.service;

import com.schedy.dto.request.*;
import com.schedy.dto.response.*;
import com.schedy.entity.PromoCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Platform-level administration facade.
 * Delegates to domain-specific services for clean separation of concerns.
 * This class intentionally has NO @Transactional — each delegate manages its own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private final OrgAdminService        orgAdminService;
    private final CommercialAdminService commercialAdminService;
    private final PlatformAdminService   platformAdminService;

    // ── Dashboard ───────────────────────────────────────────────────────────
    public SuperAdminDashboardResponse getDashboard() {
        return orgAdminService.getDashboard();
    }

    // ── Organisations ───────────────────────────────────────────────────────
    public List<OrgSummaryResponse> findAllOrganisations() {
        return orgAdminService.findAllOrganisations();
    }

    public OrgSummaryResponse findOrganisation(String id) {
        return orgAdminService.findOrganisation(id);
    }

    public OrgSummaryResponse createOrganisation(CreateOrgRequest request) {
        return orgAdminService.createOrganisation(request);
    }

    public void deleteOrganisation(String id) {
        orgAdminService.deleteOrganisation(id);
    }

    public OrgSummaryResponse updateOrgStatus(String id, String status) {
        return orgAdminService.updateOrgStatus(id, status);
    }

    public OrgSummaryResponse updateOrgPays(String id, String pays) {
        return orgAdminService.updateOrgPays(id, pays);
    }

    public void resendAdminInvitation(String orgId) {
        orgAdminService.resendAdminInvitation(orgId);
    }

    // ── Identifications ─────────────────────────────────────────────────────
    public OrgIdentificationsResponse getOrgIdentifications(String orgId) {
        return orgAdminService.getOrgIdentifications(orgId);
    }

    public OrgIdentificationsResponse updateOrgIdentifications(String orgId, UpdateOrgIdentificationsRequest request) {
        return orgAdminService.updateOrgIdentifications(orgId, request);
    }

    public OrgIdentificationsResponse updateOrgVerificationStatus(
            String orgId, String status, String note, String superadminEmail) {
        return orgAdminService.updateOrgVerificationStatus(orgId, status, note, superadminEmail);
    }

    // ── Subscriptions ───────────────────────────────────────────────────────
    public SubscriptionResponse getSubscription(String orgId) {
        return commercialAdminService.getSubscription(orgId);
    }

    public SubscriptionResponse updateSubscription(String orgId, SubscriptionDto dto) {
        return commercialAdminService.updateSubscription(orgId, dto);
    }

    public SubscriptionResponse applyPromoCode(String orgId, String promoCode) {
        return commercialAdminService.applyPromoCode(orgId, promoCode);
    }

    // ── Promo Codes ─────────────────────────────────────────────────────────
    public List<PromoCodeResponse> findAllPromoCodes() {
        return commercialAdminService.findAllPromoCodes();
    }

    public PromoCodeResponse createPromoCode(PromoCodeDto dto) {
        return commercialAdminService.createPromoCode(dto);
    }

    public PromoCodeResponse updatePromoCode(String id, PromoCodeDto dto) {
        return commercialAdminService.updatePromoCode(id, dto);
    }

    public void deletePromoCode(String id) {
        commercialAdminService.deletePromoCode(id);
    }

    public PromoCode validatePromoCode(String code) {
        return commercialAdminService.validatePromoCode(code);
    }

    // ── Feature Flags ───────────────────────────────────────────────────────
    public List<FeatureFlagResponse> getFeatureFlags(String orgId) {
        return platformAdminService.getFeatureFlags(orgId);
    }

    public List<FeatureFlagResponse> updateFeatureFlags(String orgId, List<FeatureFlagDto> dtos) {
        return platformAdminService.updateFeatureFlags(orgId, dtos);
    }

    // ── Announcements ───────────────────────────────────────────────────────
    public List<AnnouncementResponse> getAnnouncements() {
        return platformAdminService.getAnnouncements();
    }

    public AnnouncementResponse createAnnouncement(AnnouncementDto dto) {
        return platformAdminService.createAnnouncement(dto);
    }

    public AnnouncementResponse updateAnnouncement(String id, AnnouncementDto dto) {
        return platformAdminService.updateAnnouncement(id, dto);
    }

    public void deleteAnnouncement(String id) {
        platformAdminService.deleteAnnouncement(id);
    }

    // ── Impersonation ───────────────────────────────────────────────────────
    public ImpersonateResponse generateImpersonationToken(
            String orgId, String superadminEmail, String reason, String ipAddress) {
        return platformAdminService.generateImpersonationToken(orgId, superadminEmail, reason, ipAddress);
    }

    public Page<ImpersonationLogResponse> getImpersonationLog(int page, int size) {
        return platformAdminService.getImpersonationLog(page, size);
    }

    // ── Plan Templates ──────────────────────────────────────────────────────
    public List<PlanTemplateResponse> findAllPlanTemplates() {
        return commercialAdminService.findAllPlanTemplates();
    }

    public PlanTemplateResponse findPlanTemplate(String id) {
        return commercialAdminService.findPlanTemplate(id);
    }

    public PlanTemplateResponse createPlanTemplate(PlanTemplateDto dto) {
        return commercialAdminService.createPlanTemplate(dto);
    }

    public PlanTemplateResponse updatePlanTemplate(String id, PlanTemplateDto dto) {
        return commercialAdminService.updatePlanTemplate(id, dto);
    }

    public void deletePlanTemplate(String id) {
        commercialAdminService.deletePlanTemplate(id);
    }
}
