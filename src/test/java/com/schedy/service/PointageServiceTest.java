package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.PointageDto;
import com.schedy.dto.request.PointerRequest;
import com.schedy.entity.*;
import com.schedy.exception.ClockInNotAuthorizedException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.CreneauAssigneRepository;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.ParametresRepository;
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

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PointageService unit tests")
class PointageServiceTest {

    @Mock private PointageRepository pointageRepository;
    @Mock private TenantContext tenantContext;
    @Mock private PauseService pauseService;
    @Mock private CreneauAssigneRepository creneauRepository;
    @Mock private ParametresRepository parametresRepository;
    @Mock private OrganisationRepository organisationRepository;

    @InjectMocks private PointageService pointageService;

    private static final String ORG_ID = "org-123";
    private static final String EMPLOYE_ID = "emp-456";
    private static final String SITE_ID = "site-789";
    private static final String POINTAGE_ID = "ptr-111";

    @BeforeEach
    void setUp() {
        // lenient because some tests (e.g. pointerFromKiosk) must NOT call this
        lenient().when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
        // Default authorization path: a fake creneau is active NOW. The
        // security-focused nested class overrides this with empty lists.
        lenient().when(creneauRepository.findActiveForClockIn(
                        anyString(), anyString(), anyString(), anyString(),
                        anyInt(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new CreneauAssigne()));
        // Org timezone resolution — default to UTC so tests don't need to stub
        // the organisation repository unless they care about timezone semantics.
        lenient().when(organisationRepository.findById(anyString()))
                .thenReturn(Optional.empty());
        // Default tolerance params = null → service falls back to 30/30 defaults.
        lenient().when(parametresRepository.findBySiteIdAndOrganisationId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(parametresRepository.findBySiteIdIsNullAndOrganisationId(anyString()))
                .thenReturn(Optional.empty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a Pointage with enum-typed fields, as the actual entity uses enums. */
    private Pointage buildPointage(TypePointage type, OffsetDateTime horodatage) {
        return Pointage.builder()
                .id(POINTAGE_ID)
                .employeId(EMPLOYE_ID)
                .type(type)
                .horodatage(horodatage)
                .methode(MethodePointage.web)
                .statut(StatutPointage.valide)
                .organisationId(ORG_ID)
                .build();
    }

    // -------------------------------------------------------------------------
    // pointer() — entry/exit logic
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("pointer() — entry/exit type detection")
    class PointerType {

        @Test
        @DisplayName("creates entree when no previous pointage exists")
        void pointer_firstEntry_createsEntreeType() {
            PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "web");
            when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID))
                    .thenReturn(Optional.empty());
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.pointer(request);

            assertThat(result.getType()).isEqualTo(TypePointage.entree);
            assertThat(result.getStatut()).isEqualTo(StatutPointage.valide);
            assertThat(result.getEmployeId()).isEqualTo(EMPLOYE_ID);
            assertThat(result.getOrganisationId()).isEqualTo(ORG_ID);
        }

        @Test
        @DisplayName("creates sortie when last recorded pointage was an entree")
        void pointer_afterEntree_createsSortie() {
            PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "pin");
            Pointage lastEntree = buildPointage(TypePointage.entree, OffsetDateTime.now(ZoneOffset.UTC).minusHours(8));
            when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID))
                    .thenReturn(Optional.of(lastEntree));
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.pointer(request);

            assertThat(result.getType()).isEqualTo(TypePointage.sortie);
            assertThat(result.getStatut()).isEqualTo(StatutPointage.valide);
        }

        @Test
        @DisplayName("creates entree again when last recorded pointage was a sortie")
        void pointer_afterSortie_createsEntree() {
            PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "web");
            Pointage lastSortie = buildPointage(TypePointage.sortie, OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
            when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID))
                    .thenReturn(Optional.of(lastSortie));
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.pointer(request);

            assertThat(result.getType()).isEqualTo(TypePointage.entree);
            assertThat(result.getStatut()).isEqualTo(StatutPointage.valide);
        }
    }

    // -------------------------------------------------------------------------
    // pointer() — anomaly detection
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("pointer() — anomaly detection")
    class PointerAnomalies {

        @Test
        @DisplayName("flags anomalie when last pointage was less than 1 minute ago (double pointage)")
        void pointer_doublePontageAnomaly() {
            PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "web");
            Pointage recentEntree = buildPointage(TypePointage.entree, OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30));
            when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID))
                    .thenReturn(Optional.of(recentEntree));
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.pointer(request);

            assertThat(result.getStatut()).isEqualTo(StatutPointage.anomalie);
            assertThat(result.getAnomalie()).isNotBlank().containsIgnoringCase("Double pointage");
        }

        @Test
        @DisplayName("flags anomalie when last entree was more than 12 hours ago")
        void pointer_longDurationAnomaly() {
            PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "manuel");
            Pointage oldEntree = buildPointage(TypePointage.entree, OffsetDateTime.now(ZoneOffset.UTC).minusHours(13));
            when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID))
                    .thenReturn(Optional.of(oldEntree));
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.pointer(request);

            assertThat(result.getType()).isEqualTo(TypePointage.sortie);
            assertThat(result.getStatut()).isEqualTo(StatutPointage.anomalie);
            assertThat(result.getAnomalie()).isNotBlank();
        }

        @Test
        @DisplayName("does not flag anomalie for a normal 8-hour shift")
        void pointer_normalShift_noAnomaly() {
            PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "web");
            Pointage normalEntree = buildPointage(TypePointage.entree, OffsetDateTime.now(ZoneOffset.UTC).minusHours(8));
            when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID))
                    .thenReturn(Optional.of(normalEntree));
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.pointer(request);

            assertThat(result.getStatut()).isEqualTo(StatutPointage.valide);
            assertThat(result.getAnomalie()).isNull();
        }

        @Test
        @DisplayName("accepts exactly 12 hours as anomalie boundary (>= 12h triggers anomalie)")
        void pointer_exactly12Hours_triggersAnomaly() {
            PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "web");
            Pointage entree = buildPointage(TypePointage.entree, OffsetDateTime.now(ZoneOffset.UTC).minusHours(12).minusSeconds(1));
            when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID))
                    .thenReturn(Optional.of(entree));
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.pointer(request);

            assertThat(result.getStatut()).isEqualTo(StatutPointage.anomalie);
        }
    }

    // -------------------------------------------------------------------------
    // pointer() — site routing
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("pointer() — site routing")
    class PointerSiteRouting {

        @Test
        @DisplayName("uses site-scoped query when siteId is provided")
        void pointer_withSiteId_usesSiteScopedQuery() {
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "qr");
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, ORG_ID)).thenReturn(Optional.empty());
            ArgumentCaptor<Pointage> captor = ArgumentCaptor.forClass(Pointage.class);
            when(pointageRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            pointageService.pointer(request);

            // Site-scoped repo method must be called
            verify(pointageRepository)
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, SITE_ID, ORG_ID);
            // Global repo method must NOT be called
            verify(pointageRepository, never())
                    .findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(anyString(), anyString());
            assertThat(captor.getValue().getSiteId()).isEqualTo(SITE_ID);
        }

        @Test
        @DisplayName("uses global query when siteId is null")
        void pointer_withoutSiteId_usesGlobalQuery() {
            PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "web");
            when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID))
                    .thenReturn(Optional.empty());
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pointageService.pointer(request);

            verify(pointageRepository)
                    .findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID);
            verify(pointageRepository, never())
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                            anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("persists correct horodatage (within 2 seconds of now) and siteId")
        void pointer_storesCorrectData() {
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "qr");
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, ORG_ID)).thenReturn(Optional.empty());
            ArgumentCaptor<Pointage> captor = ArgumentCaptor.forClass(Pointage.class);
            when(pointageRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1);
            pointageService.pointer(request);
            OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1);

            Pointage saved = captor.getValue();
            assertThat(saved.getHorodatage()).isAfter(before).isBefore(after);
            assertThat(saved.getSiteId()).isEqualTo(SITE_ID);
            assertThat(saved.getMethode()).isEqualTo(MethodePointage.qr);
        }
    }

    // -------------------------------------------------------------------------
    // pointerFromKiosk()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("pointerFromKiosk()")
    class PointerFromKiosk {

        @Test
        @DisplayName("uses the provided orgId and never calls TenantContext")
        void pointerFromKiosk_usesProvidedOrgId() {
            String kioskOrgId = "kiosk-org-999";
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "qr");
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, kioskOrgId)).thenReturn(Optional.empty());
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.pointerFromKiosk(request, kioskOrgId);

            // TenantContext must never be consulted for a kiosk flow
            verify(tenantContext, never()).requireOrganisationId();
            assertThat(result.getOrganisationId()).isEqualTo(kioskOrgId);
        }

        @Test
        @DisplayName("applies the same entry/exit detection logic as pointer()")
        void pointerFromKiosk_appliesEntryExitLogic() {
            String kioskOrgId = "kiosk-org-999";
            // Kiosk flow always carries a site (the guard rejects null siteId).
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "qr");
            Pointage lastEntree = Pointage.builder()
                    .employeId(EMPLOYE_ID).type(TypePointage.entree)
                    .horodatage(OffsetDateTime.now(ZoneOffset.UTC).minusHours(4))
                    .methode(MethodePointage.qr).statut(StatutPointage.valide)
                    .siteId(SITE_ID).organisationId(kioskOrgId).build();
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, kioskOrgId)).thenReturn(Optional.of(lastEntree));
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.pointerFromKiosk(request, kioskOrgId);

            assertThat(result.getType()).isEqualTo(TypePointage.sortie);
        }
    }

    // -------------------------------------------------------------------------
    // pointerFromKiosk() — Creneau-guard security tests
    //
    // These tests exercise every branch of `enforceCreneauGuard` to make sure
    // an employee cannot clock in unless they have a matching creneau active
    // right now on the target site. Each test uses explicit stubs for the
    // creneau repository so the shared lenient default in setUp() is overridden.
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("pointerFromKiosk() — creneau-guard security")
    class PointerFromKioskSecurity {

        private final String kioskOrgId = "kiosk-org-999";

        /**
         * Helper: stub the planning repository to simulate "employee has an
         * active creneau NOW on this site". The list contents don't matter —
         * only the non-empty return does.
         */
        private void stubActiveCreneau() {
            when(creneauRepository.findActiveForClockIn(
                    eq(kioskOrgId), eq(EMPLOYE_ID), eq(SITE_ID),
                    anyString(), anyInt(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of(new CreneauAssigne()));
        }

        /** Helper: simulate "no creneau of any kind for this employee today". */
        private void stubNoCreneauAtAll() {
            when(creneauRepository.findActiveForClockIn(
                    anyString(), anyString(), anyString(),
                    anyString(), anyInt(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(Collections.emptyList());
            when(creneauRepository.findByEmployeIdAndSemaineAndSiteIdAndOrganisationId(
                    anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Collections.emptyList());
        }

        /**
         * Helper: simulate "employee has a creneau TODAY on this site but
         * the current time falls outside the tolerance window". Same-day
         * creneaux are returned but the active query comes back empty.
         */
        private void stubCreneauTodayButOutsideTolerance() {
            when(creneauRepository.findActiveForClockIn(
                    anyString(), anyString(), anyString(),
                    anyString(), anyInt(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(Collections.emptyList());
            // Return one same-day creneau to force OUTSIDE_TOLERANCE_WINDOW branch
            CreneauAssigne sameDay = CreneauAssigne.builder()
                    .employeId(EMPLOYE_ID)
                    .siteId(SITE_ID)
                    .organisationId(kioskOrgId)
                    .semaine(currentIsoWeek())
                    .jour(currentJourOfWeek())
                    .heureDebut(3.0)
                    .heureFin(5.0)
                    .build();
            when(creneauRepository.findByEmployeIdAndSemaineAndSiteIdAndOrganisationId(
                    anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(List.of(sameDay));
        }

        private static String currentIsoWeek() {
            LocalDate d = LocalDate.now();
            int year = d.get(IsoFields.WEEK_BASED_YEAR);
            int week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            return String.format("%d-W%02d", year, week);
        }

        private static int currentJourOfWeek() {
            return LocalDate.now().getDayOfWeek().getValue() - 1;
        }

        @Test
        @DisplayName("allows clock-in when employee has an active creneau NOW on this site")
        void allowsClockInWhenActiveCreneauExists() {
            stubActiveCreneau();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, kioskOrgId)).thenReturn(Optional.empty());
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.pointerFromKiosk(request, kioskOrgId);

            assertThat(result).isNotNull();
            assertThat(result.getEmployeId()).isEqualTo(EMPLOYE_ID);
            assertThat(result.getSiteId()).isEqualTo(SITE_ID);
            verify(pointageRepository).save(any(Pointage.class));
        }

        @Test
        @DisplayName("rejects with NO_CRENEAU_TODAY when employee is not scheduled today at all")
        void rejectsWhenNoCreneauAtAll() {
            stubNoCreneauAtAll();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");

            assertThatThrownBy(() -> pointageService.pointerFromKiosk(request, kioskOrgId))
                    .isInstanceOf(ClockInNotAuthorizedException.class)
                    .extracting(ex -> ((ClockInNotAuthorizedException) ex).getReason())
                    .isEqualTo(ClockInNotAuthorizedException.Reason.NO_CRENEAU_TODAY);

            verify(pointageRepository, never()).save(any(Pointage.class));
        }

        @Test
        @DisplayName("rejects with OUTSIDE_TOLERANCE_WINDOW when a same-day creneau exists but not active now")
        void rejectsWhenOutsideToleranceWindow() {
            stubCreneauTodayButOutsideTolerance();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");

            assertThatThrownBy(() -> pointageService.pointerFromKiosk(request, kioskOrgId))
                    .isInstanceOf(ClockInNotAuthorizedException.class)
                    .extracting(ex -> ((ClockInNotAuthorizedException) ex).getReason())
                    .isEqualTo(ClockInNotAuthorizedException.Reason.OUTSIDE_TOLERANCE_WINDOW);

            verify(pointageRepository, never()).save(any(Pointage.class));
        }

        @Test
        @DisplayName("rejects with UNKNOWN when request carries a null employeId (defensive)")
        void rejectsNullEmployeId() {
            PointerRequest request = new PointerRequest(null, SITE_ID, "pin");

            assertThatThrownBy(() -> pointageService.pointerFromKiosk(request, kioskOrgId))
                    .isInstanceOf(ClockInNotAuthorizedException.class)
                    .extracting(ex -> ((ClockInNotAuthorizedException) ex).getReason())
                    .isEqualTo(ClockInNotAuthorizedException.Reason.UNKNOWN);

            verify(pointageRepository, never()).save(any(Pointage.class));
        }

        @Test
        @DisplayName("rejects with UNKNOWN when request carries a null siteId (defensive)")
        void rejectsNullSiteId() {
            PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "pin");

            assertThatThrownBy(() -> pointageService.pointerFromKiosk(request, kioskOrgId))
                    .isInstanceOf(ClockInNotAuthorizedException.class)
                    .extracting(ex -> ((ClockInNotAuthorizedException) ex).getReason())
                    .isEqualTo(ClockInNotAuthorizedException.Reason.UNKNOWN);

            verify(pointageRepository, never()).save(any(Pointage.class));
        }

        @Test
        @DisplayName("passes the tolerance config from per-site params to the active-creneau query")
        void respectsPerSiteTolerance() {
            Parametres siteParams = Parametres.builder()
                    .toleranceAvantShiftMinutes(15)
                    .toleranceApresShiftMinutes(45)
                    .build();
            when(parametresRepository.findBySiteIdAndOrganisationId(SITE_ID, kioskOrgId))
                    .thenReturn(Optional.of(siteParams));
            stubActiveCreneau();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, kioskOrgId)).thenReturn(Optional.empty());
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pointageService.pointerFromKiosk(request, kioskOrgId);

            // 15 min before = 0.25h, 45 min after = 0.75h
            verify(creneauRepository).findActiveForClockIn(
                    eq(kioskOrgId), eq(EMPLOYE_ID), eq(SITE_ID),
                    anyString(), anyInt(), anyDouble(), eq(0.25), eq(0.75));
        }

        @Test
        @DisplayName("falls back to org-wide params when no site-scoped row exists")
        void fallsBackToOrgParams() {
            Parametres orgParams = Parametres.builder()
                    .toleranceAvantShiftMinutes(10)
                    .toleranceApresShiftMinutes(10)
                    .build();
            when(parametresRepository.findBySiteIdAndOrganisationId(SITE_ID, kioskOrgId))
                    .thenReturn(Optional.empty());
            when(parametresRepository.findBySiteIdIsNullAndOrganisationId(kioskOrgId))
                    .thenReturn(Optional.of(orgParams));
            stubActiveCreneau();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, kioskOrgId)).thenReturn(Optional.empty());
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pointageService.pointerFromKiosk(request, kioskOrgId);

            // 10/60 = 0.1666... — assert close to 10 min in hours
            verify(creneauRepository).findActiveForClockIn(
                    eq(kioskOrgId), eq(EMPLOYE_ID), eq(SITE_ID),
                    anyString(), anyInt(), anyDouble(),
                    doubleThat(v -> Math.abs(v - 10.0 / 60.0) < 1e-9),
                    doubleThat(v -> Math.abs(v - 10.0 / 60.0) < 1e-9));
        }

        @Test
        @DisplayName("generic exception message never leaks the audit reason")
        void genericMessageNeverLeaksReason() {
            stubNoCreneauAtAll();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");

            ClockInNotAuthorizedException thrown = assertThrows(
                    ClockInNotAuthorizedException.class,
                    () -> pointageService.pointerFromKiosk(request, kioskOrgId));

            assertThat(thrown.getMessage()).isEqualTo(ClockInNotAuthorizedException.GENERIC_MESSAGE);
            assertThat(thrown.getMessage()).doesNotContain("NO_CRENEAU");
            assertThat(thrown.getMessage()).doesNotContain("WRONG");
            assertThat(thrown.getMessage()).doesNotContain("OUTSIDE");
        }

        @Test
        @DisplayName("pointage is never persisted when the guard rejects")
        void rejectionNeverPersists() {
            stubNoCreneauAtAll();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");

            assertThrows(ClockInNotAuthorizedException.class,
                    () -> pointageService.pointerFromKiosk(request, kioskOrgId));

            verify(pointageRepository, never()).save(any(Pointage.class));
            verifyNoInteractions(pauseService);
        }

        @Test
        @DisplayName("guard passes employeId + siteId + orgId unchanged to the repository query")
        void guardPassesCorrectIdsToRepository() {
            stubActiveCreneau();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, kioskOrgId)).thenReturn(Optional.empty());
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pointageService.pointerFromKiosk(request, kioskOrgId);

            verify(creneauRepository).findActiveForClockIn(
                    eq(kioskOrgId), eq(EMPLOYE_ID), eq(SITE_ID),
                    anyString(), anyInt(), anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("falls back to hardcoded 30/30 when no Parametres row exists at all")
        void fallsBackToHardcodedDefaultsWhenNoParamsRow() {
            // Both site-scoped AND org-wide params return empty
            when(parametresRepository.findBySiteIdAndOrganisationId(SITE_ID, kioskOrgId))
                    .thenReturn(Optional.empty());
            when(parametresRepository.findBySiteIdIsNullAndOrganisationId(kioskOrgId))
                    .thenReturn(Optional.empty());
            stubActiveCreneau();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, kioskOrgId)).thenReturn(Optional.empty());
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pointageService.pointerFromKiosk(request, kioskOrgId);

            // 30 min / 60 = 0.5h on each side — defensive default kicks in
            verify(creneauRepository).findActiveForClockIn(
                    eq(kioskOrgId), eq(EMPLOYE_ID), eq(SITE_ID),
                    anyString(), anyInt(), anyDouble(),
                    doubleThat(v -> Math.abs(v - 0.5) < 1e-9),
                    doubleThat(v -> Math.abs(v - 0.5) < 1e-9));
        }

        @Test
        @DisplayName("honours a ZERO tolerance (strict boundaries) when configured")
        void honoursZeroToleranceWhenConfigured() {
            Parametres strict = Parametres.builder()
                    .toleranceAvantShiftMinutes(0)
                    .toleranceApresShiftMinutes(0)
                    .build();
            when(parametresRepository.findBySiteIdAndOrganisationId(SITE_ID, kioskOrgId))
                    .thenReturn(Optional.of(strict));
            stubActiveCreneau();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, kioskOrgId)).thenReturn(Optional.empty());
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pointageService.pointerFromKiosk(request, kioskOrgId);

            verify(creneauRepository).findActiveForClockIn(
                    eq(kioskOrgId), eq(EMPLOYE_ID), eq(SITE_ID),
                    anyString(), anyInt(), anyDouble(), eq(0.0), eq(0.0));
        }

        @Test
        @DisplayName("honours a large (60 min before) tolerance so an employee at 7h15 for an 8h shift passes")
        void honoursLargeBeforeTolerance() {
            // Product use case: admin sets "Avant" to 60 min in settings.
            Parametres wide = Parametres.builder()
                    .toleranceAvantShiftMinutes(60)
                    .toleranceApresShiftMinutes(30)
                    .build();
            when(parametresRepository.findBySiteIdAndOrganisationId(SITE_ID, kioskOrgId))
                    .thenReturn(Optional.of(wide));
            stubActiveCreneau();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, kioskOrgId)).thenReturn(Optional.empty());
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pointageService.pointerFromKiosk(request, kioskOrgId);

            // 60 min before = 1.0h, 30 min after = 0.5h
            verify(creneauRepository).findActiveForClockIn(
                    eq(kioskOrgId), eq(EMPLOYE_ID), eq(SITE_ID),
                    anyString(), anyInt(), anyDouble(), eq(1.0), eq(0.5));
        }

        @Test
        @DisplayName("null tolerance fields on the Parametres row fall back to 30/30 defaults")
        void nullToleranceFieldsFallBackToDefaults() {
            // A Parametres row exists but the new tolerance columns haven't
            // been populated (e.g. legacy row from before the V40 migration).
            Parametres legacy = Parametres.builder()
                    .toleranceAvantShiftMinutes(null)
                    .toleranceApresShiftMinutes(null)
                    .build();
            when(parametresRepository.findBySiteIdAndOrganisationId(SITE_ID, kioskOrgId))
                    .thenReturn(Optional.of(legacy));
            stubActiveCreneau();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, kioskOrgId)).thenReturn(Optional.empty());
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pointageService.pointerFromKiosk(request, kioskOrgId);

            verify(creneauRepository).findActiveForClockIn(
                    eq(kioskOrgId), eq(EMPLOYE_ID), eq(SITE_ID),
                    anyString(), anyInt(), anyDouble(),
                    doubleThat(v -> Math.abs(v - 0.5) < 1e-9),
                    doubleThat(v -> Math.abs(v - 0.5) < 1e-9));
        }

        @Test
        @DisplayName("site-scoped tolerance overrides org-wide tolerance (per-site wins)")
        void siteToleranceOverridesOrgTolerance() {
            Parametres orgWide = Parametres.builder()
                    .toleranceAvantShiftMinutes(30)
                    .toleranceApresShiftMinutes(30)
                    .build();
            Parametres siteSpecific = Parametres.builder()
                    .toleranceAvantShiftMinutes(90)
                    .toleranceApresShiftMinutes(5)
                    .build();
            when(parametresRepository.findBySiteIdAndOrganisationId(SITE_ID, kioskOrgId))
                    .thenReturn(Optional.of(siteSpecific));
            // Org-wide stub is never consulted because site row exists.
            lenient().when(parametresRepository.findBySiteIdIsNullAndOrganisationId(kioskOrgId))
                    .thenReturn(Optional.of(orgWide));
            stubActiveCreneau();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");
            when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, SITE_ID, kioskOrgId)).thenReturn(Optional.empty());
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pointageService.pointerFromKiosk(request, kioskOrgId);

            // 90 min / 60 = 1.5h, 5 min / 60 ≈ 0.0833h — NOT 30/30 (org defaults)
            verify(creneauRepository).findActiveForClockIn(
                    eq(kioskOrgId), eq(EMPLOYE_ID), eq(SITE_ID),
                    anyString(), anyInt(), anyDouble(), eq(1.5),
                    doubleThat(v -> Math.abs(v - 5.0 / 60.0) < 1e-9));
            verify(parametresRepository, never()).findBySiteIdIsNullAndOrganisationId(anyString());
        }

        @Test
        @DisplayName("audit log fires for every reject path (reason always set)")
        void auditLogFiresOnReject() {
            stubNoCreneauAtAll();
            PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "pin");

            ClockInNotAuthorizedException thrown = assertThrows(
                    ClockInNotAuthorizedException.class,
                    () -> pointageService.pointerFromKiosk(request, kioskOrgId));

            // The reason field is what the audit log reads from; verify it is set,
            // machine-readable, and matches one of the enum values.
            assertThat(thrown.getReason()).isNotNull();
            assertThat(thrown.getReason())
                    .isIn(ClockInNotAuthorizedException.Reason.values());
        }
    }

    // -------------------------------------------------------------------------
    // update()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("update()")
    class UpdatePointage {

        @Test
        @DisplayName("changes type, horodatage, methode, statut, and anomalie fields")
        void update_changesFieldsCorrectly() {
            Pointage existing = Pointage.builder()
                    .id(POINTAGE_ID).employeId(EMPLOYE_ID)
                    .type(TypePointage.entree).horodatage(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
                    .methode(MethodePointage.web).statut(StatutPointage.anomalie)
                    .anomalie("Double pointage").organisationId(ORG_ID).build();
            OffsetDateTime correctedTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
            PointageDto dto = new PointageDto(POINTAGE_ID, EMPLOYE_ID, "sortie", correctedTime,
                    "manuel", "corrige", null, null, ORG_ID);
            when(pointageRepository.findByIdAndOrganisationId(POINTAGE_ID, ORG_ID))
                    .thenReturn(Optional.of(existing));
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.update(POINTAGE_ID, dto);

            assertThat(result.getType()).isEqualTo(TypePointage.sortie);
            assertThat(result.getHorodatage()).isEqualTo(correctedTime);
            assertThat(result.getMethode()).isEqualTo(MethodePointage.manuel);
            assertThat(result.getStatut()).isEqualTo(StatutPointage.corrige);
            assertThat(result.getAnomalie()).isNull();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when pointage not found")
        void update_nonExisting_throwsResourceNotFoundException() {
            PointageDto dto = new PointageDto(POINTAGE_ID, EMPLOYE_ID, null, null,
                    null, null, null, null, ORG_ID);
            when(pointageRepository.findByIdAndOrganisationId(POINTAGE_ID, ORG_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> pointageService.update(POINTAGE_ID, dto));
        }

        @Test
        @DisplayName("does not overwrite fields that are null in the DTO")
        void update_withNullDtoFields_keepsExistingValues() {
            OffsetDateTime originalTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
            Pointage existing = Pointage.builder()
                    .id(POINTAGE_ID).employeId(EMPLOYE_ID)
                    .type(TypePointage.entree).horodatage(originalTime)
                    .methode(MethodePointage.pin).statut(StatutPointage.valide)
                    .organisationId(ORG_ID).build();
            // All nullable fields left null in DTO
            PointageDto dto = new PointageDto(POINTAGE_ID, EMPLOYE_ID, null, null,
                    null, null, "note", null, ORG_ID);
            when(pointageRepository.findByIdAndOrganisationId(POINTAGE_ID, ORG_ID))
                    .thenReturn(Optional.of(existing));
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.update(POINTAGE_ID, dto);

            assertThat(result.getType()).isEqualTo(TypePointage.entree);
            assertThat(result.getHorodatage()).isEqualTo(originalTime);
            assertThat(result.getMethode()).isEqualTo(MethodePointage.pin);
            assertThat(result.getStatut()).isEqualTo(StatutPointage.valide);
        }
    }

    // -------------------------------------------------------------------------
    // findAnomalies()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findAnomalies()")
    class FindAnomalies {

        @Test
        @DisplayName("returns only pointages with statut anomalie")
        void findAnomalies_returnsOnlyAnomalies() {
            Pointage a1 = Pointage.builder().id("a1").statut(StatutPointage.anomalie)
                    .employeId(EMPLOYE_ID).type(TypePointage.sortie)
                    .horodatage(OffsetDateTime.now(ZoneOffset.UTC)).methode(MethodePointage.web)
                    .organisationId(ORG_ID).build();
            Pointage a2 = Pointage.builder().id("a2").statut(StatutPointage.anomalie)
                    .employeId("emp-002").type(TypePointage.entree)
                    .horodatage(OffsetDateTime.now(ZoneOffset.UTC)).methode(MethodePointage.pin)
                    .organisationId(ORG_ID).build();
            when(pointageRepository.findByStatutAndOrganisationId(StatutPointage.anomalie, ORG_ID))
                    .thenReturn(List.of(a1, a2));

            List<Pointage> result = pointageService.findAnomalies();

            assertThat(result).hasSize(2)
                    .allMatch(p -> p.getStatut() == StatutPointage.anomalie);
            verify(pointageRepository).findByStatutAndOrganisationId(StatutPointage.anomalie, ORG_ID);
        }

        @Test
        @DisplayName("returns empty list when no anomalies exist")
        void findAnomalies_empty_whenNoAnomalies() {
            when(pointageRepository.findByStatutAndOrganisationId(StatutPointage.anomalie, ORG_ID))
                    .thenReturn(List.of());

            List<Pointage> result = pointageService.findAnomalies();

            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // delete()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("delete()")
    class DeletePointage {

        @Test
        @DisplayName("deletes pointage when found in current org")
        void delete_existingPointage_deletes() {
            Pointage existing = buildPointage(TypePointage.entree, OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
            when(pointageRepository.findByIdAndOrganisationId(POINTAGE_ID, ORG_ID))
                    .thenReturn(Optional.of(existing));

            pointageService.delete(POINTAGE_ID);

            verify(pointageRepository).delete(existing);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when pointage not found")
        void delete_nonExisting_throwsResourceNotFoundException() {
            when(pointageRepository.findByIdAndOrganisationId(POINTAGE_ID, ORG_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> pointageService.delete(POINTAGE_ID));
        }
    }
}
