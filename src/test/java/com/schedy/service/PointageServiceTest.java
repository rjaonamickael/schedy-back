package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.PointageDto;
import com.schedy.dto.request.PointerRequest;
import com.schedy.entity.*;
import com.schedy.exception.ResourceNotFoundException;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PointageService unit tests")
class PointageServiceTest {

    @Mock private PointageRepository pointageRepository;
    @Mock private TenantContext tenantContext;

    @InjectMocks private PointageService pointageService;

    private static final String ORG_ID = "org-123";
    private static final String EMPLOYE_ID = "emp-456";
    private static final String SITE_ID = "site-789";
    private static final String POINTAGE_ID = "ptr-111";

    @BeforeEach
    void setUp() {
        // lenient because some tests (e.g. pointerFromKiosk) must NOT call this
        lenient().when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a Pointage with enum-typed fields, as the actual entity uses enums. */
    private Pointage buildPointage(TypePointage type, LocalDateTime horodatage) {
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
            Pointage lastEntree = buildPointage(TypePointage.entree, LocalDateTime.now().minusHours(8));
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
            Pointage lastSortie = buildPointage(TypePointage.sortie, LocalDateTime.now().minusHours(1));
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
            Pointage recentEntree = buildPointage(TypePointage.entree, LocalDateTime.now().minusSeconds(30));
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
            Pointage oldEntree = buildPointage(TypePointage.entree, LocalDateTime.now().minusHours(13));
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
            Pointage normalEntree = buildPointage(TypePointage.entree, LocalDateTime.now().minusHours(8));
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
            Pointage entree = buildPointage(TypePointage.entree, LocalDateTime.now().minusHours(12).minusSeconds(1));
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

            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            pointageService.pointer(request);
            LocalDateTime after = LocalDateTime.now().plusSeconds(1);

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
            PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "qr");
            Pointage lastEntree = Pointage.builder()
                    .employeId(EMPLOYE_ID).type(TypePointage.entree)
                    .horodatage(LocalDateTime.now().minusHours(4))
                    .methode(MethodePointage.qr).statut(StatutPointage.valide)
                    .organisationId(kioskOrgId).build();
            when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(
                    EMPLOYE_ID, kioskOrgId)).thenReturn(Optional.of(lastEntree));
            when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Pointage result = pointageService.pointerFromKiosk(request, kioskOrgId);

            assertThat(result.getType()).isEqualTo(TypePointage.sortie);
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
                    .type(TypePointage.entree).horodatage(LocalDateTime.now().minusHours(1))
                    .methode(MethodePointage.web).statut(StatutPointage.anomalie)
                    .anomalie("Double pointage").organisationId(ORG_ID).build();
            LocalDateTime correctedTime = LocalDateTime.now().minusHours(2);
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
            LocalDateTime originalTime = LocalDateTime.now().minusHours(1);
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
                    .horodatage(LocalDateTime.now()).methode(MethodePointage.web)
                    .organisationId(ORG_ID).build();
            Pointage a2 = Pointage.builder().id("a2").statut(StatutPointage.anomalie)
                    .employeId("emp-002").type(TypePointage.entree)
                    .horodatage(LocalDateTime.now()).methode(MethodePointage.pin)
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
            Pointage existing = buildPointage(TypePointage.entree, LocalDateTime.now().minusHours(1));
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
