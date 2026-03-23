package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.DemandeCongeDto;
import com.schedy.entity.BanqueConge;
import com.schedy.entity.DemandeConge;
import com.schedy.entity.TypeConge;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.BanqueCongeRepository;
import com.schedy.repository.DemandeCongeRepository;
import com.schedy.repository.JourFerieRepository;
import com.schedy.repository.TypeCongeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CongeService unit tests")
class CongeServiceTest {

    @Mock private TypeCongeRepository typeCongeRepository;
    @Mock private BanqueCongeRepository banqueCongeRepository;
    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private JourFerieRepository jourFerieRepository;
    @Mock private TenantContext tenantContext;

    @InjectMocks private CongeService congeService;

    private static final String ORG_ID = "org-123";
    private static final String EMPLOYE_ID = "emp-456";
    private static final String TYPE_ID = "type-789";
    private static final String DEMANDE_ID = "dem-111";

    @BeforeEach
    void setUp() {
        when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
    }

    private DemandeCongeDto buildDto(double duree) {
        return new DemandeCongeDto(null, EMPLOYE_ID, TYPE_ID,
                LocalDate.now(), LocalDate.now().plusDays(1),
                null, null, duree, null, "Vacances", null, null);
    }

    private BanqueConge buildBanque(double quota, double utilise, double enAttente) {
        return BanqueConge.builder().id("banque-1").employeId(EMPLOYE_ID).typeCongeId(TYPE_ID)
                .quota(quota).utilise(utilise).enAttente(enAttente).organisationId(ORG_ID).build();
    }

    private TypeConge buildTypeConge(boolean quotaIllimite, boolean autoriserNegatif) {
        return TypeConge.builder().id(TYPE_ID).nom("Conge paye").categorie("paye").unite("jours")
                .quotaIllimite(quotaIllimite).autoriserNegatif(autoriserNegatif).organisationId(ORG_ID).build();
    }

    private DemandeConge buildDemande(String statut, double duree) {
        return DemandeConge.builder().id(DEMANDE_ID).employeId(EMPLOYE_ID).typeCongeId(TYPE_ID)
                .dateDebut(LocalDate.now()).dateFin(LocalDate.now().plusDays(1))
                .duree(duree).statut(statut).organisationId(ORG_ID).build();
    }

    @Test
    @DisplayName("createDemande() succeeds when quota is sufficient")
    void createDemande_succeeds_whenQuotaSufficient() {
        BanqueConge banque = buildBanque(20.0, 5.0, 2.0);
        TypeConge type = buildTypeConge(false, false);
        when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                .thenReturn(Optional.of(banque));
        when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID)).thenReturn(Optional.of(type));
        when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DemandeConge result = congeService.createDemande(buildDto(3.0));

        assertThat(result.getStatut()).isEqualTo("en_attente");
        assertThat(result.getDuree()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("createDemande() throws when quota insufficient")
    void createDemande_throws_whenQuotaInsufficient() {
        BanqueConge banque = buildBanque(20.0, 10.0, 8.0);
        TypeConge type = buildTypeConge(false, false);
        when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                .thenReturn(Optional.of(banque));
        when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID)).thenReturn(Optional.of(type));

        assertThrows(BusinessRuleException.class, () -> congeService.createDemande(buildDto(15.0)));
        verify(demandeCongeRepository, never()).save(any());
    }

    @Test
    @DisplayName("approveDemande() transitions en_attente -> approuve")
    void approveDemande_transitions() {
        DemandeConge demande = buildDemande("en_attente", 3.0);
        BanqueConge banque = buildBanque(20.0, 5.0, 3.0);
        when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID)).thenReturn(Optional.of(demande));
        when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                .thenReturn(Optional.of(banque));
        when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DemandeConge result = congeService.approveDemande(DEMANDE_ID, "OK");

        assertThat(result.getStatut()).isEqualTo("approuve");
    }

    @Test
    @DisplayName("approveDemande() throws when not en_attente")
    void approveDemande_throws_whenNotEnAttente() {
        DemandeConge demande = buildDemande("approuve", 3.0);
        when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID)).thenReturn(Optional.of(demande));

        assertThrows(BusinessRuleException.class, () -> congeService.approveDemande(DEMANDE_ID, "note"));
    }

    @Test
    @DisplayName("approveDemande() moves enAttente to utilise")
    void approveDemande_movesBalance() {
        DemandeConge demande = buildDemande("en_attente", 3.0);
        BanqueConge banque = buildBanque(20.0, 5.0, 3.0);
        when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID)).thenReturn(Optional.of(demande));
        when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                .thenReturn(Optional.of(banque));
        when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);

        congeService.approveDemande(DEMANDE_ID, null);

        verify(banqueCongeRepository).save(captor.capture());
        assertThat(captor.getValue().getEnAttente()).isEqualTo(0.0);
        assertThat(captor.getValue().getUtilise()).isEqualTo(8.0);
    }

    @Test
    @DisplayName("refuseDemande() transitions and decrements enAttente")
    void refuseDemande_transitions() {
        DemandeConge demande = buildDemande("en_attente", 3.0);
        BanqueConge banque = buildBanque(20.0, 5.0, 3.0);
        when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID)).thenReturn(Optional.of(demande));
        when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                .thenReturn(Optional.of(banque));
        when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DemandeConge result = congeService.refuseDemande(DEMANDE_ID, "Motif");

        assertThat(result.getStatut()).isEqualTo("refuse");
    }

    @Test
    @DisplayName("deleteDemande() decrements enAttente for pending request")
    void deleteDemande_decrementsEnAttente() {
        DemandeConge demande = buildDemande("en_attente", 3.0);
        BanqueConge banque = buildBanque(20.0, 5.0, 3.0);
        when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID)).thenReturn(Optional.of(demande));
        when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                .thenReturn(Optional.of(banque));

        congeService.deleteDemande(DEMANDE_ID);

        verify(demandeCongeRepository).delete(demande);
    }
}
