package com.schedy.service;

import com.schedy.dto.request.*;
import com.schedy.dto.response.*;
import com.schedy.entity.PromoCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Thin facade test for SuperAdminService.
 *
 * SuperAdminService has NO business logic of its own — it only delegates to
 * OrgAdminService, CommercialAdminService and PlatformAdminService.
 * Each test verifies exactly one delegation: the right sub-service method is
 * called with the right arguments, and the return value is passed through
 * unchanged.
 *
 * Business logic is tested exhaustively in the sub-service test classes:
 *   - OrgAdminServiceTest
 *   - CommercialAdminServiceTest
 *   - PlatformAdminServiceTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminService — facade delegation tests")
class SuperAdminServiceTest {

    @Mock private OrgAdminService        orgAdminService;
    @Mock private CommercialAdminService commercialAdminService;
    @Mock private PlatformAdminService   platformAdminService;

    @InjectMocks private SuperAdminService superAdminService;

    private static final String ORG_ID = "org-facade-1";

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getDashboard() — delegates to OrgAdminService")
    class GetDashboard {

        @Test
        @DisplayName("delegates to orgAdminService.getDashboard() and returns its result")
        void delegatesToOrgAdminService() {
            SuperAdminDashboardResponse expected = new SuperAdminDashboardResponse(
                    3L, 2L, 1L, 10L, 30L, Map.of("PRO", 1L), Map.of("ACTIVE", 2L));
            when(orgAdminService.getDashboard()).thenReturn(expected);

            SuperAdminDashboardResponse result = superAdminService.getDashboard();

            verify(orgAdminService).getDashboard();
            assertThat(result).isSameAs(expected);
        }
    }

    // ── Organisation delegation ───────────────────────────────────────────────

    @Nested
    @DisplayName("Organisation methods — delegate to OrgAdminService")
    class OrgDelegation {

        @Test
        @DisplayName("findAllOrganisations delegates to orgAdminService")
        void findAllOrganisationsDelegates() {
            List<OrgSummaryResponse> expected = List.of(
                    new OrgSummaryResponse(ORG_ID, "Acme", "ACTIVE", "PRO", "CAN", 5, 2,
                            OffsetDateTime.now(), null));
            when(orgAdminService.findAllOrganisations()).thenReturn(expected);

            List<OrgSummaryResponse> result = superAdminService.findAllOrganisations();

            verify(orgAdminService).findAllOrganisations();
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("findOrganisation delegates to orgAdminService with orgId")
        void findOrganisationDelegates() {
            OrgSummaryResponse expected = new OrgSummaryResponse(
                    ORG_ID, "Acme", "ACTIVE", "PRO", "CAN", 5, 2, OffsetDateTime.now(), null);
            when(orgAdminService.findOrganisation(ORG_ID)).thenReturn(expected);

            OrgSummaryResponse result = superAdminService.findOrganisation(ORG_ID);

            verify(orgAdminService).findOrganisation(ORG_ID);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("createOrganisation delegates to orgAdminService with request")
        void createOrganisationDelegates() {
            CreateOrgRequest request = new CreateOrgRequest("NewCo", "a@b.com", null, "CAN", "PRO");
            OrgSummaryResponse expected = new OrgSummaryResponse(
                    ORG_ID, "NewCo", "ACTIVE", "PRO", "CAN", 0, 1, OffsetDateTime.now(), null);
            when(orgAdminService.createOrganisation(request)).thenReturn(expected);

            OrgSummaryResponse result = superAdminService.createOrganisation(request);

            verify(orgAdminService).createOrganisation(request);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("deleteOrganisation delegates to orgAdminService with orgId")
        void deleteOrganisationDelegates() {
            superAdminService.deleteOrganisation(ORG_ID);

            verify(orgAdminService).deleteOrganisation(ORG_ID);
            verifyNoInteractions(commercialAdminService, platformAdminService);
        }

        @Test
        @DisplayName("updateOrgStatus delegates to orgAdminService with orgId and status")
        void updateOrgStatusDelegates() {
            OrgSummaryResponse expected = new OrgSummaryResponse(
                    ORG_ID, "Co", "SUSPENDED", "ESSENTIALS", "CAN", 0, 0, OffsetDateTime.now(), null);
            when(orgAdminService.updateOrgStatus(ORG_ID, "SUSPENDED")).thenReturn(expected);

            OrgSummaryResponse result = superAdminService.updateOrgStatus(ORG_ID, "SUSPENDED");

            verify(orgAdminService).updateOrgStatus(ORG_ID, "SUSPENDED");
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("updateOrgPays delegates to orgAdminService with orgId and pays")
        void updateOrgPaysDelegates() {
            OrgSummaryResponse expected = new OrgSummaryResponse(
                    ORG_ID, "Co", "ACTIVE", "ESSENTIALS", "MDG", 0, 0, OffsetDateTime.now(), null);
            when(orgAdminService.updateOrgPays(ORG_ID, "MDG")).thenReturn(expected);

            OrgSummaryResponse result = superAdminService.updateOrgPays(ORG_ID, "MDG");

            verify(orgAdminService).updateOrgPays(ORG_ID, "MDG");
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("resendAdminInvitation delegates to orgAdminService with orgId")
        void resendAdminInvitationDelegates() {
            superAdminService.resendAdminInvitation(ORG_ID);

            verify(orgAdminService).resendAdminInvitation(ORG_ID);
            verifyNoInteractions(commercialAdminService, platformAdminService);
        }
    }

    // ── Identifications delegation ────────────────────────────────────────────

    @Nested
    @DisplayName("Identification methods — delegate to OrgAdminService")
    class IdentificationsDelegation {

        @Test
        @DisplayName("getOrgIdentifications delegates to orgAdminService with orgId")
        void getOrgIdentificationsDelegates() {
            OrgIdentificationsResponse expected = new OrgIdentificationsResponse(
                    ORG_ID, "Co", "CAN", "QC", "BN", "PI", null, null, "UNVERIFIED", null, null, null);
            when(orgAdminService.getOrgIdentifications(ORG_ID)).thenReturn(expected);

            OrgIdentificationsResponse result = superAdminService.getOrgIdentifications(ORG_ID);

            verify(orgAdminService).getOrgIdentifications(ORG_ID);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("updateOrgIdentifications delegates to orgAdminService with request")
        void updateOrgIdentificationsDelegates() {
            UpdateOrgIdentificationsRequest request =
                    new UpdateOrgIdentificationsRequest("QC", "BN", "PI", null, null);
            OrgIdentificationsResponse expected = new OrgIdentificationsResponse(
                    ORG_ID, "Co", "CAN", "QC", "BN", "PI", null, null, "UNVERIFIED", null, null, null);
            when(orgAdminService.updateOrgIdentifications(ORG_ID, request)).thenReturn(expected);

            OrgIdentificationsResponse result = superAdminService.updateOrgIdentifications(ORG_ID, request);

            verify(orgAdminService).updateOrgIdentifications(ORG_ID, request);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("updateOrgVerificationStatus delegates to orgAdminService with all params")
        void updateOrgVerificationStatusDelegates() {
            OrgIdentificationsResponse expected = new OrgIdentificationsResponse(
                    ORG_ID, "Co", "CAN", null, null, null, null, null, "VERIFIED", "admin@s.io", null, "OK");
            when(orgAdminService.updateOrgVerificationStatus(ORG_ID, "VERIFIED", "OK", "admin@s.io"))
                    .thenReturn(expected);

            OrgIdentificationsResponse result = superAdminService.updateOrgVerificationStatus(
                    ORG_ID, "VERIFIED", "OK", "admin@s.io");

            verify(orgAdminService).updateOrgVerificationStatus(ORG_ID, "VERIFIED", "OK", "admin@s.io");
            assertThat(result).isSameAs(expected);
        }
    }

    // ── Subscription delegation ───────────────────────────────────────────────

    @Nested
    @DisplayName("Subscription methods — delegate to CommercialAdminService")
    class SubscriptionDelegation {

        @Test
        @DisplayName("getSubscription delegates to commercialAdminService with orgId")
        void getSubscriptionDelegates() {
            SubscriptionResponse expected = new SubscriptionResponse(
                    "sub-1", ORG_ID, com.schedy.entity.Subscription.PlanTier.PRO,
                    com.schedy.entity.Subscription.SubscriptionStatus.ACTIVE,
                    100, 5, null, null, null, null);
            when(commercialAdminService.getSubscription(ORG_ID)).thenReturn(expected);

            SubscriptionResponse result = superAdminService.getSubscription(ORG_ID);

            verify(commercialAdminService).getSubscription(ORG_ID);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("updateSubscription delegates to commercialAdminService with orgId and dto")
        void updateSubscriptionDelegates() {
            SubscriptionDto dto = new SubscriptionDto("PRO", 50, 3, null, null);
            SubscriptionResponse expected = new SubscriptionResponse(
                    "sub-1", ORG_ID, com.schedy.entity.Subscription.PlanTier.PRO,
                    com.schedy.entity.Subscription.SubscriptionStatus.ACTIVE,
                    50, 3, null, null, null, null);
            when(commercialAdminService.updateSubscription(ORG_ID, dto)).thenReturn(expected);

            SubscriptionResponse result = superAdminService.updateSubscription(ORG_ID, dto);

            verify(commercialAdminService).updateSubscription(ORG_ID, dto);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("applyPromoCode delegates to commercialAdminService with orgId and code")
        void applyPromoCodeDelegates() {
            SubscriptionResponse expected = new SubscriptionResponse(
                    "sub-1", ORG_ID, com.schedy.entity.Subscription.PlanTier.PRO,
                    com.schedy.entity.Subscription.SubscriptionStatus.ACTIVE,
                    100, 5, null, "promo-1", null, null);
            when(commercialAdminService.applyPromoCode(ORG_ID, "BETA50")).thenReturn(expected);

            SubscriptionResponse result = superAdminService.applyPromoCode(ORG_ID, "BETA50");

            verify(commercialAdminService).applyPromoCode(ORG_ID, "BETA50");
            assertThat(result).isSameAs(expected);
        }
    }

    // ── Promo code delegation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Promo code methods — delegate to CommercialAdminService")
    class PromoCodeDelegation {

        @Test
        @DisplayName("findAllPromoCodes delegates to commercialAdminService")
        void findAllPromoCodesDelegates() {
            List<PromoCodeResponse> expected = List.of();
            when(commercialAdminService.findAllPromoCodes()).thenReturn(expected);

            List<PromoCodeResponse> result = superAdminService.findAllPromoCodes();

            verify(commercialAdminService).findAllPromoCodes();
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("createPromoCode delegates to commercialAdminService with dto")
        void createPromoCodeDelegates() {
            PromoCodeDto dto = new PromoCodeDto("NEW", "desc", 10, null, null, null, null, null, true);
            PromoCodeResponse expected = new PromoCodeResponse(
                    "p1", "NEW", "desc", 10, null, null, null, 0, null, null, true);
            when(commercialAdminService.createPromoCode(dto)).thenReturn(expected);

            PromoCodeResponse result = superAdminService.createPromoCode(dto);

            verify(commercialAdminService).createPromoCode(dto);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("updatePromoCode delegates to commercialAdminService with id and dto")
        void updatePromoCodeDelegates() {
            PromoCodeDto dto = new PromoCodeDto("NEW", "d", 10, null, null, null, null, null, true);
            PromoCodeResponse expected = new PromoCodeResponse(
                    "p1", "NEW", "d", 10, null, null, null, 0, null, null, true);
            when(commercialAdminService.updatePromoCode("p1", dto)).thenReturn(expected);

            PromoCodeResponse result = superAdminService.updatePromoCode("p1", dto);

            verify(commercialAdminService).updatePromoCode("p1", dto);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("deletePromoCode delegates to commercialAdminService with id")
        void deletePromoCodeDelegates() {
            superAdminService.deletePromoCode("p1");

            verify(commercialAdminService).deletePromoCode("p1");
            verifyNoInteractions(orgAdminService, platformAdminService);
        }

        @Test
        @DisplayName("validatePromoCode delegates to commercialAdminService and returns entity")
        void validatePromoCodeDelegates() {
            PromoCode expected = PromoCode.builder().id("p1").code("VALID").active(true).build();
            when(commercialAdminService.validatePromoCode("VALID")).thenReturn(expected);

            PromoCode result = superAdminService.validatePromoCode("VALID");

            verify(commercialAdminService).validatePromoCode("VALID");
            assertThat(result).isSameAs(expected);
        }
    }

    // ── Feature flag delegation ───────────────────────────────────────────────

    @Nested
    @DisplayName("Feature flag methods — delegate to PlatformAdminService")
    class FeatureFlagDelegation {

        @Test
        @DisplayName("getFeatureFlags delegates to platformAdminService with orgId")
        void getFeatureFlagsDelegates() {
            List<FeatureFlagResponse> expected = List.of(
                    new FeatureFlagResponse("ff-1", ORG_ID, "PLANNING", true));
            when(platformAdminService.getFeatureFlags(ORG_ID)).thenReturn(expected);

            List<FeatureFlagResponse> result = superAdminService.getFeatureFlags(ORG_ID);

            verify(platformAdminService).getFeatureFlags(ORG_ID);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("updateFeatureFlags delegates to platformAdminService with orgId and dtos")
        void updateFeatureFlagsDelegates() {
            List<FeatureFlagDto> dtos = List.of(new FeatureFlagDto("REPORTS", false));
            List<FeatureFlagResponse> expected = List.of(
                    new FeatureFlagResponse("ff-2", ORG_ID, "REPORTS", false));
            when(platformAdminService.updateFeatureFlags(ORG_ID, dtos)).thenReturn(expected);

            List<FeatureFlagResponse> result = superAdminService.updateFeatureFlags(ORG_ID, dtos);

            verify(platformAdminService).updateFeatureFlags(ORG_ID, dtos);
            assertThat(result).isSameAs(expected);
        }
    }

    // ── Announcement delegation ───────────────────────────────────────────────

    @Nested
    @DisplayName("Announcement methods — delegate to PlatformAdminService")
    class AnnouncementDelegation {

        @Test
        @DisplayName("getAnnouncements delegates to platformAdminService")
        void getAnnouncementsDelegates() {
            List<AnnouncementResponse> expected = List.of();
            when(platformAdminService.getAnnouncements()).thenReturn(expected);

            List<AnnouncementResponse> result = superAdminService.getAnnouncements();

            verify(platformAdminService).getAnnouncements();
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("createAnnouncement delegates to platformAdminService with dto")
        void createAnnouncementDelegates() {
            AnnouncementDto dto = new AnnouncementDto("T", "B", "INFO", true, null);
            AnnouncementResponse expected = new AnnouncementResponse(
                    "ann-1", "T", "B",
                    com.schedy.entity.PlatformAnnouncement.Severity.INFO,
                    true, null, null, null);
            when(platformAdminService.createAnnouncement(dto)).thenReturn(expected);

            AnnouncementResponse result = superAdminService.createAnnouncement(dto);

            verify(platformAdminService).createAnnouncement(dto);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("updateAnnouncement delegates to platformAdminService with id and dto")
        void updateAnnouncementDelegates() {
            AnnouncementDto dto = new AnnouncementDto("New", "Body", "WARNING", false, null);
            AnnouncementResponse expected = new AnnouncementResponse(
                    "ann-1", "New", "Body",
                    com.schedy.entity.PlatformAnnouncement.Severity.WARNING,
                    false, null, null, null);
            when(platformAdminService.updateAnnouncement("ann-1", dto)).thenReturn(expected);

            AnnouncementResponse result = superAdminService.updateAnnouncement("ann-1", dto);

            verify(platformAdminService).updateAnnouncement("ann-1", dto);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("deleteAnnouncement delegates to platformAdminService with id")
        void deleteAnnouncementDelegates() {
            superAdminService.deleteAnnouncement("ann-1");

            verify(platformAdminService).deleteAnnouncement("ann-1");
            verifyNoInteractions(orgAdminService, commercialAdminService);
        }
    }

    // ── Impersonation delegation ──────────────────────────────────────────────

    @Nested
    @DisplayName("Impersonation methods — delegate to PlatformAdminService")
    class ImpersonationDelegation {

        @Test
        @DisplayName("generateImpersonationToken delegates to platformAdminService with all params")
        void generateImpersonationTokenDelegates() {
            ImpersonateResponse expected = new ImpersonateResponse("tok", "Acme", "CAN", 1800L);
            when(platformAdminService.generateImpersonationToken(
                    ORG_ID, "superadmin@s.io", "Support", "1.2.3.4"))
                    .thenReturn(expected);

            ImpersonateResponse result = superAdminService.generateImpersonationToken(
                    ORG_ID, "superadmin@s.io", "Support", "1.2.3.4");

            verify(platformAdminService)
                    .generateImpersonationToken(ORG_ID, "superadmin@s.io", "Support", "1.2.3.4");
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("getImpersonationLog delegates to platformAdminService with page and size")
        void getImpersonationLogDelegates() {
            Page<ImpersonationLogResponse> expected = new PageImpl<>(List.of());
            when(platformAdminService.getImpersonationLog(1, 20)).thenReturn(expected);

            Page<ImpersonationLogResponse> result = superAdminService.getImpersonationLog(1, 20);

            verify(platformAdminService).getImpersonationLog(1, 20);
            assertThat(result).isSameAs(expected);
        }
    }

    // ── Plan template delegation ──────────────────────────────────────────────

    @Nested
    @DisplayName("Plan template methods — delegate to CommercialAdminService")
    class PlanTemplateDelegation {

        @Test
        @DisplayName("findAllPlanTemplates delegates to commercialAdminService")
        void findAllPlanTemplatesDelegates() {
            List<PlanTemplateResponse> expected = List.of();
            when(commercialAdminService.findAllPlanTemplates()).thenReturn(expected);

            List<PlanTemplateResponse> result = superAdminService.findAllPlanTemplates();

            verify(commercialAdminService).findAllPlanTemplates();
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("createPlanTemplate delegates to commercialAdminService with dto")
        void createPlanTemplateDelegates() {
            PlanTemplateDto dto = new PlanTemplateDto(
                    "STARTER", "Starter", null, 25, 2, null, null, 14, true, 1, null);
            PlanTemplateResponse expected = new PlanTemplateResponse(
                    "t1", "STARTER", "Starter", null, 25, 2,
                    null, null, 14, true, 1, Map.of(), null, null);
            when(commercialAdminService.createPlanTemplate(dto)).thenReturn(expected);

            PlanTemplateResponse result = superAdminService.createPlanTemplate(dto);

            verify(commercialAdminService).createPlanTemplate(dto);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("updatePlanTemplate delegates to commercialAdminService with id and dto")
        void updatePlanTemplateDelegates() {
            PlanTemplateDto dto = new PlanTemplateDto(
                    "STARTER", "Updated", null, 30, 3, null, null, 30, true, 1, null);
            PlanTemplateResponse expected = new PlanTemplateResponse(
                    "t1", "STARTER", "Updated", null, 30, 3,
                    null, null, 30, true, 1, Map.of(), null, null);
            when(commercialAdminService.updatePlanTemplate("t1", dto)).thenReturn(expected);

            PlanTemplateResponse result = superAdminService.updatePlanTemplate("t1", dto);

            verify(commercialAdminService).updatePlanTemplate("t1", dto);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("deletePlanTemplate delegates to commercialAdminService with id")
        void deletePlanTemplateDelegates() {
            superAdminService.deletePlanTemplate("t1");

            verify(commercialAdminService).deletePlanTemplate("t1");
            verifyNoInteractions(orgAdminService, platformAdminService);
        }

        @Test
        @DisplayName("findPlanTemplate delegates to commercialAdminService with id")
        void findPlanTemplateDelegates() {
            PlanTemplateResponse expected = new PlanTemplateResponse(
                    "t1", "PRO", "Pro", null, 100, 10,
                    null, null, 30, true, 2, Map.of(), null, null);
            when(commercialAdminService.findPlanTemplate("t1")).thenReturn(expected);

            PlanTemplateResponse result = superAdminService.findPlanTemplate("t1");

            verify(commercialAdminService).findPlanTemplate("t1");
            assertThat(result).isSameAs(expected);
        }
    }
}
