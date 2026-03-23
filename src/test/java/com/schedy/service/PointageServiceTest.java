package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.request.PointerRequest;
import com.schedy.entity.Pointage;
import com.schedy.repository.PointageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @BeforeEach
    void setUp() {
        when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
    }

    @Test
    @DisplayName("pointer() creates entree when no previous pointage")
    void pointer_createsEntree_whenNoPrevious() {
        PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "web");
        when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID))
                .thenReturn(Optional.empty());
        when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Pointage result = pointageService.pointer(request);

        assertThat(result.getType()).isEqualTo("entree");
        assertThat(result.getStatut()).isEqualTo("valide");
        assertThat(result.getEmployeId()).isEqualTo(EMPLOYE_ID);
        assertThat(result.getOrganisationId()).isEqualTo(ORG_ID);
    }

    @Test
    @DisplayName("pointer() creates sortie when last was entree")
    void pointer_createsSortie_whenLastWasEntree() {
        PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "pin");
        Pointage lastEntree = Pointage.builder()
                .employeId(EMPLOYE_ID).type("entree")
                .horodatage(LocalDateTime.now().minusHours(8))
                .methode("pin").statut("valide").organisationId(ORG_ID).build();
        when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID))
                .thenReturn(Optional.of(lastEntree));
        when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Pointage result = pointageService.pointer(request);

        assertThat(result.getType()).isEqualTo("sortie");
        assertThat(result.getStatut()).isEqualTo("valide");
    }

    @Test
    @DisplayName("pointer() detects double pointage anomaly (< 1 min)")
    void pointer_detectsDoublePointageAnomaly() {
        PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "web");
        Pointage recent = Pointage.builder()
                .employeId(EMPLOYE_ID).type("entree")
                .horodatage(LocalDateTime.now().minusSeconds(30))
                .methode("web").statut("valide").organisationId(ORG_ID).build();
        when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID))
                .thenReturn(Optional.of(recent));
        when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Pointage result = pointageService.pointer(request);

        assertThat(result.getStatut()).isEqualTo("anomalie");
        assertThat(result.getAnomalie()).contains("Double pointage");
    }

    @Test
    @DisplayName("pointer() detects long duration anomaly (>= 12h)")
    void pointer_detectsLongDurationAnomaly() {
        PointerRequest request = new PointerRequest(EMPLOYE_ID, null, "manuel");
        Pointage oldEntree = Pointage.builder()
                .employeId(EMPLOYE_ID).type("entree")
                .horodatage(LocalDateTime.now().minusHours(13))
                .methode("manuel").statut("valide").organisationId(ORG_ID).build();
        when(pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, ORG_ID))
                .thenReturn(Optional.of(oldEntree));
        when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Pointage result = pointageService.pointer(request);

        assertThat(result.getType()).isEqualTo("sortie");
        assertThat(result.getStatut()).isEqualTo("anomalie");
    }

    @Test
    @DisplayName("pointer() stores correct horodatage and siteId")
    void pointer_storesCorrectData() {
        PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "qr");
        when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, SITE_ID, ORG_ID))
                .thenReturn(Optional.empty());
        ArgumentCaptor<Pointage> captor = ArgumentCaptor.forClass(Pointage.class);
        when(pointageRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        pointageService.pointer(request);
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        Pointage saved = captor.getValue();
        assertThat(saved.getHorodatage()).isAfter(before).isBefore(after);
        assertThat(saved.getSiteId()).isEqualTo(SITE_ID);
    }

    @Test
    @DisplayName("pointerFromKiosk() bypasses TenantContext")
    void pointerFromKiosk_bypassesTenantContext() {
        String kioskOrgId = "kiosk-org-999";
        PointerRequest request = new PointerRequest(EMPLOYE_ID, SITE_ID, "qr");
        when(pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(EMPLOYE_ID, SITE_ID, kioskOrgId))
                .thenReturn(Optional.empty());
        when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        pointageService.pointerFromKiosk(request, kioskOrgId);

        verify(tenantContext, never()).requireOrganisationId();
    }
}
