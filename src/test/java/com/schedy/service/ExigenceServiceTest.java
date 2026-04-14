package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.entity.Exigence;
import com.schedy.repository.ExigenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Sprint 16 / Feature 1 : tests for variable staffing needs by period.
 *
 * <p>Focus on {@link ExigenceService#findActiveForDate(LocalDate)} : period
 * filtering (dateDebut/dateFin range) and priority deduplication when two
 * exigences cover the same tuple.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExigenceService — Sprint 16 period scoping")
class ExigenceServiceTest {

    private static final String ORG_ID = "org-1";

    @Mock private ExigenceRepository exigenceRepository;
    @Mock private TenantContext tenantContext;

    @InjectMocks private ExigenceService exigenceService;

    @BeforeEach
    void setup() {
        when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
    }

    // Helper to build a bare exigence with common defaults
    private Exigence exigence(String id, String role, LocalDate dateDebut, LocalDate dateFin, int priorite, int nombreRequis) {
        return Exigence.builder()
                .id(id)
                .libelle(id)
                .role(role)
                .jours(List.of(0, 1, 2, 3, 4))   // Mon-Fri
                .heureDebut(9.0)
                .heureFin(17.0)
                .siteId("site-A")
                .organisationId(ORG_ID)
                .nombreRequis(nombreRequis)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .priorite(priorite)
                .build();
    }

    @Test
    @DisplayName("findActiveForDate returns base exigence (dateDebut=null) regardless of reference date")
    void findActiveForDate_baseExigence_alwaysActive() {
        Exigence base = exigence("base", "cuisinier", null, null, 0, 2);
        when(exigenceRepository.findByOrganisationId(ORG_ID)).thenReturn(List.of(base));

        List<Exigence> active = exigenceService.findActiveForDate(LocalDate.of(2026, 7, 14));

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getId()).isEqualTo("base");
    }

    @Test
    @DisplayName("findActiveForDate excludes a period exigence when the reference is outside its range")
    void findActiveForDate_periodExigence_outsideRange_excluded() {
        Exigence holidays = exigence("holidays", "cuisinier",
                LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 5),
                1, 4);
        when(exigenceRepository.findByOrganisationId(ORG_ID)).thenReturn(List.of(holidays));

        List<Exigence> active = exigenceService.findActiveForDate(LocalDate.of(2026, 7, 14));

        assertThat(active).isEmpty();
    }

    @Test
    @DisplayName("findActiveForDate includes a period exigence when the reference is inside its range")
    void findActiveForDate_periodExigence_insideRange_included() {
        Exigence holidays = exigence("holidays", "cuisinier",
                LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 5),
                1, 4);
        when(exigenceRepository.findByOrganisationId(ORG_ID)).thenReturn(List.of(holidays));

        List<Exigence> active = exigenceService.findActiveForDate(LocalDate.of(2026, 12, 20));

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getNombreRequis()).isEqualTo(4);
    }

    @Test
    @DisplayName("findActiveForDate keeps the higher-priorite exigence when two cover the same tuple")
    void findActiveForDate_overlappingTuples_higherPriorityWins() {
        // Both exigences cover (cuisinier, site-A, jours 0-4, 9h-17h)
        Exigence base     = exigence("base",     "cuisinier", null, null, 0, 2);
        Exigence holidays = exigence("holidays", "cuisinier",
                LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 5),
                1, 5);
        when(exigenceRepository.findByOrganisationId(ORG_ID))
                .thenReturn(List.of(base, holidays));

        // Reference date inside the holiday window : both are "applicable" after the
        // date filter, but the priority 1 entry wins the dedupe.
        List<Exigence> active = exigenceService.findActiveForDate(LocalDate.of(2026, 12, 20));

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getId()).isEqualTo("holidays");
        assertThat(active.get(0).getNombreRequis()).isEqualTo(5);
        assertThat(active.get(0).getPriorite()).isEqualTo(1);
    }

    @Test
    @DisplayName("findActiveForDate falls back to base exigence outside the period window")
    void findActiveForDate_outsidePeriod_fallsBackToBase() {
        Exigence base     = exigence("base",     "cuisinier", null, null, 0, 2);
        Exigence holidays = exigence("holidays", "cuisinier",
                LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 5),
                1, 5);
        when(exigenceRepository.findByOrganisationId(ORG_ID))
                .thenReturn(List.of(base, holidays));

        // Outside the holiday window : only base applies.
        List<Exigence> active = exigenceService.findActiveForDate(LocalDate.of(2026, 7, 14));

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getId()).isEqualTo("base");
        assertThat(active.get(0).getNombreRequis()).isEqualTo(2);
    }
}
