package com.schedy.service;

import com.schedy.dto.request.CreateOrgRequest;
import com.schedy.dto.request.UpdateOrgIdentificationsRequest;
import com.schedy.dto.response.OrgIdentificationsResponse;
import com.schedy.dto.response.OrgSummaryResponse;
import com.schedy.dto.response.SuperAdminDashboardResponse;
import com.schedy.entity.Organisation;
import com.schedy.entity.PromoCode;
import com.schedy.entity.Subscription;
import com.schedy.entity.User;
import com.schedy.repository.*;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrgAdminService unit tests")
class OrgAdminServiceTest {

    @Mock private OrganisationRepository        organisationRepository;
    @Mock private UserRepository                userRepository;
    @Mock private EmployeRepository             employeRepository;
    @Mock private SubscriptionRepository        subscriptionRepository;
    @Mock private PromoCodeRepository           promoCodeRepository;
    @Mock private FeatureFlagRepository         featureFlagRepository;
    @Mock private PlatformAnnouncementRepository announcementRepository;
    @Mock private PointageRepository            pointageRepository;
    @Mock private PointageCodeRepository        pointageCodeRepository;
    @Mock private CreneauAssigneRepository      creneauAssigneRepository;
    @Mock private DemandeCongeRepository        demandeCongeRepository;
    @Mock private AbsenceImprevueRepository     absenceImprevueRepository;
    @Mock private PauseRepository               pauseRepository;
    @Mock private BanqueCongeRepository         banqueCongeRepository;
    @Mock private ExigenceRepository            exigenceRepository;
    @Mock private SiteRepository                siteRepository;
    @Mock private RoleRepository                roleRepository;
    @Mock private TypeCongeRepository           typeCongeRepository;
    @Mock private JourFerieRepository           jourFerieRepository;
    @Mock private ParametresRepository          parametresRepository;
    @Mock private PasswordEncoder               passwordEncoder;
    @Mock private EmailService                  emailService;

    @InjectMocks private OrgAdminService orgAdminService;

    private static final String ORG_ID   = "org-test-1";
    private static final String ORG_NOM  = "Acme Corp";
    private static final String ORG_PAYS = "CAN";

    private Organisation stubOrg() {
        Organisation org = Organisation.builder()
                .id(ORG_ID).nom(ORG_NOM).status("ACTIVE").pays(ORG_PAYS)
                .createdAt(OffsetDateTime.now()).build();
        lenient().when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
        lenient().when(organisationRepository.save(any(Organisation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        return org;
    }

    private Subscription stubSubscription() {
        Subscription sub = Subscription.builder()
                .id("sub-1").organisationId(ORG_ID)
                .planTier(Subscription.PlanTier.ESSENTIALS)
                .maxEmployees(15).maxSites(1).build();
        lenient().when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(sub));
        return sub;
    }

    // ── getDashboard ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getDashboard()")
    class GetDashboard {

        private void stubDashboard() {
            when(organisationRepository.count()).thenReturn(3L);
            when(organisationRepository.countByStatus("ACTIVE")).thenReturn(2L);
            when(organisationRepository.countByStatus("SUSPENDED")).thenReturn(1L);
            when(userRepository.count()).thenReturn(7L);
            when(employeRepository.count()).thenReturn(20L);
            when(subscriptionRepository.countByPlanTierGrouped()).thenReturn(List.of(
                    new Object[]{Subscription.PlanTier.ESSENTIALS, 2L},
                    new Object[]{Subscription.PlanTier.PRO, 1L}
            ));
            when(subscriptionRepository.countByStatusGrouped()).thenReturn(List.of(
                    new Object[]{Subscription.SubscriptionStatus.TRIAL, 1L},
                    new Object[]{Subscription.SubscriptionStatus.ACTIVE, 2L}
            ));
        }

        @Test
        @DisplayName("returns correct aggregate counts")
        void returnsCorrectCounts() {
            stubDashboard();

            SuperAdminDashboardResponse response = orgAdminService.getDashboard();

            assertThat(response.totalOrganisations()).isEqualTo(3L);
            assertThat(response.activeOrganisations()).isEqualTo(2L);
            assertThat(response.suspendedOrganisations()).isEqualTo(1L);
            assertThat(response.totalUsers()).isEqualTo(7L);
            assertThat(response.totalEmployees()).isEqualTo(20L);
        }

        @Test
        @DisplayName("orgsByPlan map is populated from grouped query")
        void orgsByPlanPopulatedFromGroupedQuery() {
            stubDashboard();

            SuperAdminDashboardResponse response = orgAdminService.getDashboard();

            Map<String, Long> byPlan = response.orgsByPlan();
            assertThat(byPlan).containsEntry("ESSENTIALS", 2L).containsEntry("PRO", 1L);
        }

        @Test
        @DisplayName("orgsByStatus map is populated from grouped query")
        void orgsByStatusPopulatedFromGroupedQuery() {
            stubDashboard();

            SuperAdminDashboardResponse response = orgAdminService.getDashboard();

            Map<String, Long> byStatus = response.orgsByStatus();
            assertThat(byStatus).containsEntry("TRIAL", 1L).containsEntry("ACTIVE", 2L);
        }

        @Test
        @DisplayName("uses single GROUP BY queries — never per-enum individual queries")
        void usesBatchGroupedQueries() {
            stubDashboard();

            orgAdminService.getDashboard();

            verify(subscriptionRepository, times(1)).countByPlanTierGrouped();
            verify(subscriptionRepository, never()).findByPlanTier(any());
            verify(subscriptionRepository, times(1)).countByStatusGrouped();
            verify(subscriptionRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("empty grouped results produce empty maps without NPE")
        void emptyGroupedResultsProduceEmptyMaps() {
            when(organisationRepository.count()).thenReturn(0L);
            when(organisationRepository.countByStatus("ACTIVE")).thenReturn(0L);
            when(organisationRepository.countByStatus("SUSPENDED")).thenReturn(0L);
            when(userRepository.count()).thenReturn(0L);
            when(employeRepository.count()).thenReturn(0L);
            when(subscriptionRepository.countByPlanTierGrouped()).thenReturn(List.of());
            when(subscriptionRepository.countByStatusGrouped()).thenReturn(List.of());

            SuperAdminDashboardResponse response = orgAdminService.getDashboard();

            assertThat(response.orgsByPlan()).isEmpty();
            assertThat(response.orgsByStatus()).isEmpty();
        }
    }

    // ── findAllOrganisations ─────────────────────────────────────────────────

    @Nested
    @DisplayName("findAllOrganisations()")
    class FindAllOrganisations {

        @Test
        @DisplayName("returns empty list when no organisations exist")
        void returnsEmptyListWhenNone() {
            when(organisationRepository.findAll()).thenReturn(List.of());

            List<OrgSummaryResponse> result = orgAdminService.findAllOrganisations();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns summary for each organisation including plan tier from subscription")
        void returnsSummaryWithPlanTier() {
            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom(ORG_NOM).status("ACTIVE").pays(ORG_PAYS)
                    .createdAt(OffsetDateTime.now()).build();
            when(organisationRepository.findAll()).thenReturn(List.of(org));
            when(employeRepository.countGroupedByOrganisationId(any()))
                    .thenReturn(java.util.Collections.singletonList(new Object[]{ORG_ID, 5L}));
            when(userRepository.countGroupedByOrganisationId(any()))
                    .thenReturn(java.util.Collections.singletonList(new Object[]{ORG_ID, 2L}));
            Subscription sub = Subscription.builder()
                    .id("sub-1").organisationId(ORG_ID)
                    .planTier(Subscription.PlanTier.PRO).build();
            when(subscriptionRepository.findByOrganisationIdIn(any())).thenReturn(List.of(sub));

            List<OrgSummaryResponse> result = orgAdminService.findAllOrganisations();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).planTier()).isEqualTo("PRO");
            assertThat(result.get(0).employeeCount()).isEqualTo(5L);
            assertThat(result.get(0).userCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("shows NONE plan tier when organisation has no subscription")
        void showsNonePlanTierWhenNoSubscription() {
            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom(ORG_NOM).status("ACTIVE").pays(ORG_PAYS)
                    .createdAt(OffsetDateTime.now()).build();
            when(organisationRepository.findAll()).thenReturn(List.of(org));
            when(employeRepository.countGroupedByOrganisationId(any())).thenReturn(List.of());
            when(userRepository.countGroupedByOrganisationId(any())).thenReturn(List.of());
            when(subscriptionRepository.findByOrganisationIdIn(any())).thenReturn(List.of());

            List<OrgSummaryResponse> result = orgAdminService.findAllOrganisations();

            assertThat(result.get(0).planTier()).isEqualTo("NONE");
        }

        @Test
        @DisplayName("resolves promo code label from subscription promoCodeId")
        void resolvesPromoCodeLabel() {
            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom(ORG_NOM).status("ACTIVE").pays(ORG_PAYS)
                    .createdAt(OffsetDateTime.now()).build();
            Subscription sub = Subscription.builder()
                    .id("sub-1").organisationId(ORG_ID)
                    .planTier(Subscription.PlanTier.ESSENTIALS)
                    .promoCodeId("promo-1").build();
            PromoCode promo = PromoCode.builder().id("promo-1").code("BETA50").build();

            when(organisationRepository.findAll()).thenReturn(List.of(org));
            when(employeRepository.countGroupedByOrganisationId(any())).thenReturn(List.of());
            when(userRepository.countGroupedByOrganisationId(any())).thenReturn(List.of());
            when(subscriptionRepository.findByOrganisationIdIn(any())).thenReturn(List.of(sub));
            when(promoCodeRepository.findByIdIn(any())).thenReturn(List.of(promo));

            List<OrgSummaryResponse> result = orgAdminService.findAllOrganisations();

            assertThat(result.get(0).promoCode()).isEqualTo("BETA50");
        }
    }

    // ── findOrganisation ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("findOrganisation()")
    class FindOrganisation {

        @Test
        @DisplayName("returns OrgSummaryResponse when organisation exists")
        void returnsResponseWhenFound() {
            stubOrg();
            stubSubscription();
            when(employeRepository.countByOrganisationId(ORG_ID)).thenReturn(3L);
            when(userRepository.countByOrganisationId(ORG_ID)).thenReturn(1L);

            OrgSummaryResponse result = orgAdminService.findOrganisation(ORG_ID);

            assertThat(result.id()).isEqualTo(ORG_ID);
            assertThat(result.nom()).isEqualTo(ORG_NOM);
        }

        @Test
        @DisplayName("throws 404 when organisation not found")
        void throws404WhenNotFound() {
            when(organisationRepository.findById("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orgAdminService.findOrganisation("unknown"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── createOrganisation ───────────────────────────────────────────────────

    @Nested
    @DisplayName("createOrganisation()")
    class CreateOrganisation {

        @Test
        @DisplayName("saves organisation, user and subscription then returns summary")
        void createsSavesAllEntities() {
            when(organisationRepository.existsByNom("NewCo")).thenReturn(false);
            when(organisationRepository.save(any(Organisation.class))).thenAnswer(inv -> {
                Organisation o = inv.getArgument(0);
                return Organisation.builder()
                        .id(ORG_ID).nom(o.getNom()).status(o.getStatus()).pays(o.getPays())
                        .createdAt(o.getCreatedAt()).build();
            });
            when(userRepository.existsByEmail("admin@newco.com")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(employeRepository.countByOrganisationId(ORG_ID)).thenReturn(0L);
            when(userRepository.countByOrganisationId(ORG_ID)).thenReturn(1L);
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(
                    Optional.of(Subscription.builder().id("s1").organisationId(ORG_ID)
                            .planTier(Subscription.PlanTier.ESSENTIALS).build()));

            CreateOrgRequest request = new CreateOrgRequest("NewCo", "admin@newco.com", null, "CAN", "PRO");

            OrgSummaryResponse result = orgAdminService.createOrganisation(request);

            assertThat(result.nom()).isEqualTo("NewCo");
            verify(subscriptionRepository).save(any(Subscription.class));
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("subscription uses requested planTier when valid")
        void usesRequestedPlanTier() {
            when(organisationRepository.existsByNom(any())).thenReturn(false);
            when(organisationRepository.save(any(Organisation.class))).thenAnswer(inv -> {
                Organisation o = inv.getArgument(0);
                return Organisation.builder()
                        .id(ORG_ID).nom(o.getNom()).status("ACTIVE").pays("CAN").build();
            });
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
            when(subscriptionRepository.save(subCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
            when(employeRepository.countByOrganisationId(any())).thenReturn(0L);
            when(userRepository.countByOrganisationId(any())).thenReturn(0L);
            when(subscriptionRepository.findByOrganisationId(any())).thenReturn(
                    Optional.of(Subscription.builder().planTier(Subscription.PlanTier.PRO).build()));

            orgAdminService.createOrganisation(new CreateOrgRequest("Co", "a@b.com", null, "CAN", "PRO"));

            assertThat(subCaptor.getValue().getPlanTier()).isEqualTo(Subscription.PlanTier.PRO);
        }

        @Test
        @DisplayName("falls back to ESSENTIALS when planTier is blank")
        void fallsBackToEssentials() {
            when(organisationRepository.existsByNom(any())).thenReturn(false);
            when(organisationRepository.save(any(Organisation.class))).thenAnswer(inv -> {
                Organisation o = inv.getArgument(0);
                return Organisation.builder()
                        .id(ORG_ID).nom(o.getNom()).status("ACTIVE").pays("CAN").build();
            });
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
            when(subscriptionRepository.save(subCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
            when(employeRepository.countByOrganisationId(any())).thenReturn(0L);
            when(userRepository.countByOrganisationId(any())).thenReturn(0L);
            when(subscriptionRepository.findByOrganisationId(any())).thenReturn(
                    Optional.of(Subscription.builder().planTier(Subscription.PlanTier.ESSENTIALS).build()));

            orgAdminService.createOrganisation(new CreateOrgRequest("Co2", "b@c.com", null, "CAN", "  "));

            assertThat(subCaptor.getValue().getPlanTier()).isEqualTo(Subscription.PlanTier.ESSENTIALS);
        }

        @Test
        @DisplayName("throws CONFLICT when organisation name already exists")
        void throwsConflictWhenNameTaken() {
            when(organisationRepository.existsByNom("Taken")).thenReturn(true);

            assertThatThrownBy(() -> orgAdminService.createOrganisation(
                    new CreateOrgRequest("Taken", "x@x.com", null, "CAN", null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("throws CONFLICT when admin email already exists")
        void throwsConflictWhenEmailTaken() {
            when(organisationRepository.existsByNom("Fresh")).thenReturn(false);
            when(organisationRepository.save(any(Organisation.class))).thenAnswer(inv -> {
                Organisation o = inv.getArgument(0);
                return Organisation.builder().id("new-id").nom(o.getNom()).status("ACTIVE").build();
            });
            when(userRepository.existsByEmail("taken@x.com")).thenReturn(true);

            assertThatThrownBy(() -> orgAdminService.createOrganisation(
                    new CreateOrgRequest("Fresh", "taken@x.com", null, "CAN", null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);

            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("email failure does not roll back organisation creation")
        void emailFailureDoesNotRollback() {
            when(organisationRepository.existsByNom(any())).thenReturn(false);
            when(organisationRepository.save(any(Organisation.class))).thenAnswer(inv -> {
                Organisation o = inv.getArgument(0);
                return Organisation.builder()
                        .id(ORG_ID).nom(o.getNom()).status("ACTIVE").pays("CAN").build();
            });
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(employeRepository.countByOrganisationId(any())).thenReturn(0L);
            when(userRepository.countByOrganisationId(any())).thenReturn(0L);
            when(subscriptionRepository.findByOrganisationId(any())).thenReturn(
                    Optional.of(Subscription.builder().planTier(Subscription.PlanTier.ESSENTIALS).build()));
            doThrow(new RuntimeException("SMTP down"))
                    .when(emailService).sendAdminInvitationEmail(any(), any(), any());

            // Must not throw even though email service throws
            OrgSummaryResponse result = orgAdminService.createOrganisation(
                    new CreateOrgRequest("MailFail", "f@f.com", null, "CAN", null));

            assertThat(result).isNotNull();
            verify(subscriptionRepository).save(any(Subscription.class));
        }
    }

    // ── deleteOrganisation ───────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteOrganisation() — cascade delete order")
    class DeleteOrganisation {

        @Test
        @DisplayName("deletes leaf tables before employees (dependency order)")
        void cascadeDeletesInCorrectOrder() {
            stubOrg();

            orgAdminService.deleteOrganisation(ORG_ID);

            InOrder inOrder = inOrder(
                    pointageRepository, pointageCodeRepository, creneauAssigneRepository,
                    demandeCongeRepository, banqueCongeRepository, absenceImprevueRepository,
                    pauseRepository, exigenceRepository, employeRepository);
            inOrder.verify(pointageRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(pointageCodeRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(creneauAssigneRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(demandeCongeRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(banqueCongeRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(absenceImprevueRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(pauseRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(exigenceRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(employeRepository).deleteByOrganisationId(ORG_ID);
        }

        @Test
        @DisplayName("deletes config tables after employees and before org root")
        void deletesConfigTablesBeforeOrgRoot() {
            stubOrg();

            orgAdminService.deleteOrganisation(ORG_ID);

            InOrder inOrder = inOrder(
                    employeRepository, siteRepository, roleRepository, typeCongeRepository,
                    jourFerieRepository, parametresRepository, userRepository,
                    featureFlagRepository, subscriptionRepository, announcementRepository,
                    organisationRepository);
            inOrder.verify(employeRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(siteRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(roleRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(typeCongeRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(jourFerieRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(parametresRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(userRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(featureFlagRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(subscriptionRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(announcementRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(organisationRepository).delete(any(Organisation.class));
        }

        @Test
        @DisplayName("calls organisationRepository.delete last")
        void organisationDeletedLast() {
            stubOrg();

            orgAdminService.deleteOrganisation(ORG_ID);

            InOrder inOrder = inOrder(subscriptionRepository, announcementRepository, organisationRepository);
            inOrder.verify(subscriptionRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(announcementRepository).deleteByOrganisationId(ORG_ID);
            inOrder.verify(organisationRepository).delete(any(Organisation.class));
        }

        @Test
        @DisplayName("throws 404 when organisation does not exist")
        void throws404WhenNotFound() {
            when(organisationRepository.findById("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orgAdminService.deleteOrganisation("ghost"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);

            verify(organisationRepository, never()).delete(any());
        }
    }

    // ── updateOrgStatus ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateOrgStatus()")
    class UpdateOrgStatus {

        @Test
        @DisplayName("ACTIVE status is accepted and persisted")
        void acceptsActiveStatus() {
            stubOrg();
            stubSubscription();
            when(employeRepository.countByOrganisationId(ORG_ID)).thenReturn(0L);
            when(userRepository.countByOrganisationId(ORG_ID)).thenReturn(0L);

            OrgSummaryResponse result = orgAdminService.updateOrgStatus(ORG_ID, "ACTIVE");

            assertThat(result.status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("SUSPENDED status is accepted and persisted")
        void acceptsSuspendedStatus() {
            stubOrg();
            stubSubscription();
            when(employeRepository.countByOrganisationId(ORG_ID)).thenReturn(0L);
            when(userRepository.countByOrganisationId(ORG_ID)).thenReturn(0L);

            OrgSummaryResponse result = orgAdminService.updateOrgStatus(ORG_ID, "SUSPENDED");

            ArgumentCaptor<Organisation> cap = ArgumentCaptor.forClass(Organisation.class);
            verify(organisationRepository).save(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo("SUSPENDED");
        }

        @Test
        @DisplayName("normalises lowercase input to uppercase")
        void normalisesLowercaseToUppercase() {
            stubOrg();
            stubSubscription();
            when(employeRepository.countByOrganisationId(ORG_ID)).thenReturn(0L);
            when(userRepository.countByOrganisationId(ORG_ID)).thenReturn(0L);

            orgAdminService.updateOrgStatus(ORG_ID, "active");

            ArgumentCaptor<Organisation> cap = ArgumentCaptor.forClass(Organisation.class);
            verify(organisationRepository).save(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("throws BAD_REQUEST for unknown status value")
        void throwsBadRequestForUnknownStatus() {
            assertThatThrownBy(() -> orgAdminService.updateOrgStatus(ORG_ID, "DELETED"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("throws BAD_REQUEST when status is blank")
        void throwsBadRequestWhenBlank() {
            assertThatThrownBy(() -> orgAdminService.updateOrgStatus(ORG_ID, "  "))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("throws BAD_REQUEST when status is null")
        void throwsBadRequestWhenNull() {
            assertThatThrownBy(() -> orgAdminService.updateOrgStatus(ORG_ID, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── updateOrgPays ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateOrgPays()")
    class UpdateOrgPays {

        @Test
        @DisplayName("saves updated pays on the entity")
        void savesPays() {
            stubOrg();
            stubSubscription();
            when(employeRepository.countByOrganisationId(ORG_ID)).thenReturn(0L);
            when(userRepository.countByOrganisationId(ORG_ID)).thenReturn(0L);

            orgAdminService.updateOrgPays(ORG_ID, "MDG");

            ArgumentCaptor<Organisation> cap = ArgumentCaptor.forClass(Organisation.class);
            verify(organisationRepository).save(cap.capture());
            assertThat(cap.getValue().getPays()).isEqualTo("MDG");
        }
    }

    // ── getOrgIdentifications / updateOrgIdentifications ──────────────────────

    @Nested
    @DisplayName("getOrgIdentifications()")
    class GetOrgIdentifications {

        @Test
        @DisplayName("returns OrgIdentificationsResponse with all identification fields")
        void returnsIdentificationsResponse() {
            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom(ORG_NOM).pays("CAN")
                    .province("QC").businessNumber("BN123").provincialId("PI456")
                    .nif(null).stat(null)
                    .verificationStatus("UNVERIFIED").build();
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            OrgIdentificationsResponse result = orgAdminService.getOrgIdentifications(ORG_ID);

            assertThat(result.id()).isEqualTo(ORG_ID);
            assertThat(result.province()).isEqualTo("QC");
            assertThat(result.businessNumber()).isEqualTo("BN123");
            assertThat(result.provincialId()).isEqualTo("PI456");
            assertThat(result.verificationStatus()).isEqualTo("UNVERIFIED");
        }
    }

    @Nested
    @DisplayName("updateOrgIdentifications()")
    class UpdateOrgIdentifications {

        @Test
        @DisplayName("saves all identification fields and returns updated response")
        void savesAllFields() {
            stubOrg();
            UpdateOrgIdentificationsRequest request = new UpdateOrgIdentificationsRequest(
                    "QC", "BN-987", "PI-654", "NIF-111", "STAT-222");

            ArgumentCaptor<Organisation> cap = ArgumentCaptor.forClass(Organisation.class);

            OrgIdentificationsResponse result = orgAdminService.updateOrgIdentifications(ORG_ID, request);

            verify(organisationRepository).save(cap.capture());
            assertThat(cap.getValue().getProvince()).isEqualTo("QC");
            assertThat(cap.getValue().getBusinessNumber()).isEqualTo("BN-987");
            assertThat(cap.getValue().getNif()).isEqualTo("NIF-111");
            assertThat(cap.getValue().getStat()).isEqualTo("STAT-222");
        }
    }

    // ── updateOrgVerificationStatus ───────────────────────────────────────────

    @Nested
    @DisplayName("updateOrgVerificationStatus()")
    class UpdateOrgVerificationStatus {

        @Test
        @DisplayName("sets verification status, verifiedBy, verifiedAt and note")
        void setsAllVerificationFields() {
            stubOrg();

            ArgumentCaptor<Organisation> cap = ArgumentCaptor.forClass(Organisation.class);

            orgAdminService.updateOrgVerificationStatus(ORG_ID, "VERIFIED", "Docs OK", "superadmin@schedy.io");

            verify(organisationRepository).save(cap.capture());
            Organisation saved = cap.getValue();
            assertThat(saved.getVerificationStatus()).isEqualTo("VERIFIED");
            assertThat(saved.getVerifiedBy()).isEqualTo("superadmin@schedy.io");
            assertThat(saved.getVerificationNote()).isEqualTo("Docs OK");
            assertThat(saved.getVerifiedAt()).isNotNull();
        }
    }

    // ── resendAdminInvitation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("resendAdminInvitation()")
    class ResendAdminInvitation {

        @Test
        @DisplayName("resets invitation token and saves user")
        void resetsTokenAndSavesUser() {
            stubOrg();
            User admin = User.builder()
                    .id(1L).email("admin@acme.com")
                    .organisationId(ORG_ID)
                    .role(User.UserRole.ADMIN)
                    .password("old-hash")
                    .passwordSet(true).build();
            when(userRepository.findFirstByOrganisationIdAndRole(ORG_ID, User.UserRole.ADMIN))
                    .thenReturn(Optional.of(admin));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

            orgAdminService.resendAdminInvitation(ORG_ID);

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(cap.capture());
            User saved = cap.getValue();
            assertThat(saved.getInvitationToken()).isNotNull();
            assertThat(saved.getInvitationTokenExpiresAt()).isNotNull();
            assertThat(saved.isPasswordSet()).isFalse();
        }

        @Test
        @DisplayName("throws 404 when no admin user found for the organisation")
        void throws404WhenNoAdmin() {
            stubOrg();
            when(userRepository.findFirstByOrganisationIdAndRole(ORG_ID, User.UserRole.ADMIN))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orgAdminService.resendAdminInvitation(ORG_ID))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("email failure does not surface — invitation token is already saved")
        void emailFailureDoesNotSurface() {
            stubOrg();
            User admin = User.builder()
                    .id(2L).email("a@b.com")
                    .organisationId(ORG_ID).role(User.UserRole.ADMIN)
                    .password("hash").passwordSet(true).build();
            when(userRepository.findFirstByOrganisationIdAndRole(ORG_ID, User.UserRole.ADMIN))
                    .thenReturn(Optional.of(admin));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(passwordEncoder.encode(anyString())).thenReturn("h");
            doThrow(new RuntimeException("SMTP down"))
                    .when(emailService).sendAdminInvitationEmail(any(), any(), any());

            // Must not throw
            orgAdminService.resendAdminInvitation(ORG_ID);

            verify(userRepository).save(any(User.class));
        }
    }

    // ── requireOrg ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("requireOrg()")
    class RequireOrg {

        @Test
        @DisplayName("returns organisation when it exists")
        void returnsOrgWhenFound() {
            stubOrg();

            Organisation result = orgAdminService.requireOrg(ORG_ID);

            assertThat(result.getId()).isEqualTo(ORG_ID);
        }

        @Test
        @DisplayName("throws 404 ResponseStatusException when organisation is not found")
        void throws404WhenNotFound() {
            when(organisationRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orgAdminService.requireOrg("missing"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
