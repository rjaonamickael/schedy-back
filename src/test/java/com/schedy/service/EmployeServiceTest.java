package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.EmployeDto;
import com.schedy.entity.Employe;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.*;
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
@DisplayName("EmployeService unit tests")
class EmployeServiceTest {

    @Mock private EmployeRepository employeRepository;
    @Mock private TenantContext tenantContext;
    @Mock private CreneauAssigneRepository creneauAssigneRepository;
    @Mock private PointageRepository pointageRepository;
    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private BanqueCongeRepository banqueCongeRepository;

    @InjectMocks private EmployeService employeService;

    private static final String ORG_ID = "org-123";
    private static final String EMPLOYE_ID = "emp-456";

    @BeforeEach
    void setUp() {
        when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
    }

    @Test
    @DisplayName("create() saves employe with organisation context")
    void create_savesWithOrg() {
        EmployeDto dto = new EmployeDto(null, "Alice", "employe", "0600000000",
                "alice@example.com", null, null, "1234", null, List.of(), List.of("site-1"));
        when(employeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Employe result = employeService.create(dto);

        assertThat(result.getNom()).isEqualTo("Alice");
        assertThat(result.getOrganisationId()).isEqualTo(ORG_ID);
    }

    @Test
    @DisplayName("delete() cascades cleanup before deletion")
    void delete_cascadesCleanup() {
        Employe emp = Employe.builder().id(EMPLOYE_ID).nom("Alice").organisationId(ORG_ID).build();
        when(employeRepository.findByIdAndOrganisationId(EMPLOYE_ID, ORG_ID)).thenReturn(Optional.of(emp));

        employeService.delete(EMPLOYE_ID);

        verify(creneauAssigneRepository).deleteByEmployeIdAndOrganisationId(EMPLOYE_ID, ORG_ID);
        verify(pointageRepository).deleteByEmployeIdAndOrganisationId(EMPLOYE_ID, ORG_ID);
        verify(demandeCongeRepository).deleteByEmployeIdAndOrganisationId(EMPLOYE_ID, ORG_ID);
        verify(banqueCongeRepository).deleteByEmployeIdAndOrganisationId(EMPLOYE_ID, ORG_ID);
        verify(employeRepository).delete(emp);
    }

    @Test
    @DisplayName("delete() throws when employe not found")
    void delete_throwsWhenNotFound() {
        when(employeRepository.findByIdAndOrganisationId(EMPLOYE_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> employeService.delete(EMPLOYE_ID));
    }

    @Test
    @DisplayName("findAll() filters by organisationId")
    void findAll_filtersByOrg() {
        List<Employe> expected = List.of(
                Employe.builder().id("e1").nom("A").organisationId(ORG_ID).build(),
                Employe.builder().id("e2").nom("B").organisationId(ORG_ID).build());
        when(employeRepository.findByOrganisationId(ORG_ID)).thenReturn(expected);

        List<Employe> result = employeService.findAll();

        assertThat(result).hasSize(2);
    }
}
