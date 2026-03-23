package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.DemandeCongeDto;
import com.schedy.entity.*;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DemandeCongeDto buildDto(double duree) {
        return new DemandeCongeDto(null, EMPLOYE_ID, TYPE_ID,
                LocalDate.now(), LocalDate.now().plusDays(1),
                null, null, duree, null, "Vacances", null, null);
    }

    private BanqueConge buildBanque(double quota, double utilise, double enAttente) {
        return BanqueConge.builder()
                .id("banque-1").employeId(EMPLOYE_ID).typeCongeId(TYPE_ID)
                .quota(quota).utilise(utilise).enAttente(enAttente)
                .organisationId(ORG_ID).build();
    }

    private TypeConge buildTypeConge(boolean quotaIllimite, boolean autoriserNegatif) {
        return TypeConge.builder()
                .id(TYPE_ID).nom("Conge paye")
                .categorie(CategorieConge.paye).unite(UniteConge.jours)
                .quotaIllimite(quotaIllimite).autoriserNegatif(autoriserNegatif)
                .organisationId(ORG_ID).build();
    }

    private DemandeConge buildDemande(StatutDemande statut, double duree) {
        return DemandeConge.builder()
                .id(DEMANDE_ID).employeId(EMPLOYE_ID).typeCongeId(TYPE_ID)
                .dateDebut(LocalDate.now()).dateFin(LocalDate.now().plusDays(1))
                .duree(duree).statut(statut).organisationId(ORG_ID).build();
    }

    // -------------------------------------------------------------------------
    // createDemande()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("createDemande()")
    class CreateDemande {

        @Test
        @DisplayName("always sets statut to en_attente regardless of DTO value")
        void createDemande_setsStatutEnAttente() {
            // quota sufficient: 20 - 5 - 2 = 13 available, requesting 3
            BanqueConge banque = buildBanque(20.0, 5.0, 2.0);
            TypeConge type = buildTypeConge(false, false);
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(type));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DemandeConge result = congeService.createDemande(buildDto(3.0));

            assertThat(result.getStatut()).isEqualTo(StatutDemande.en_attente);
        }

        @Test
        @DisplayName("increments banque.enAttente by the requested duree")
        void createDemande_updatesEnAttente() {
            BanqueConge banque = buildBanque(20.0, 5.0, 2.0);
            TypeConge type = buildTypeConge(false, false);
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(type));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);

            congeService.createDemande(buildDto(3.0));

            // banqueCongeRepository.save() is called twice: once for quota check (no save),
            // once for updating enAttente after the demande is saved.
            verify(banqueCongeRepository, atLeastOnce()).save(captor.capture());
            BanqueConge saved = captor.getValue();
            // enAttente was 2.0, must become 2.0 + 3.0 = 5.0
            assertThat(saved.getEnAttente()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("throws BusinessRuleException when quota is insufficient")
        void createDemande_quotaInsuffisant_throwsBusinessRuleException() {
            // quota: 20, utilise: 10, enAttente: 8  => disponible = 2, requesting 15
            BanqueConge banque = buildBanque(20.0, 10.0, 8.0);
            TypeConge type = buildTypeConge(false, false);
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(type));

            assertThrows(BusinessRuleException.class, () -> congeService.createDemande(buildDto(15.0)));
            verify(demandeCongeRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips quota check when typeConge.quotaIllimite is true")
        void createDemande_quotaIllimite_noQuotaCheck() {
            // Quota is exhausted on paper, but type is unlimited — request must succeed
            BanqueConge banque = buildBanque(5.0, 5.0, 0.0);
            TypeConge illimite = buildTypeConge(true, false);
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(illimite));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Should not throw even though disponible = 0
            DemandeConge result = congeService.createDemande(buildDto(10.0));

            assertThat(result.getStatut()).isEqualTo(StatutDemande.en_attente);
        }

        @Test
        @DisplayName("skips quota check when typeConge.autoriserNegatif is true")
        void createDemande_autoriserNegatif_noQuotaCheck() {
            BanqueConge banque = buildBanque(5.0, 5.0, 0.0);
            TypeConge negatifAllowed = buildTypeConge(false, true);
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(negatifAllowed));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DemandeConge result = congeService.createDemande(buildDto(10.0));

            assertThat(result.getStatut()).isEqualTo(StatutDemande.en_attente);
        }

        @Test
        @DisplayName("succeeds without touching banque when no banque exists for this employee/type")
        void createDemande_noBanque_noQuotaEnforced() {
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.empty());
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DemandeConge result = congeService.createDemande(buildDto(5.0));

            assertThat(result.getStatut()).isEqualTo(StatutDemande.en_attente);
            verify(banqueCongeRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // approveDemande()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("approveDemande()")
    class ApproveDemande {

        @Test
        @DisplayName("transitions statut from en_attente to approuve")
        void approveDemande_movesFromEnAttenteToApprouve() {
            DemandeConge demande = buildDemande(StatutDemande.en_attente, 3.0);
            BanqueConge banque = buildBanque(20.0, 5.0, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DemandeConge result = congeService.approveDemande(DEMANDE_ID, "OK");

            assertThat(result.getStatut()).isEqualTo(StatutDemande.approuve);
        }

        @Test
        @DisplayName("moves duree from enAttente to utilise in the banque")
        void approveDemande_movesFromEnAttenteToUtilise() {
            DemandeConge demande = buildDemande(StatutDemande.en_attente, 3.0);
            BanqueConge banque = buildBanque(20.0, 5.0, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);

            congeService.approveDemande(DEMANDE_ID, null);

            verify(banqueCongeRepository).save(captor.capture());
            BanqueConge saved = captor.getValue();
            // enAttente 3.0 - 3.0 = 0.0
            assertThat(saved.getEnAttente()).isEqualTo(0.0);
            // utilise 5.0 + 3.0 = 8.0
            assertThat(saved.getUtilise()).isEqualTo(8.0);
        }

        @Test
        @DisplayName("stores note d'approbation on the demande")
        void approveDemande_storesNote() {
            DemandeConge demande = buildDemande(StatutDemande.en_attente, 3.0);
            BanqueConge banque = buildBanque(20.0, 5.0, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DemandeConge result = congeService.approveDemande(DEMANDE_ID, "Approved by manager");

            assertThat(result.getNoteApprobation()).isEqualTo("Approved by manager");
        }

        @Test
        @DisplayName("throws BusinessRuleException when demande is not en_attente")
        void approveDemande_notEnAttente_throwsBusinessRuleException() {
            DemandeConge demande = buildDemande(StatutDemande.approuve, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));

            assertThrows(BusinessRuleException.class, () -> congeService.approveDemande(DEMANDE_ID, "note"));
            verify(banqueCongeRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws BusinessRuleException when demande is already refuse")
        void approveDemande_alreadyRefuse_throwsBusinessRuleException() {
            DemandeConge demande = buildDemande(StatutDemande.refuse, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));

            assertThrows(BusinessRuleException.class, () -> congeService.approveDemande(DEMANDE_ID, "note"));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when demande does not exist")
        void approveDemande_notFound_throwsResourceNotFoundException() {
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> congeService.approveDemande(DEMANDE_ID, "note"));
        }

        @Test
        @DisplayName("clamps enAttente to 0 when banque.enAttente is already below duree")
        void approveDemande_clampsEnAttenteToZero() {
            DemandeConge demande = buildDemande(StatutDemande.en_attente, 5.0);
            // enAttente is only 2.0 — should clamp to 0 rather than going negative
            BanqueConge banque = buildBanque(20.0, 3.0, 2.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);

            congeService.approveDemande(DEMANDE_ID, null);

            verify(banqueCongeRepository).save(captor.capture());
            assertThat(captor.getValue().getEnAttente()).isEqualTo(0.0);
        }
    }

    // -------------------------------------------------------------------------
    // refuseDemande()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("refuseDemande()")
    class RefuseDemande {

        @Test
        @DisplayName("transitions statut from en_attente to refuse")
        void refuseDemande_transitions() {
            DemandeConge demande = buildDemande(StatutDemande.en_attente, 3.0);
            BanqueConge banque = buildBanque(20.0, 5.0, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DemandeConge result = congeService.refuseDemande(DEMANDE_ID, "Motif");

            assertThat(result.getStatut()).isEqualTo(StatutDemande.refuse);
        }

        @Test
        @DisplayName("rolls back enAttente in the banque when refusal occurs")
        void refuseDemande_rollsBackEnAttente() {
            DemandeConge demande = buildDemande(StatutDemande.en_attente, 3.0);
            BanqueConge banque = buildBanque(20.0, 5.0, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);

            congeService.refuseDemande(DEMANDE_ID, "Motif");

            verify(banqueCongeRepository).save(captor.capture());
            // enAttente 3.0 - 3.0 = 0.0 rolled back
            assertThat(captor.getValue().getEnAttente()).isEqualTo(0.0);
            // utilise must NOT change
            assertThat(captor.getValue().getUtilise()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("throws BusinessRuleException when demande is not en_attente")
        void refuseDemande_notEnAttente_throwsBusinessRuleException() {
            DemandeConge demande = buildDemande(StatutDemande.approuve, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));

            assertThrows(BusinessRuleException.class, () -> congeService.refuseDemande(DEMANDE_ID, "note"));
            verify(banqueCongeRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // deleteDemande()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteDemande()")
    class DeleteDemande {

        @Test
        @DisplayName("rolls back enAttente when deleting a pending request")
        void deleteDemande_pending_rollsBackEnAttente() {
            DemandeConge demande = buildDemande(StatutDemande.en_attente, 3.0);
            BanqueConge banque = buildBanque(20.0, 5.0, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);

            congeService.deleteDemande(DEMANDE_ID);

            verify(banqueCongeRepository).save(captor.capture());
            assertThat(captor.getValue().getEnAttente()).isEqualTo(0.0);
            verify(demandeCongeRepository).delete(demande);
        }

        @Test
        @DisplayName("does not touch banque when deleting an approved request")
        void deleteDemande_approved_noRollback() {
            DemandeConge demande = buildDemande(StatutDemande.approuve, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));

            congeService.deleteDemande(DEMANDE_ID);

            verify(banqueCongeRepository, never()).save(any());
            verify(banqueCongeRepository, never())
                    .findByEmployeIdAndTypeCongeIdAndOrganisationId(anyString(), anyString(), anyString());
            verify(demandeCongeRepository).delete(demande);
        }

        @Test
        @DisplayName("does not touch banque when deleting a refused request")
        void deleteDemande_refused_noRollback() {
            DemandeConge demande = buildDemande(StatutDemande.refuse, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));

            congeService.deleteDemande(DEMANDE_ID);

            verify(banqueCongeRepository, never()).save(any());
            verify(demandeCongeRepository).delete(demande);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when demande does not exist")
        void deleteDemande_notFound_throwsResourceNotFoundException() {
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> congeService.deleteDemande(DEMANDE_ID));
        }
    }

    // -------------------------------------------------------------------------
    // deleteType()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteType()")
    class DeleteType {

        @Test
        @DisplayName("throws BusinessRuleException when approved demandes exist for this type")
        void deleteType_withApprovedDemandes_throwsBusinessRuleException() {
            TypeConge type = buildTypeConge(false, false);
            DemandeConge approved = buildDemande(StatutDemande.approuve, 3.0);
            when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(type));
            when(demandeCongeRepository.findByTypeCongeIdAndStatutAndOrganisationId(
                    TYPE_ID, StatutDemande.approuve, ORG_ID))
                    .thenReturn(List.of(approved));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> congeService.deleteType(TYPE_ID));
            assertThat(ex.getMessage()).contains("1");
            verify(typeCongeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("cascades cleanup of demandes and banques before deleting the type")
        void deleteType_cascadesCleanup() {
            TypeConge type = buildTypeConge(false, false);
            when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(type));
            when(demandeCongeRepository.findByTypeCongeIdAndStatutAndOrganisationId(
                    TYPE_ID, StatutDemande.approuve, ORG_ID))
                    .thenReturn(List.of());

            congeService.deleteType(TYPE_ID);

            verify(demandeCongeRepository).deleteByTypeCongeIdAndOrganisationId(TYPE_ID, ORG_ID);
            verify(banqueCongeRepository).deleteByTypeCongeIdAndOrganisationId(TYPE_ID, ORG_ID);
            verify(typeCongeRepository).delete(type);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when type does not exist")
        void deleteType_notFound_throwsResourceNotFoundException() {
            when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> congeService.deleteType(TYPE_ID));
        }
    }
}
