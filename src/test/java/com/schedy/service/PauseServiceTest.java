package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.request.PauseCreateRequest;
import com.schedy.entity.MethodePointage;
import com.schedy.entity.Parametres;
import com.schedy.entity.Pause;
import com.schedy.entity.Pointage;
import com.schedy.entity.SourcePause;
import com.schedy.entity.StatutPause;
import com.schedy.entity.StatutPointage;
import com.schedy.entity.TypePause;
import com.schedy.entity.TypePointage;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.ParametresRepository;
import com.schedy.repository.PauseRepository;
import com.schedy.repository.PointageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PauseService unit tests")
class PauseServiceTest {

    @Mock private PauseRepository pauseRepository;
    @Mock private PointageRepository pointageRepository;
    @Mock private ParametresRepository parametresRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private TenantContext tenantContext;

    @InjectMocks private PauseService pauseService;

    private static final String ORG_ID = "org-123";
    private static final String EMPLOYE_ID = "emp-456";
    private static final String SITE_ID = "site-789";

    @BeforeEach
    void setUp() {
        // Defensive: ensure no SecurityContext state leaks from a previously-run
        // test class in the same Surefire fork (AuthService/AuthControllerTest
        // leave "alice@example.com" in the thread-local).
        SecurityContextHolder.clearContext();
        lenient().when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "manager@schedy.test",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))
                )
        );
    }

    @org.junit.jupiter.api.AfterEach
    void tearDownSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Pointage pointage(String id, TypePointage type, OffsetDateTime horodatage, String siteId) {
        return Pointage.builder()
                .id(id)
                .employeId(EMPLOYE_ID)
                .type(type)
                .horodatage(horodatage)
                .methode(MethodePointage.web)
                .statut(StatutPointage.valide)
                .organisationId(ORG_ID)
                .siteId(siteId)
                .build();
    }

    private Parametres params(int minMinutes, int maxMinutes) {
        return Parametres.builder()
                .id(1L)
                .organisationId(ORG_ID)
                .fenetrePauseMinMinutes(minMinutes)
                .fenetrePauseMaxMinutes(maxMinutes)
                .build();
    }

    // -------------------------------------------------------------------------
    // creer() — manual entry
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("creer() — manager manual entry")
    class Creer {

        @Test
        @DisplayName("persists MANUEL/CONFIRME pause with computed duration")
        void creer_validRequest_persistsManuelConfirme() {
            OffsetDateTime debut = OffsetDateTime.of(2026, 4, 15, 12, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime fin = OffsetDateTime.of(2026, 4, 15, 12, 45, 0, 0, ZoneOffset.UTC);
            PauseCreateRequest req = new PauseCreateRequest(
                    EMPLOYE_ID, SITE_ID, debut, fin, TypePause.REPAS, false);
            when(pauseRepository.save(any(Pause.class))).thenAnswer(inv -> inv.getArgument(0));

            Pause saved = pauseService.creer(req);

            assertThat(saved.getEmployeId()).isEqualTo(EMPLOYE_ID);
            assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
            assertThat(saved.getDureeMinutes()).isEqualTo(45);
            assertThat(saved.getType()).isEqualTo(TypePause.REPAS);
            assertThat(saved.getSource()).isEqualTo(SourcePause.MANUEL);
            assertThat(saved.getStatut()).isEqualTo(StatutPause.CONFIRME);
            assertThat(saved.getConfirmeParId()).isEqualTo("manager@schedy.test");
            assertThat(saved.getConfirmeAt()).isNotNull();
        }

        @Test
        @DisplayName("rejects when fin is before or equal to debut")
        void creer_finBeforeDebut_throwsBusinessRule() {
            OffsetDateTime t = OffsetDateTime.of(2026, 4, 15, 12, 0, 0, 0, ZoneOffset.UTC);
            PauseCreateRequest req = new PauseCreateRequest(
                    EMPLOYE_ID, null, t, t, TypePause.PAUSE, true);

            assertThatThrownBy(() -> pauseService.creer(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("apres le debut");
        }

        @Test
        @DisplayName("rejects when rounded duration is zero")
        void creer_subMinuteDuration_throwsBusinessRule() {
            OffsetDateTime debut = OffsetDateTime.of(2026, 4, 15, 12, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime fin = debut.plusSeconds(30);
            PauseCreateRequest req = new PauseCreateRequest(
                    EMPLOYE_ID, null, debut, fin, TypePause.PAUSE, true);

            assertThatThrownBy(() -> pauseService.creer(req))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    // -------------------------------------------------------------------------
    // detectFromPointage() — Layer 3 auto-detection
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("detectFromPointage() — Layer 3 auto-detection")
    class Detection {

        @Test
        @DisplayName("creates REPAS pause when gap is 45min (≥ threshold)")
        void detect_gap45min_createsRepas() {
            OffsetDateTime sortieTime = OffsetDateTime.of(2026, 4, 15, 12, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime entreeTime = sortieTime.plusMinutes(45);
            Pointage sortie = pointage("ptr-sortie", TypePointage.sortie, sortieTime, SITE_ID);
            Pointage entree = pointage("ptr-entree", TypePointage.entree, entreeTime, SITE_ID);

            when(pointageRepository
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            EMPLOYE_ID, SITE_ID, ORG_ID, entreeTime))
                    .thenReturn(Optional.of(sortie));
            when(pauseRepository.findByPointageEntreeIdAndPointageSortieId("ptr-entree", "ptr-sortie"))
                    .thenReturn(Optional.empty());
            when(parametresRepository.findBySiteIdAndOrganisationId(SITE_ID, ORG_ID))
                    .thenReturn(Optional.of(params(15, 90)));
            when(pauseRepository.save(any(Pause.class))).thenAnswer(inv -> inv.getArgument(0));

            pauseService.detectFromPointage(entree);

            ArgumentCaptor<Pause> captor = ArgumentCaptor.forClass(Pause.class);
            verify(pauseRepository).save(captor.capture());
            Pause saved = captor.getValue();
            assertThat(saved.getDureeMinutes()).isEqualTo(45);
            assertThat(saved.getType()).isEqualTo(TypePause.REPAS);
            assertThat(saved.getSource()).isEqualTo(SourcePause.DETECTION_AUTO);
            assertThat(saved.getStatut()).isEqualTo(StatutPause.DETECTE);
            assertThat(saved.isPayee()).isFalse();
            assertThat(saved.getPointageEntreeId()).isEqualTo("ptr-entree");
            assertThat(saved.getPointageSortieId()).isEqualTo("ptr-sortie");
            assertThat(saved.getSiteId()).isEqualTo(SITE_ID);
        }

        @Test
        @DisplayName("creates PAUSE when gap is 20min (below REPAS threshold)")
        void detect_gap20min_createsShortPause() {
            OffsetDateTime sortieTime = OffsetDateTime.of(2026, 4, 15, 10, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime entreeTime = sortieTime.plusMinutes(20);
            Pointage sortie = pointage("ptr-s", TypePointage.sortie, sortieTime, SITE_ID);
            Pointage entree = pointage("ptr-e", TypePointage.entree, entreeTime, SITE_ID);

            when(pointageRepository
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            EMPLOYE_ID, SITE_ID, ORG_ID, entreeTime))
                    .thenReturn(Optional.of(sortie));
            when(pauseRepository.findByPointageEntreeIdAndPointageSortieId("ptr-e", "ptr-s"))
                    .thenReturn(Optional.empty());
            when(parametresRepository.findBySiteIdAndOrganisationId(SITE_ID, ORG_ID))
                    .thenReturn(Optional.of(params(15, 90)));
            when(pauseRepository.save(any(Pause.class))).thenAnswer(inv -> inv.getArgument(0));

            pauseService.detectFromPointage(entree);

            ArgumentCaptor<Pause> captor = ArgumentCaptor.forClass(Pause.class);
            verify(pauseRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(TypePause.PAUSE);
            assertThat(captor.getValue().getDureeMinutes()).isEqualTo(20);
        }

        @Test
        @DisplayName("does nothing when gap is below detection window (10min < 15min)")
        void detect_gapTooShort_skips() {
            OffsetDateTime sortieTime = OffsetDateTime.of(2026, 4, 15, 10, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime entreeTime = sortieTime.plusMinutes(10);
            Pointage sortie = pointage("s", TypePointage.sortie, sortieTime, SITE_ID);
            Pointage entree = pointage("e", TypePointage.entree, entreeTime, SITE_ID);

            when(pointageRepository
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            EMPLOYE_ID, SITE_ID, ORG_ID, entreeTime))
                    .thenReturn(Optional.of(sortie));
            when(pauseRepository.findByPointageEntreeIdAndPointageSortieId("e", "s"))
                    .thenReturn(Optional.empty());
            when(parametresRepository.findBySiteIdAndOrganisationId(SITE_ID, ORG_ID))
                    .thenReturn(Optional.of(params(15, 90)));

            pauseService.detectFromPointage(entree);

            verify(pauseRepository, never()).save(any(Pause.class));
        }

        @Test
        @DisplayName("does nothing when gap exceeds detection window (120min > 90min)")
        void detect_gapTooLong_skips() {
            OffsetDateTime sortieTime = OffsetDateTime.of(2026, 4, 15, 10, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime entreeTime = sortieTime.plusMinutes(120);
            Pointage sortie = pointage("s", TypePointage.sortie, sortieTime, SITE_ID);
            Pointage entree = pointage("e", TypePointage.entree, entreeTime, SITE_ID);

            when(pointageRepository
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            EMPLOYE_ID, SITE_ID, ORG_ID, entreeTime))
                    .thenReturn(Optional.of(sortie));
            when(pauseRepository.findByPointageEntreeIdAndPointageSortieId("e", "s"))
                    .thenReturn(Optional.empty());
            when(parametresRepository.findBySiteIdAndOrganisationId(SITE_ID, ORG_ID))
                    .thenReturn(Optional.of(params(15, 90)));

            pauseService.detectFromPointage(entree);

            verify(pauseRepository, never()).save(any(Pause.class));
        }

        @Test
        @DisplayName("does nothing when previous pointage is also an entree (forgot sortie)")
        void detect_previousIsEntree_skips() {
            OffsetDateTime previousEntreeTime = OffsetDateTime.of(2026, 4, 15, 8, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime newEntreeTime = previousEntreeTime.plusMinutes(30);
            Pointage previousEntree = pointage("e1", TypePointage.entree, previousEntreeTime, SITE_ID);
            Pointage newEntree = pointage("e2", TypePointage.entree, newEntreeTime, SITE_ID);

            when(pointageRepository
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            EMPLOYE_ID, SITE_ID, ORG_ID, newEntreeTime))
                    .thenReturn(Optional.of(previousEntree));

            pauseService.detectFromPointage(newEntree);

            verify(pauseRepository, never()).save(any(Pause.class));
        }

        @Test
        @DisplayName("does nothing when no previous pointage exists")
        void detect_firstPointageOfDay_skips() {
            OffsetDateTime entreeTime = OffsetDateTime.of(2026, 4, 15, 9, 0, 0, 0, ZoneOffset.UTC);
            Pointage entree = pointage("e", TypePointage.entree, entreeTime, SITE_ID);

            when(pointageRepository
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            EMPLOYE_ID, SITE_ID, ORG_ID, entreeTime))
                    .thenReturn(Optional.empty());

            pauseService.detectFromPointage(entree);

            verify(pauseRepository, never()).save(any(Pause.class));
        }

        @Test
        @DisplayName("does nothing when pointage is a sortie (detection runs on entree only)")
        void detect_onSortie_skipsEntirely() {
            OffsetDateTime t = OffsetDateTime.of(2026, 4, 15, 12, 0, 0, 0, ZoneOffset.UTC);
            Pointage sortie = pointage("s", TypePointage.sortie, t, SITE_ID);

            pauseService.detectFromPointage(sortie);

            verify(pauseRepository, never()).save(any(Pause.class));
        }

        @Test
        @DisplayName("duplicate guard : skips when a pause already links the pair")
        void detect_duplicateExists_skips() {
            OffsetDateTime sortieTime = OffsetDateTime.of(2026, 4, 15, 12, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime entreeTime = sortieTime.plusMinutes(45);
            Pointage sortie = pointage("s", TypePointage.sortie, sortieTime, SITE_ID);
            Pointage entree = pointage("e", TypePointage.entree, entreeTime, SITE_ID);

            when(pointageRepository
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            EMPLOYE_ID, SITE_ID, ORG_ID, entreeTime))
                    .thenReturn(Optional.of(sortie));
            when(pauseRepository.findByPointageEntreeIdAndPointageSortieId("e", "s"))
                    .thenReturn(Optional.of(Pause.builder().id("existing").build()));

            pauseService.detectFromPointage(entree);

            verify(pauseRepository, never()).save(any(Pause.class));
        }

        @Test
        @DisplayName("uses default window (15/90) when no Parametres row exists")
        void detect_noParametres_usesDefaultWindow() {
            OffsetDateTime sortieTime = OffsetDateTime.of(2026, 4, 15, 10, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime entreeTime = sortieTime.plusMinutes(60);
            Pointage sortie = pointage("s", TypePointage.sortie, sortieTime, null);
            Pointage entree = pointage("e", TypePointage.entree, entreeTime, null);

            when(pointageRepository
                    .findTopByEmployeIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            EMPLOYE_ID, ORG_ID, entreeTime))
                    .thenReturn(Optional.of(sortie));
            when(pauseRepository.findByPointageEntreeIdAndPointageSortieId("e", "s"))
                    .thenReturn(Optional.empty());
            when(parametresRepository.findBySiteIdIsNullAndOrganisationId(ORG_ID))
                    .thenReturn(Optional.empty());
            when(pauseRepository.save(any(Pause.class))).thenAnswer(inv -> inv.getArgument(0));

            pauseService.detectFromPointage(entree);

            ArgumentCaptor<Pause> captor = ArgumentCaptor.forClass(Pause.class);
            verify(pauseRepository).save(captor.capture());
            assertThat(captor.getValue().getDureeMinutes()).isEqualTo(60);
            assertThat(captor.getValue().getType()).isEqualTo(TypePause.REPAS);
        }

        @Test
        @DisplayName("site-scoped lookup when pointage has siteId")
        void detect_siteScoped_usesSiteFinder() {
            OffsetDateTime sortieTime = OffsetDateTime.of(2026, 4, 15, 10, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime entreeTime = sortieTime.plusMinutes(30);
            Pointage sortie = pointage("s", TypePointage.sortie, sortieTime, SITE_ID);
            Pointage entree = pointage("e", TypePointage.entree, entreeTime, SITE_ID);

            when(pointageRepository
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            EMPLOYE_ID, SITE_ID, ORG_ID, entreeTime))
                    .thenReturn(Optional.of(sortie));
            when(pauseRepository.findByPointageEntreeIdAndPointageSortieId("e", "s"))
                    .thenReturn(Optional.empty());
            when(parametresRepository.findBySiteIdAndOrganisationId(SITE_ID, ORG_ID))
                    .thenReturn(Optional.of(params(15, 90)));
            when(pauseRepository.save(any(Pause.class))).thenAnswer(inv -> inv.getArgument(0));

            pauseService.detectFromPointage(entree);

            verify(pointageRepository)
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            EMPLOYE_ID, SITE_ID, ORG_ID, entreeTime);
            verify(pointageRepository, never())
                    .findTopByEmployeIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            any(), any(), any());
        }

        @Test
        @DisplayName("swallows unexpected exceptions so pointage commit is never blocked")
        void detect_repositoryThrows_doesNotPropagate() {
            OffsetDateTime t = OffsetDateTime.of(2026, 4, 15, 12, 0, 0, 0, ZoneOffset.UTC);
            Pointage entree = pointage("e", TypePointage.entree, t, SITE_ID);

            when(pointageRepository
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            EMPLOYE_ID, SITE_ID, ORG_ID, t))
                    .thenThrow(new RuntimeException("db outage"));

            // must not throw
            pauseService.detectFromPointage(entree);

            verify(pauseRepository, never()).save(any(Pause.class));
        }
    }

    // -------------------------------------------------------------------------
    // confirmer() / contester() — state transitions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("confirmer() / contester() — workflow")
    class Workflow {

        @Test
        @DisplayName("confirmer flips DETECTE → CONFIRME and stamps manager")
        void confirmer_detecte_transitionsToConfirme() {
            Pause detected = Pause.builder()
                    .id("p1")
                    .organisationId(ORG_ID)
                    .statut(StatutPause.DETECTE)
                    .build();
            when(pauseRepository.findByIdAndOrganisationId("p1", ORG_ID))
                    .thenReturn(Optional.of(detected));
            when(pauseRepository.save(any(Pause.class))).thenAnswer(inv -> inv.getArgument(0));

            Pause result = pauseService.confirmer("p1");

            assertThat(result.getStatut()).isEqualTo(StatutPause.CONFIRME);
            assertThat(result.getConfirmeParId()).isEqualTo("manager@schedy.test");
            assertThat(result.getConfirmeAt()).isNotNull();
        }

        @Test
        @DisplayName("confirmer rejects when pause is already CONFIRME")
        void confirmer_alreadyConfirme_throws() {
            Pause confirmed = Pause.builder()
                    .id("p1")
                    .organisationId(ORG_ID)
                    .statut(StatutPause.CONFIRME)
                    .build();
            when(pauseRepository.findByIdAndOrganisationId("p1", ORG_ID))
                    .thenReturn(Optional.of(confirmed));

            assertThatThrownBy(() -> pauseService.confirmer("p1"))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("contester flips DETECTE → CONTESTE with motif")
        void contester_detecte_transitionsToConteste() {
            Pause detected = Pause.builder()
                    .id("p1")
                    .organisationId(ORG_ID)
                    .statut(StatutPause.DETECTE)
                    .build();
            when(pauseRepository.findByIdAndOrganisationId("p1", ORG_ID))
                    .thenReturn(Optional.of(detected));
            when(pauseRepository.save(any(Pause.class))).thenAnswer(inv -> inv.getArgument(0));

            Pause result = pauseService.contester("p1", "ce n'etait pas une pause");

            assertThat(result.getStatut()).isEqualTo(StatutPause.CONTESTE);
            assertThat(result.getMotifContestation()).isEqualTo("ce n'etait pas une pause");
        }
    }

    // -------------------------------------------------------------------------
    // delete()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("deletes an existing pause scoped to the caller's org")
        void delete_existing_removesFromRepository() {
            Pause existing = Pause.builder()
                    .id("p1")
                    .organisationId(ORG_ID)
                    .employeId(EMPLOYE_ID)
                    .statut(StatutPause.CONFIRME)
                    .build();
            when(pauseRepository.findByIdAndOrganisationId("p1", ORG_ID))
                    .thenReturn(Optional.of(existing));

            pauseService.delete("p1");

            verify(pauseRepository).delete(existing);
        }

        @Test
        @DisplayName("throws when pause is not found in the caller's org")
        void delete_notFound_throws() {
            when(pauseRepository.findByIdAndOrganisationId("missing", ORG_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> pauseService.delete("missing"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
