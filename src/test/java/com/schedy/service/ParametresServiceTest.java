package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.ParametresDto;
import com.schedy.entity.Parametres;
import com.schedy.repository.ParametresRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Focused on the clock-in tolerance fields added for the kiosk creneau guard.
 * The rest of the ParametresService surface (pause rules, labor law, etc.) is
 * already covered by higher-level integration tests and is intentionally left
 * out of this unit suite.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ParametresService — clock-in tolerance mapping")
class ParametresServiceTest {

    @Mock private ParametresRepository parametresRepository;
    @Mock private TenantContext tenantContext;
    @Mock private ParametresService.ParametresCacheStore cacheStore;

    @InjectMocks private ParametresService parametresService;

    private static final String ORG_ID = "org-abc";

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
    }

    /**
     * Builds a DTO with the tolerance fields set and everything else at
     * sensible defaults. Keeps the test bodies short.
     */
    private ParametresDto dtoWithTolerance(Integer avant, Integer apres) {
        return new ParametresDto(
                null,                      // id
                6,                         // heureDebut
                22,                        // heureFin
                List.of(0, 1, 2, 3, 4),    // joursActifs
                0,                         // premierJour
                1.0,                       // dureeMinAffectation
                48.0,                      // heuresMaxSemaine
                10.0,                      // dureeMaxJour
                null,                      // taillePolice
                null,                      // planningVue
                1.0,                       // planningGranularite
                List.of(),                 // reglesAffectation
                60,                        // delaiSignalementAbsenceMinutes
                48,                        // seuilAbsenceVsCongeHeures
                0.0, 0.0, 0,               // labor law (disabled)
                null, null, List.of(),     // pause layer 1
                false, 0.0, 0, false, List.of(), // pause layer 2
                15, 90, false,             // pause layer 3
                avant,                     // toleranceAvantShiftMinutes
                apres                      // toleranceApresShiftMinutes
        );
    }

    private Parametres existingEntity() {
        return Parametres.builder()
                .organisationId(ORG_ID)
                .heureDebut(6)
                .heureFin(22)
                .joursActifs(new java.util.ArrayList<>(List.of(0, 1, 2, 3, 4)))
                .premierJour(0)
                .dureeMinAffectation(1.0)
                .toleranceAvantShiftMinutes(30)
                .toleranceApresShiftMinutes(30)
                .build();
    }

    @Nested
    @DisplayName("update() — tolerance field mapping")
    class ToleranceMapping {

        @Test
        @DisplayName("writes non-null tolerance values from the DTO onto the entity")
        void writesNonNullTolerance() {
            Parametres entity = existingEntity();
            when(cacheStore.getForOrg(ORG_ID, parametresRepository)).thenReturn(entity);
            when(parametresRepository.save(any(Parametres.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ParametresDto dto = dtoWithTolerance(45, 15);
            Parametres saved = parametresService.update(dto);

            assertThat(saved.getToleranceAvantShiftMinutes()).isEqualTo(45);
            assertThat(saved.getToleranceApresShiftMinutes()).isEqualTo(15);
        }

        @Test
        @DisplayName("accepts ZERO tolerance (strict boundaries)")
        void acceptsZeroTolerance() {
            Parametres entity = existingEntity();
            when(cacheStore.getForOrg(ORG_ID, parametresRepository)).thenReturn(entity);
            when(parametresRepository.save(any(Parametres.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Parametres saved = parametresService.update(dtoWithTolerance(0, 0));

            assertThat(saved.getToleranceAvantShiftMinutes()).isZero();
            assertThat(saved.getToleranceApresShiftMinutes()).isZero();
        }

        @Test
        @DisplayName("preserves existing tolerance values when the DTO passes null")
        void preservesExistingWhenDtoNull() {
            Parametres entity = existingEntity();
            entity.setToleranceAvantShiftMinutes(42);
            entity.setToleranceApresShiftMinutes(17);
            when(cacheStore.getForOrg(ORG_ID, parametresRepository)).thenReturn(entity);
            when(parametresRepository.save(any(Parametres.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Parametres saved = parametresService.update(dtoWithTolerance(null, null));

            assertThat(saved.getToleranceAvantShiftMinutes())
                    .as("null in DTO must not overwrite — keep the previously saved value")
                    .isEqualTo(42);
            assertThat(saved.getToleranceApresShiftMinutes()).isEqualTo(17);
        }

        @Test
        @DisplayName("partial update: only 'avant' changed, 'apres' preserved")
        void partialUpdateAvantOnly() {
            Parametres entity = existingEntity();
            entity.setToleranceAvantShiftMinutes(30);
            entity.setToleranceApresShiftMinutes(60);
            when(cacheStore.getForOrg(ORG_ID, parametresRepository)).thenReturn(entity);
            when(parametresRepository.save(any(Parametres.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Parametres saved = parametresService.update(dtoWithTolerance(120, null));

            assertThat(saved.getToleranceAvantShiftMinutes()).isEqualTo(120);
            assertThat(saved.getToleranceApresShiftMinutes())
                    .as("apres must stay at its previous value when DTO is null")
                    .isEqualTo(60);
        }

        @Test
        @DisplayName("updateBySite() also maps the tolerance fields onto the site-scoped row")
        void updateBySiteMapsTolerance() {
            String siteId = "site-1";
            Parametres siteRow = existingEntity();
            siteRow.setSiteId(siteId);
            when(parametresRepository.findBySiteIdAndOrganisationId(siteId, ORG_ID))
                    .thenReturn(Optional.of(siteRow));
            when(parametresRepository.save(any(Parametres.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Parametres saved = parametresService.updateBySite(siteId, dtoWithTolerance(75, 10));

            assertThat(saved.getSiteId()).isEqualTo(siteId);
            assertThat(saved.getToleranceAvantShiftMinutes()).isEqualTo(75);
            assertThat(saved.getToleranceApresShiftMinutes()).isEqualTo(10);
        }
    }
}
