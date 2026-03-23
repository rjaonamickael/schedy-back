package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.CreneauAssigneDto;
import com.schedy.entity.CreneauAssigne;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.CreneauAssigneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanningService unit tests")
class PlanningServiceTest {

    @Mock private CreneauAssigneRepository creneauRepository;
    @Mock private TenantContext tenantContext;

    @InjectMocks private PlanningService planningService;

    private static final String ORG_ID = "org-123";
    private static final String SEMAINE = "2025-W12";
    private static final String CRENEAU_ID = "crn-111";

    @BeforeEach
    void setUp() {
        when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
    }

    @Test
    @DisplayName("create() saves creneau with org context")
    void create_savesCreneau() {
        CreneauAssigneDto dto = new CreneauAssigneDto(null, "emp-1", 1, 8.0, 17.0, SEMAINE, "site-1", null);
        when(creneauRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreneauAssigne result = planningService.create(dto);

        assertThat(result.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(result.getEmployeId()).isEqualTo("emp-1");
    }

    @Test
    @DisplayName("createBatch() saves all creneaux")
    void createBatch_savesAll() {
        List<CreneauAssigneDto> dtos = List.of(
                new CreneauAssigneDto(null, "emp-1", 1, 8.0, 12.0, SEMAINE, "site-1", null),
                new CreneauAssigneDto(null, "emp-2", 2, 9.0, 17.0, SEMAINE, "site-1", null));
        when(creneauRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<CreneauAssigne> result = planningService.createBatch(dtos);

        assertThat(result).hasSize(2);
        verify(creneauRepository).saveAll(any());
    }

    @Test
    @DisplayName("delete() removes creneau")
    void delete_removesCreneau() {
        CreneauAssigne creneau = CreneauAssigne.builder().id(CRENEAU_ID).organisationId(ORG_ID).build();
        when(creneauRepository.findByIdAndOrganisationId(CRENEAU_ID, ORG_ID)).thenReturn(Optional.of(creneau));

        planningService.delete(CRENEAU_ID);

        verify(creneauRepository).delete(creneau);
    }

    @Test
    @DisplayName("delete() throws when not found")
    void delete_throwsWhenNotFound() {
        when(creneauRepository.findByIdAndOrganisationId(CRENEAU_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> planningService.delete(CRENEAU_ID));
    }

    @Test
    @DisplayName("findBySemaine() returns matching creneaux")
    void findBySemaine_returnsMatching() {
        List<CreneauAssigne> expected = List.of(
                CreneauAssigne.builder().id("c1").semaine(SEMAINE).organisationId(ORG_ID).build());
        when(creneauRepository.findBySemaineAndOrganisationId(SEMAINE, ORG_ID)).thenReturn(expected);

        assertThat(planningService.findBySemaine(SEMAINE)).hasSize(1);
    }
}
