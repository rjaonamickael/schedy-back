package com.schedy.service;

import com.schedy.config.JwtUtil;
import com.schedy.dto.request.PromoCodeDto;
import com.schedy.dto.request.SubscriptionDto;
import com.schedy.dto.response.FeatureFlagResponse;
import com.schedy.dto.response.ImpersonationLogResponse;
import com.schedy.dto.response.PromoCodeResponse;
import com.schedy.dto.response.SubscriptionResponse;
import com.schedy.dto.response.SuperAdminDashboardResponse;
import com.schedy.entity.AbsenceImprevue;
import com.schedy.entity.DemandeConge;
import com.schedy.entity.FeatureFlag;
import com.schedy.entity.ImpersonationLog;
import com.schedy.entity.Organisation;
import com.schedy.entity.Pause;
import com.schedy.entity.PromoCode;
import com.schedy.entity.RegistrationRequest;
import com.schedy.entity.Subscription;
import com.schedy.repository.*;
import jakarta.persistence.Version;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminService unit tests (B-05, B-09, B-16, B-17, C-02, C-03)")
class SuperAdminServiceTest {

    @Mock private OrganisationRepository organisationRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmployeRepository employeRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PromoCodeRepository promoCodeRepository;
    @Mock private FeatureFlagRepository featureFlagRepository;
    @Mock private PlatformAnnouncementRepository announcementRepository;
    @Mock private ImpersonationLogRepository impersonationLogRepository;
    @Mock private PointageRepository pointageRepository;
    @Mock private PointageCodeRepository pointageCodeRepository;
    @Mock private CreneauAssigneRepository creneauAssigneRepository;
    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private AbsenceImprevueRepository absenceImprevueRepository;
    @Mock private PauseRepository pauseRepository;
    @Mock private BanqueCongeRepository banqueCongeRepository;
    @Mock private ExigenceRepository exigenceRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private TypeCongeRepository typeCongeRepository;
    @Mock private JourFerieRepository jourFerieRepository;
    @Mock private ParametresRepository parametresRepository;
    @Mock private PlanTemplateRepository planTemplateRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private EmailService emailService;

    @InjectMocks private SuperAdminService superAdminService;

    private static final String ORG_ID = "org-abc";

    private Subscription stubSubscription() {
        Subscription sub = Subscription.builder()
                .id("sub-1")
                .organisationId(ORG_ID)
                .planTier(Subscription.PlanTier.ESSENTIALS)
                .maxEmployees(15)
                .maxSites(1)
                .build();
        lenient().when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(sub));
        lenient().when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        return sub;
    }

    // ── C-02 — updateSubscription: trialEndsAt ──

    @Nested
    @DisplayName("updateSubscription() — C-02")
    class UpdateSubscription {

        @Test
        @DisplayName("maps trialEndsAt to entity when non-null")
        void setsTrialEndsAt() {
            when(organisationRepository.findById(ORG_ID)).thenReturn(
                    Optional.of(Organisation.builder().id(ORG_ID).nom("Acme").status("ACTIVE").pays("FR").build()));
            stubSubscription();
            OffsetDateTime trial = OffsetDateTime.now().plusDays(30);
            SubscriptionDto dto = new SubscriptionDto("ESSENTIALS", 0, 0, null, trial);

            ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
            when(subscriptionRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            superAdminService.updateSubscription(ORG_ID, dto);

            assertThat(cap.getValue().getTrialEndsAt()).isEqualTo(trial);
        }

        @Test
        @DisplayName("preserves existing trialEndsAt when DTO field is null")
        void preservesExisting() {
            when(organisationRepository.findById(ORG_ID)).thenReturn(
                    Optional.of(Organisation.builder().id(ORG_ID).nom("Acme").status("ACTIVE").pays("FR").build()));
            OffsetDateTime existing = OffsetDateTime.now().plusDays(14);
            Subscription sub = Subscription.builder()
                    .id("sub-1").organisationId(ORG_ID)
                    .planTier(Subscription.PlanTier.ESSENTIALS)
                    .maxEmployees(15).maxSites(1)
                    .trialEndsAt(existing).build();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(sub));
            ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
            when(subscriptionRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            superAdminService.updateSubscription(ORG_ID, new SubscriptionDto("ESSENTIALS", 0, 0, null, null));

            assertThat(cap.getValue().getTrialEndsAt()).isEqualTo(existing);
        }
    }

    // ── C-03 — createPromoCode: active default ──

    @Nested
    @DisplayName("createPromoCode() — C-03")
    class CreatePromoCode {

        @Test
        @DisplayName("defaults active to true when null")
        void defaultsActiveTrue() {
            when(promoCodeRepository.existsByCode("SUMMER50")).thenReturn(false);
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeResponse result = superAdminService.createPromoCode(
                    new PromoCodeDto("SUMMER50", "Summer", 50, null, null, null, null, null, null));

            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("respects explicit active = false")
        void respectsExplicitFalse() {
            when(promoCodeRepository.existsByCode("OFF10")).thenReturn(false);
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeResponse result = superAdminService.createPromoCode(
                    new PromoCodeDto("OFF10", "Disabled", 10, null, null, null, null, null, false));

            assertThat(result.active()).isFalse();
        }
    }

    // ── C-03 — updatePromoCode: soft-delete guard ──

    @Nested
    @DisplayName("updatePromoCode() — C-03")
    class UpdatePromoCode {

        @Test
        @DisplayName("deactivates active code when active=false sent")
        void deactivatesActiveCode() {
            PromoCode promo = PromoCode.builder().id("p1").code("X").active(true).build();
            when(promoCodeRepository.findById("p1")).thenReturn(Optional.of(promo));
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeResponse result = superAdminService.updatePromoCode("p1",
                    new PromoCodeDto("X", "d", null, null, null, null, null, null, false));

            assertThat(result.active()).isFalse();
        }

        @Test
        @DisplayName("reactivates soft-deleted code when active=true sent")
        void reactivatesSoftDeleted() {
            PromoCode promo = PromoCode.builder().id("p2").code("Y").active(false).build();
            when(promoCodeRepository.findById("p2")).thenReturn(Optional.of(promo));
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeResponse result = superAdminService.updatePromoCode("p2",
                    new PromoCodeDto("Y", "d", null, null, null, null, null, null, true));

            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("throws CONFLICT on soft-deleted code without active=true")
        void rejectsUpdateOnSoftDeleted() {
            PromoCode promo = PromoCode.builder().id("p3").code("Z").active(false).build();
            when(promoCodeRepository.findById("p3")).thenReturn(Optional.of(promo));

            assertThatThrownBy(() -> superAdminService.updatePromoCode("p3",
                    new PromoCodeDto("Z", "d", null, null, null, null, null, null, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("throws CONFLICT on soft-deleted code with active=false")
        void rejectsUpdateWithActiveFalseOnSoftDeleted() {
            PromoCode promo = PromoCode.builder().id("p4").code("W").active(false).build();
            when(promoCodeRepository.findById("p4")).thenReturn(Optional.of(promo));

            assertThatThrownBy(() -> superAdminService.updatePromoCode("p4",
                    new PromoCodeDto("W", "d", null, null, null, null, null, null, false)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // ── B-09 — deleteOrganisation: cascade order ──────────────────────────────

    @Nested
    @DisplayName("deleteOrganisation() — B-09 cascade delete order")
    class DeleteOrganisation {

        private Organisation stubOrg() {
            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom("Acme").status("ACTIVE").pays("FR").build();
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            return org;
        }

        @Test
        @DisplayName("calls absenceImprevueRepository.deleteByOrganisationId")
        void deletesAbsenceImprevues() {
            stubOrg();

            superAdminService.deleteOrganisation(ORG_ID);

            verify(absenceImprevueRepository).deleteByOrganisationId(ORG_ID);
        }

        @Test
        @DisplayName("calls pauseRepository.deleteByOrganisationId")
        void deletesPauses() {
            stubOrg();

            superAdminService.deleteOrganisation(ORG_ID);

            verify(pauseRepository).deleteByOrganisationId(ORG_ID);
        }

        @Test
        @DisplayName("absenceImprevue and pause are deleted BEFORE employeRepository (dependency order)")
        void absenceAndPauseDeletedBeforeEmploye() {
            stubOrg();

            superAdminService.deleteOrganisation(ORG_ID);

            InOrder inOrder = inOrder(absenceImprevueRepository, pauseRepository, employeRepository);
            inOrder.verify(absenceImprevueRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(pauseRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(employeRepository).deleteByOrganisationId(ORG_ID);
        }
    }

    // ── B-17 — @Version on optimistic-lock entities ───────────────────────────

    /**
     * Helper that locates a field (declared on the class or any superclass)
     * annotated with {@link Version} and typed as {@code Long}/{@code long}.
     * Returns {@code null} if none is found.
     */
    private static Field findVersionField(Class<?> clazz) {
        Class<?> cursor = clazz;
        while (cursor != null && cursor != Object.class) {
            for (Field f : cursor.getDeclaredFields()) {
                if (f.isAnnotationPresent(Version.class)
                        && (f.getType() == Long.class || f.getType() == long.class)) {
                    return f;
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    @Nested
    @DisplayName("@Version optimistic-lock field — B-17")
    class EntityVersionAnnotation {

        @Test
        @DisplayName("DemandeConge has @Version Long version")
        void demandeCongeHasVersion() {
            Field versionField = findVersionField(DemandeConge.class);
            assertThat(versionField)
                    .as("DemandeConge must declare a Long field annotated with @Version")
                    .isNotNull();
            assertThat(versionField.getName()).isEqualTo("version");
        }

        @Test
        @DisplayName("AbsenceImprevue has @Version Long version")
        void absenceImprevueHasVersion() {
            Field versionField = findVersionField(AbsenceImprevue.class);
            assertThat(versionField)
                    .as("AbsenceImprevue must declare a Long field annotated with @Version")
                    .isNotNull();
            assertThat(versionField.getName()).isEqualTo("version");
        }

        @Test
        @DisplayName("Pause has @Version Long version")
        void pauseHasVersion() {
            Field versionField = findVersionField(Pause.class);
            assertThat(versionField)
                    .as("Pause must declare a Long field annotated with @Version")
                    .isNotNull();
            assertThat(versionField.getName()).isEqualTo("version");
        }

        @Test
        @DisplayName("RegistrationRequest has @Version Long version")
        void registrationRequestHasVersion() {
            Field versionField = findVersionField(RegistrationRequest.class);
            assertThat(versionField)
                    .as("RegistrationRequest must declare a Long field annotated with @Version")
                    .isNotNull();
            assertThat(versionField.getName()).isEqualTo("version");
        }
    }

    // ── B-05 — getDashboard: GROUP BY queries ─────────────────────────────────

    @Nested
    @DisplayName("getDashboard() — B-05 GROUP BY queries")
    class GetDashboard {

        /**
         * Stubs the minimum set of repositories that getDashboard() calls.
         * Returns a fixed set of grouped results covering the full enum range
         * so the stream.collect() inside the service does not throw.
         */
        private void stubDashboardDependencies() {
            when(organisationRepository.count()).thenReturn(5L);
            when(organisationRepository.countByStatus("ACTIVE")).thenReturn(4L);
            when(organisationRepository.countByStatus("SUSPENDED")).thenReturn(1L);
            when(userRepository.count()).thenReturn(10L);
            when(employeRepository.count()).thenReturn(30L);

            // B-05: grouped query — one row per PlanTier
            when(subscriptionRepository.countByPlanTierGrouped()).thenReturn(List.of(
                    new Object[]{Subscription.PlanTier.ESSENTIALS,    3L},
                    new Object[]{Subscription.PlanTier.STARTER, 1L},
                    new Object[]{Subscription.PlanTier.PRO,     1L}
            ));

            // B-05: grouped query — one row per SubscriptionStatus
            when(subscriptionRepository.countByStatusGrouped()).thenReturn(List.of(
                    new Object[]{Subscription.SubscriptionStatus.TRIAL,     2L},
                    new Object[]{Subscription.SubscriptionStatus.ACTIVE,    2L},
                    new Object[]{Subscription.SubscriptionStatus.SUSPENDED, 1L}
            ));
        }

        @Test
        @DisplayName("calls countByPlanTierGrouped() — not individual findByPlanTier per enum")
        void usesPlanTierGroupedQuery() {
            stubDashboardDependencies();

            superAdminService.getDashboard();

            // B-05: must use the single GROUP BY query
            verify(subscriptionRepository, times(1)).countByPlanTierGrouped();
            // Must NOT fall back to per-enum individual queries
            verify(subscriptionRepository, never()).findByPlanTier(any());
        }

        @Test
        @DisplayName("calls countByStatusGrouped() — not individual findByStatus per enum")
        void usesStatusGroupedQuery() {
            stubDashboardDependencies();

            superAdminService.getDashboard();

            // B-05: must use the single GROUP BY query
            verify(subscriptionRepository, times(1)).countByStatusGrouped();
            // Must NOT fall back to per-enum individual queries
            verify(subscriptionRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("returns correct aggregate counts from repositories")
        void returnsCorrectAggregates() {
            stubDashboardDependencies();

            SuperAdminDashboardResponse response = superAdminService.getDashboard();

            assertThat(response.totalOrganisations()).isEqualTo(5L);
            assertThat(response.activeOrganisations()).isEqualTo(4L);
            assertThat(response.suspendedOrganisations()).isEqualTo(1L);
            assertThat(response.totalUsers()).isEqualTo(10L);
            assertThat(response.totalEmployees()).isEqualTo(30L);
        }

        @Test
        @DisplayName("orgsByPlan map contains all returned PlanTier keys")
        void orgsByPlanMapIsPopulatedCorrectly() {
            stubDashboardDependencies();

            SuperAdminDashboardResponse response = superAdminService.getDashboard();

            Map<String, Long> orgsByPlan = response.orgsByPlan();
            assertThat(orgsByPlan)
                    .containsEntry("ESSENTIALS",    3L)
                    .containsEntry("STARTER", 1L)
                    .containsEntry("PRO",     1L);
        }

        @Test
        @DisplayName("orgsByStatus map contains all returned SubscriptionStatus keys")
        void orgsByStatusMapIsPopulatedCorrectly() {
            stubDashboardDependencies();

            SuperAdminDashboardResponse response = superAdminService.getDashboard();

            Map<String, Long> orgsByStatus = response.orgsByStatus();
            assertThat(orgsByStatus)
                    .containsEntry("TRIAL",     2L)
                    .containsEntry("ACTIVE",    2L)
                    .containsEntry("SUSPENDED", 1L);
        }

        @Test
        @DisplayName("empty grouped results produce empty maps (no NPE)")
        void emptyGroupedResultsProduceEmptyMaps() {
            when(organisationRepository.count()).thenReturn(0L);
            when(organisationRepository.countByStatus("ACTIVE")).thenReturn(0L);
            when(organisationRepository.countByStatus("SUSPENDED")).thenReturn(0L);
            when(userRepository.count()).thenReturn(0L);
            when(employeRepository.count()).thenReturn(0L);
            when(subscriptionRepository.countByPlanTierGrouped()).thenReturn(List.of());
            when(subscriptionRepository.countByStatusGrouped()).thenReturn(List.of());

            SuperAdminDashboardResponse response = superAdminService.getDashboard();

            assertThat(response.orgsByPlan()).isEmpty();
            assertThat(response.orgsByStatus()).isEmpty();
        }
    }

    // ── B-16 — DTO from() factory mapping ────────────────────────────────────

    @Nested
    @DisplayName("SubscriptionResponse.from() — B-16 DTO mapping")
    class SubscriptionResponseMapping {

        @Test
        @DisplayName("maps all fields from entity to record")
        void mapsAllFields() {
            OffsetDateTime trial   = OffsetDateTime.now().plusDays(30);
            OffsetDateTime created = OffsetDateTime.now().minusDays(1);

            Subscription entity = Subscription.builder()
                    .id("sub-1")
                    .organisationId("org-1")
                    .planTier(Subscription.PlanTier.STARTER)
                    .status(Subscription.SubscriptionStatus.TRIAL)
                    .maxEmployees(50)
                    .maxSites(3)
                    .trialEndsAt(trial)
                    .promoCodeId("promo-abc")
                    .createdAt(created)
                    .build();

            SubscriptionResponse response = SubscriptionResponse.from(entity);

            assertThat(response.id()).isEqualTo("sub-1");
            assertThat(response.organisationId()).isEqualTo("org-1");
            assertThat(response.planTier()).isEqualTo(Subscription.PlanTier.STARTER);
            assertThat(response.status()).isEqualTo(Subscription.SubscriptionStatus.TRIAL);
            assertThat(response.maxEmployees()).isEqualTo(50);
            assertThat(response.maxSites()).isEqualTo(3);
            assertThat(response.trialEndsAt()).isEqualTo(trial);
            assertThat(response.promoCodeId()).isEqualTo("promo-abc");
            assertThat(response.createdAt()).isEqualTo(created);
        }

        @Test
        @DisplayName("nullable fields (trialEndsAt, promoCodeId) map to null when absent")
        void nullableFieldsMappedToNull() {
            Subscription entity = Subscription.builder()
                    .id("sub-2")
                    .organisationId("org-2")
                    .planTier(Subscription.PlanTier.ESSENTIALS)
                    .build();

            SubscriptionResponse response = SubscriptionResponse.from(entity);

            assertThat(response.trialEndsAt()).isNull();
            assertThat(response.promoCodeId()).isNull();
        }
    }

    @Nested
    @DisplayName("PromoCodeResponse.from() — B-16 DTO mapping")
    class PromoCodeResponseMapping {

        @Test
        @DisplayName("maps all fields from entity to record")
        void mapsAllFields() {
            OffsetDateTime validFrom = OffsetDateTime.now().minusDays(5);
            OffsetDateTime validTo   = OffsetDateTime.now().plusDays(25);

            PromoCode entity = PromoCode.builder()
                    .id("promo-1")
                    .code("SUMMER50")
                    .description("Summer discount")
                    .discountPercent(50)
                    .discountMonths(3)
                    .planOverride("PRO")
                    .maxUses(100)
                    .currentUses(10)
                    .validFrom(validFrom)
                    .validTo(validTo)
                    .active(true)
                    .build();

            PromoCodeResponse response = PromoCodeResponse.from(entity);

            assertThat(response.id()).isEqualTo("promo-1");
            assertThat(response.code()).isEqualTo("SUMMER50");
            assertThat(response.description()).isEqualTo("Summer discount");
            assertThat(response.discountPercent()).isEqualTo(50);
            assertThat(response.discountMonths()).isEqualTo(3);
            assertThat(response.planOverride()).isEqualTo("PRO");
            assertThat(response.maxUses()).isEqualTo(100);
            assertThat(response.currentUses()).isEqualTo(10);
            assertThat(response.validFrom()).isEqualTo(validFrom);
            assertThat(response.validTo()).isEqualTo(validTo);
            assertThat(response.active()).isTrue();
        }

        @Test
        @DisplayName("optional fields (discountPercent, discountMonths, planOverride, maxUses, validTo) map to null when absent")
        void nullableFieldsMappedToNull() {
            PromoCode entity = PromoCode.builder()
                    .id("promo-2")
                    .code("MINIMAL")
                    .active(true)
                    .build();

            PromoCodeResponse response = PromoCodeResponse.from(entity);

            assertThat(response.discountPercent()).isNull();
            assertThat(response.discountMonths()).isNull();
            assertThat(response.planOverride()).isNull();
            assertThat(response.maxUses()).isNull();
            assertThat(response.validTo()).isNull();
        }

        @Test
        @DisplayName("active=false is preserved in response")
        void inactiveFlagPreserved() {
            PromoCode entity = PromoCode.builder()
                    .id("promo-3")
                    .code("EXPIRED")
                    .active(false)
                    .build();

            PromoCodeResponse response = PromoCodeResponse.from(entity);

            assertThat(response.active()).isFalse();
        }
    }

    @Nested
    @DisplayName("FeatureFlagResponse.from() — B-16 DTO mapping")
    class FeatureFlagResponseMapping {

        @Test
        @DisplayName("maps all fields from entity to record")
        void mapsAllFields() {
            FeatureFlag entity = FeatureFlag.builder()
                    .id("ff-1")
                    .organisationId("org-1")
                    .featureKey("PLANNING_EXPORT")
                    .enabled(true)
                    .build();

            FeatureFlagResponse response = FeatureFlagResponse.from(entity);

            assertThat(response.id()).isEqualTo("ff-1");
            assertThat(response.organisationId()).isEqualTo("org-1");
            assertThat(response.featureKey()).isEqualTo("PLANNING_EXPORT");
            assertThat(response.enabled()).isTrue();
        }

        @Test
        @DisplayName("enabled=false is preserved in response")
        void disabledFlagPreserved() {
            FeatureFlag entity = FeatureFlag.builder()
                    .id("ff-2")
                    .organisationId("org-1")
                    .featureKey("BETA_FEATURE")
                    .enabled(false)
                    .build();

            FeatureFlagResponse response = FeatureFlagResponse.from(entity);

            assertThat(response.enabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("ImpersonationLogResponse.from() — B-16 DTO mapping")
    class ImpersonationLogResponseMapping {

        @Test
        @DisplayName("maps all fields from entity to record")
        void mapsAllFields() {
            OffsetDateTime startedAt = OffsetDateTime.now().minusMinutes(10);
            OffsetDateTime endedAt   = OffsetDateTime.now();

            ImpersonationLog entity = ImpersonationLog.builder()
                    .id("log-1")
                    .superadminEmail("admin@schedy.io")
                    .targetOrgId("org-xyz")
                    .targetOrgName("Acme Corp")
                    .reason("Support request #42")
                    .ipAddress("192.168.1.1")
                    .startedAt(startedAt)
                    .endedAt(endedAt)
                    .build();

            ImpersonationLogResponse response = ImpersonationLogResponse.from(entity);

            assertThat(response.id()).isEqualTo("log-1");
            assertThat(response.superadminEmail()).isEqualTo("admin@schedy.io");
            //assertThat(response.organisationId()).isEqualTo("org-xyz");
            assertThat(response.organisationName()).isEqualTo("Acme Corp");
            assertThat(response.reason()).isEqualTo("Support request #42");
            assertThat(response.ipAddress()).isEqualTo("192.168.1.1");
            assertThat(response.startedAt()).isEqualTo(startedAt);
            assertThat(response.endedAt()).isEqualTo(endedAt);
        }

        @Test
        @DisplayName("nullable fields (endedAt, reason, ipAddress) map to null when absent")
        void nullableFieldsMappedToNull() {
            ImpersonationLog entity = ImpersonationLog.builder()
                    .id("log-2")
                    .superadminEmail("admin@schedy.io")
                    .targetOrgId("org-xyz")
                    .targetOrgName("Acme Corp")
                    .build();

            ImpersonationLogResponse response = ImpersonationLogResponse.from(entity);

            assertThat(response.endedAt()).isNull();
            assertThat(response.reason()).isNull();
            assertThat(response.ipAddress()).isNull();
        }
    }
}
