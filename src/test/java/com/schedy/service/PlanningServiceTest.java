package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.CreneauAssigneDto;
import com.schedy.entity.CreneauAssigne;
import com.schedy.entity.DemandeConge;
import com.schedy.entity.Employe;
import com.schedy.entity.StatutDemande;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.CreneauAssigneRepository;
import com.schedy.repository.DemandeCongeRepository;
import com.schedy.repository.EmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanningService unit tests")
class PlanningServiceTest {

    @Mock private CreneauAssigneRepository creneauRepository;
    @Mock private EmployeRepository employeRepository;
    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private TenantContext tenantContext;

    @InjectMocks private PlanningService planningService;

    private static final String ORG_ID = "org-123";
    private static final String SEMAINE = "2025-W12";
    private static final String CRENEAU_ID = "crn-111";
    private static final String EMP_ID = "emp-1";

    @BeforeEach
    void setUp() {
        when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
    }

    /** Helper : an existing creneau in the DB matching CRENEAU_ID. */
    private CreneauAssigne existingCreneau() {
        return CreneauAssigne.builder()
                .id(CRENEAU_ID)
                .organisationId(ORG_ID)
                .employeId(EMP_ID)
                .semaine(SEMAINE)
                .jour(1)
                .heureDebut(8.0)
                .heureFin(12.0)
                .siteId("site-1")
                .build();
    }

    private CreneauAssigneDto validUpdateDto() {
        return new CreneauAssigneDto(CRENEAU_ID, EMP_ID, 2, 9.0, 17.0, SEMAINE, "site-1", null, null);
    }

    private void stubUpdateHappyPath() {
        when(creneauRepository.findByIdAndOrganisationId(CRENEAU_ID, ORG_ID))
                .thenReturn(Optional.of(existingCreneau()));
        when(employeRepository.findByIdAndOrganisationId(EMP_ID, ORG_ID))
                .thenReturn(Optional.of(new Employe()));
        when(creneauRepository.findByEmployeIdAndSemaineAndOrganisationId(EMP_ID, SEMAINE, ORG_ID))
                .thenReturn(Collections.emptyList());
        when(demandeCongeRepository
                .findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        eq(ORG_ID), eq(StatutDemande.approuve), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(creneauRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("create() saves creneau with org context")
    void create_savesCreneau() {
        CreneauAssigneDto dto = new CreneauAssigneDto(null, "emp-1", 1, 8.0, 17.0, SEMAINE, "site-1", null, null);
        when(creneauRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreneauAssigne result = planningService.create(dto);

        assertThat(result.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(result.getEmployeId()).isEqualTo("emp-1");
    }

    @Test
    @DisplayName("createBatch() saves all creneaux")
    void createBatch_savesAll() {
        List<CreneauAssigneDto> dtos = List.of(
                new CreneauAssigneDto(null, "emp-1", 1, 8.0, 12.0, SEMAINE, "site-1", null, null),
                new CreneauAssigneDto(null, "emp-2", 2, 9.0, 17.0, SEMAINE, "site-1", null, null));
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

    // ── BE-04 / V33 SEC : update() validation ──────────────────────────────

    @Test
    @DisplayName("update() happy path : valid payload saves and returns updated creneau")
    void update_happyPath() {
        stubUpdateHappyPath();

        CreneauAssigne result = planningService.update(CRENEAU_ID, validUpdateDto());

        assertThat(result.getEmployeId()).isEqualTo(EMP_ID);
        assertThat(result.getJour()).isEqualTo(2);
        assertThat(result.getHeureDebut()).isEqualTo(9.0);
        assertThat(result.getHeureFin()).isEqualTo(17.0);
        verify(creneauRepository).save(any());
    }

    @Test
    @DisplayName("update() throws BusinessRule when heureFin <= heureDebut")
    void update_rejectsInvalidHours() {
        when(creneauRepository.findByIdAndOrganisationId(CRENEAU_ID, ORG_ID))
                .thenReturn(Optional.of(existingCreneau()));

        CreneauAssigneDto invalid = new CreneauAssigneDto(CRENEAU_ID, EMP_ID, 1, 12.0, 10.0, SEMAINE, "site-1", null, null);
        assertThrows(BusinessRuleException.class, () -> planningService.update(CRENEAU_ID, invalid));
        verify(creneauRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() throws BusinessRule when target employe does not belong to org")
    void update_rejectsCrossOrgEmploye() {
        when(creneauRepository.findByIdAndOrganisationId(CRENEAU_ID, ORG_ID))
                .thenReturn(Optional.of(existingCreneau()));
        when(employeRepository.findByIdAndOrganisationId(EMP_ID, ORG_ID))
                .thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class,
                () -> planningService.update(CRENEAU_ID, validUpdateDto()));
        verify(creneauRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() throws BusinessRule on overlap with another creneau on same employe/jour")
    void update_rejectsOverlap() {
        when(creneauRepository.findByIdAndOrganisationId(CRENEAU_ID, ORG_ID))
                .thenReturn(Optional.of(existingCreneau()));
        when(employeRepository.findByIdAndOrganisationId(EMP_ID, ORG_ID))
                .thenReturn(Optional.of(new Employe()));
        // Existing 14-18 on jour=2 overlaps with target 9-17
        CreneauAssigne overlapping = CreneauAssigne.builder()
                .id("crn-other")
                .organisationId(ORG_ID)
                .employeId(EMP_ID)
                .semaine(SEMAINE)
                .jour(2)
                .heureDebut(14.0)
                .heureFin(18.0)
                .siteId("site-1")
                .build();
        when(creneauRepository.findByEmployeIdAndSemaineAndOrganisationId(EMP_ID, SEMAINE, ORG_ID))
                .thenReturn(List.of(overlapping));

        assertThrows(BusinessRuleException.class,
                () -> planningService.update(CRENEAU_ID, validUpdateDto()));
        verify(creneauRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() throws BusinessRule when target date is covered by an approved leave")
    void update_rejectsApprovedLeaveConflict() {
        when(creneauRepository.findByIdAndOrganisationId(CRENEAU_ID, ORG_ID))
                .thenReturn(Optional.of(existingCreneau()));
        when(employeRepository.findByIdAndOrganisationId(EMP_ID, ORG_ID))
                .thenReturn(Optional.of(new Employe()));
        when(creneauRepository.findByEmployeIdAndSemaineAndOrganisationId(EMP_ID, SEMAINE, ORG_ID))
                .thenReturn(Collections.emptyList());
        DemandeConge conge = new DemandeConge();
        conge.setEmployeId(EMP_ID);
        when(demandeCongeRepository
                .findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        eq(ORG_ID), eq(StatutDemande.approuve), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(conge));

        assertThrows(BusinessRuleException.class,
                () -> planningService.update(CRENEAU_ID, validUpdateDto()));
        verify(creneauRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() ignores the creneau being updated when checking overlap (skip self)")
    void update_doesNotConflictWithItself() {
        when(creneauRepository.findByIdAndOrganisationId(CRENEAU_ID, ORG_ID))
                .thenReturn(Optional.of(existingCreneau()));
        when(employeRepository.findByIdAndOrganisationId(EMP_ID, ORG_ID))
                .thenReturn(Optional.of(new Employe()));
        // Only the target creneau itself exists on day 2 (it should be skipped)
        CreneauAssigne self = CreneauAssigne.builder()
                .id(CRENEAU_ID)
                .organisationId(ORG_ID)
                .employeId(EMP_ID)
                .semaine(SEMAINE)
                .jour(2)
                .heureDebut(9.0)
                .heureFin(17.0)
                .siteId("site-1")
                .build();
        when(creneauRepository.findByEmployeIdAndSemaineAndOrganisationId(EMP_ID, SEMAINE, ORG_ID))
                .thenReturn(List.of(self));
        when(demandeCongeRepository
                .findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        eq(ORG_ID), eq(StatutDemande.approuve), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(creneauRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreneauAssigne result = planningService.update(CRENEAU_ID, validUpdateDto());
        assertThat(result).isNotNull();
        verify(creneauRepository).save(any());
    }

    // =========================================================================
    // Sprint 16 / Feature 2 : role compatibility check on validateCreneauUpdate
    // =========================================================================

    private CreneauAssigneDto updateDtoWithRole(String role) {
        return new CreneauAssigneDto(CRENEAU_ID, EMP_ID, 2, 9.0, 17.0, SEMAINE, "site-1", null, role);
    }

    @Test
    @DisplayName("Sprint 16 : update throws 422 when dto.role is not in employee.roles")
    void update_roleNotInEmployeRoles_throws422() {
        when(creneauRepository.findByIdAndOrganisationId(CRENEAU_ID, ORG_ID))
                .thenReturn(Optional.of(existingCreneau()));
        // Employee holds only "caissier" — cannot be assigned as "cuisinier"
        Employe emp = Employe.builder()
                .id(EMP_ID)
                .nom("Alice")
                .roles(List.of("caissier"))
                .organisationId(ORG_ID)
                .build();
        when(employeRepository.findByIdAndOrganisationId(EMP_ID, ORG_ID))
                .thenReturn(Optional.of(emp));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> planningService.update(CRENEAU_ID, updateDtoWithRole("cuisinier")));
        assertThat(ex.getMessage()).contains("cuisinier");
        verify(creneauRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sprint 16 : update accepts a role held as secondary in the hierarchy")
    void update_roleHeldAsSecondary_passes() {
        when(creneauRepository.findByIdAndOrganisationId(CRENEAU_ID, ORG_ID))
                .thenReturn(Optional.of(existingCreneau()));
        // Employee holds "caissier" primary and "plongeur" secondary — the assignment
        // as "plongeur" must pass (hierarchy position does not gate validation).
        Employe emp = Employe.builder()
                .id(EMP_ID)
                .nom("Alice")
                .roles(List.of("caissier", "plongeur"))
                .organisationId(ORG_ID)
                .build();
        when(employeRepository.findByIdAndOrganisationId(EMP_ID, ORG_ID))
                .thenReturn(Optional.of(emp));
        when(creneauRepository.findByEmployeIdAndSemaineAndOrganisationId(EMP_ID, SEMAINE, ORG_ID))
                .thenReturn(Collections.emptyList());
        when(demandeCongeRepository
                .findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        eq(ORG_ID), eq(StatutDemande.approuve), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(creneauRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreneauAssigne result = planningService.update(CRENEAU_ID, updateDtoWithRole("plongeur"));

        assertThat(result).isNotNull();
        assertThat(result.getRole()).isEqualTo("plongeur");
    }

    @Test
    @DisplayName("Sprint 16 : update with null role skips role compat check (legacy creneau)")
    void update_nullRole_skipsCheck() {
        when(creneauRepository.findByIdAndOrganisationId(CRENEAU_ID, ORG_ID))
                .thenReturn(Optional.of(existingCreneau()));
        Employe emp = Employe.builder()
                .id(EMP_ID)
                .nom("Alice")
                .roles(List.of("caissier"))
                .organisationId(ORG_ID)
                .build();
        when(employeRepository.findByIdAndOrganisationId(EMP_ID, ORG_ID))
                .thenReturn(Optional.of(emp));
        when(creneauRepository.findByEmployeIdAndSemaineAndOrganisationId(EMP_ID, SEMAINE, ORG_ID))
                .thenReturn(Collections.emptyList());
        when(demandeCongeRepository
                .findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        eq(ORG_ID), eq(StatutDemande.approuve), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(creneauRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // validUpdateDto() has role = null → check is skipped, update proceeds
        CreneauAssigne result = planningService.update(CRENEAU_ID, validUpdateDto());

        assertThat(result).isNotNull();
    }
}
