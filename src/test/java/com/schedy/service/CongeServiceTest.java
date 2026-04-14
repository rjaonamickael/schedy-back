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

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CongeService unit tests")
class CongeServiceTest {

    @Mock private TypeCongeRepository typeCongeRepository;
    @Mock private BanqueCongeRepository banqueCongeRepository;
    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private JourFerieRepository jourFerieRepository;
    @Mock private EmployeRepository employeRepository;
    @Mock private TenantContext tenantContext;

    @InjectMocks private CongeService congeService;

    private static final String ORG_ID = "org-123";
    private static final String EMPLOYE_ID = "emp-456";
    private static final String TYPE_ID = "type-789";
    private static final String DEMANDE_ID = "dem-111";

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
        // SecurityContext for deleteDemande which checks roles
        var auth = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
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

    private TypeConge buildTypeConge(boolean sansLimite, boolean autoriserDepassement) {
        return TypeConge.builder()
                .id(TYPE_ID).nom("Conge paye")
                .paye(true).unite(UniteConge.jours)
                .typeLimite(sansLimite ? TypeLimite.AUCUNE : TypeLimite.ENVELOPPE_ANNUELLE)
                .autoriserDepassement(autoriserDepassement)
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
            when(banqueCongeRepository.findForUpdate(EMPLOYE_ID, TYPE_ID, ORG_ID))
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
            when(banqueCongeRepository.findForUpdate(EMPLOYE_ID, TYPE_ID, ORG_ID))
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
            when(banqueCongeRepository.findForUpdate(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(type));

            assertThrows(BusinessRuleException.class, () -> congeService.createDemande(buildDto(15.0)));
            verify(demandeCongeRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips quota check when typeLimite is AUCUNE")
        void createDemande_sansLimite_noQuotaCheck() {
            // Quota is exhausted on paper, but type is unlimited — request must succeed
            BanqueConge banque = buildBanque(5.0, 5.0, 0.0);
            TypeConge sansLimite = buildTypeConge(true, false);
            when(banqueCongeRepository.findForUpdate(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(sansLimite));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Should not throw even though disponible = 0
            DemandeConge result = congeService.createDemande(buildDto(10.0));

            assertThat(result.getStatut()).isEqualTo(StatutDemande.en_attente);
        }

        @Test
        @DisplayName("skips quota check when typeConge.autoriserDepassement is true")
        void createDemande_autoriserDepassement_noQuotaCheck() {
            BanqueConge banque = buildBanque(5.0, 5.0, 0.0);
            TypeConge depassementAllowed = buildTypeConge(false, true);
            when(banqueCongeRepository.findForUpdate(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(typeCongeRepository.findByIdAndOrganisationId(TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(depassementAllowed));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DemandeConge result = congeService.createDemande(buildDto(10.0));

            assertThat(result.getStatut()).isEqualTo(StatutDemande.en_attente);
        }

        @Test
        @DisplayName("succeeds without touching banque when no banque exists for this employee/type")
        void createDemande_noBanque_noQuotaEnforced() {
            when(banqueCongeRepository.findForUpdate(EMPLOYE_ID, TYPE_ID, ORG_ID))
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
            when(banqueCongeRepository.findForUpdate(EMPLOYE_ID, TYPE_ID, ORG_ID))
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
            when(banqueCongeRepository.findForUpdate(EMPLOYE_ID, TYPE_ID, ORG_ID))
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
            when(banqueCongeRepository.findForUpdate(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DemandeConge result = congeService.approveDemande(DEMANDE_ID, "Approved by manager");

            assertThat(result.getNoteApprobation()).isEqualTo("Approved by manager");
        }

        @Test
        @DisplayName("throws BusinessRuleException when demande is already approuve")
        void approveDemande_alreadyApprouve_throwsBusinessRuleException() {
            DemandeConge demande = buildDemande(StatutDemande.approuve, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));

            assertThrows(BusinessRuleException.class, () -> congeService.approveDemande(DEMANDE_ID, "note"));
            verify(banqueCongeRepository, never()).save(any());
        }

        @Test
        @DisplayName("allows approving a previously refused demande (refuse → approuve)")
        void approveDemande_fromRefuse_succeeds() {
            DemandeConge demande = buildDemande(StatutDemande.refuse, 3.0);
            BanqueConge banque = buildBanque(20.0, 5.0, 0.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));
            when(banqueCongeRepository.findForUpdate(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);

            DemandeConge result = congeService.approveDemande(DEMANDE_ID, "Réapprouvé");

            assertThat(result.getStatut()).isEqualTo(StatutDemande.approuve);
            verify(banqueCongeRepository).save(captor.capture());
            // Was refused: utilise goes up, enAttente unchanged
            assertThat(captor.getValue().getUtilise()).isEqualTo(8.0); // 5+3
            assertThat(captor.getValue().getEnAttente()).isEqualTo(0.0);
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
            when(banqueCongeRepository.findForUpdate(EMPLOYE_ID, TYPE_ID, ORG_ID))
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
        @DisplayName("rolls back enAttente in the banque when refusing an en_attente request")
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
            assertThat(captor.getValue().getEnAttente()).isEqualTo(0.0);
            assertThat(captor.getValue().getUtilise()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("reverses utilise when refusing a previously approved request")
        void refuseDemande_fromApproved_reversesUtilise() {
            DemandeConge demande = buildDemande(StatutDemande.approuve, 3.0);
            BanqueConge banque = buildBanque(20.0, 8.0, 0.0); // 8h utilise
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));
            when(banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(EMPLOYE_ID, TYPE_ID, ORG_ID))
                    .thenReturn(Optional.of(banque));
            when(demandeCongeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);

            DemandeConge result = congeService.refuseDemande(DEMANDE_ID, "Annulé");

            assertThat(result.getStatut()).isEqualTo(StatutDemande.refuse);
            verify(banqueCongeRepository).save(captor.capture());
            // utilise 8.0 - 3.0 = 5.0 (reversed)
            assertThat(captor.getValue().getUtilise()).isEqualTo(5.0);
            // enAttente unchanged (was 0)
            assertThat(captor.getValue().getEnAttente()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("throws BusinessRuleException when demande is already refuse")
        void refuseDemande_alreadyRefuse_throwsBusinessRuleException() {
            DemandeConge demande = buildDemande(StatutDemande.refuse, 3.0);
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

            congeService.deleteDemande(DEMANDE_ID);

            verify(demandeCongeRepository).delete(demande);
        }

        @Test
        @DisplayName("does not throw when deleting an approved request")
        void deleteDemande_approved_noThrow() {
            DemandeConge demande = buildDemande(StatutDemande.approuve, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));

            congeService.deleteDemande(DEMANDE_ID);

            verify(demandeCongeRepository).delete(demande);
        }

        @Test
        @DisplayName("does not throw when deleting a refused request")
        void deleteDemande_refused_noThrow() {
            DemandeConge demande = buildDemande(StatutDemande.refuse, 3.0);
            when(demandeCongeRepository.findByIdAndOrganisationId(DEMANDE_ID, ORG_ID))
                    .thenReturn(Optional.of(demande));

            congeService.deleteDemande(DEMANDE_ID);

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

    // -------------------------------------------------------------------------
    // provisionBanquesForType() / provisionBanquesForEmploye()
    //
    // Auto-provisioning enforces the invariant : every (employe, type, org) triple
    // has exactly one BanqueConge. These tests cover both directions of the contract
    // and the idempotency guarantee.
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("provisionBanquesForType()")
    class ProvisionBanquesForType {

        private TypeConge buildEnvelopeType(double quotaAnnuel) {
            return TypeConge.builder()
                    .id(TYPE_ID).nom("Conge paye")
                    .paye(true).unite(UniteConge.jours)
                    .typeLimite(TypeLimite.ENVELOPPE_ANNUELLE)
                    .quotaAnnuel(quotaAnnuel)
                    .organisationId(ORG_ID).build();
        }

        private TypeConge buildAccrualType() {
            return TypeConge.builder()
                    .id(TYPE_ID).nom("Vacances accumulees")
                    .paye(true).unite(UniteConge.jours)
                    .typeLimite(TypeLimite.ACCRUAL)
                    .accrualMontant(2.5)
                    .organisationId(ORG_ID).build();
        }

        private TypeConge buildUnlimitedType() {
            return TypeConge.builder()
                    .id(TYPE_ID).nom("Maladie")
                    .paye(true).unite(UniteConge.jours)
                    .typeLimite(TypeLimite.AUCUNE)
                    .organisationId(ORG_ID).build();
        }

        private Employe buildEmploye(String id) {
            return Employe.builder().id(id).nom("Emp " + id).organisationId(ORG_ID).build();
        }

        @Test
        @DisplayName("creates one banque per employee for an ENVELOPPE_ANNUELLE type")
        void provisionForType_envelope_createsOnePerEmploye() {
            TypeConge type = buildEnvelopeType(25.0);
            when(employeRepository.findByOrganisationId(ORG_ID))
                    .thenReturn(List.of(buildEmploye("e1"), buildEmploye("e2"), buildEmploye("e3")));
            when(banqueCongeRepository.findByTypeCongeId(TYPE_ID)).thenReturn(List.of());

            congeService.provisionBanquesForType(type, ORG_ID);

            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);
            verify(banqueCongeRepository, times(3)).save(captor.capture());
            // All three banques must use the type's quota_annuel as default
            captor.getAllValues().forEach(b -> {
                assertThat(b.getQuota()).isEqualTo(25.0);
                assertThat(b.getUtilise()).isEqualTo(0.0);
                assertThat(b.getEnAttente()).isEqualTo(0.0);
                assertThat(b.getTypeCongeId()).isEqualTo(TYPE_ID);
                assertThat(b.getOrganisationId()).isEqualTo(ORG_ID);
            });
        }

        @Test
        @DisplayName("starts ACCRUAL banques at quota=0 (scheduler will credit)")
        void provisionForType_accrual_startsAtZero() {
            TypeConge type = buildAccrualType();
            when(employeRepository.findByOrganisationId(ORG_ID))
                    .thenReturn(List.of(buildEmploye("e1")));
            when(banqueCongeRepository.findByTypeCongeId(TYPE_ID)).thenReturn(List.of());

            congeService.provisionBanquesForType(type, ORG_ID);

            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);
            verify(banqueCongeRepository).save(captor.capture());
            assertThat(captor.getValue().getQuota()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("AUCUNE banques have null quota (= unlimited)")
        void provisionForType_aucune_nullQuota() {
            TypeConge type = buildUnlimitedType();
            when(employeRepository.findByOrganisationId(ORG_ID))
                    .thenReturn(List.of(buildEmploye("e1")));
            when(banqueCongeRepository.findByTypeCongeId(TYPE_ID)).thenReturn(List.of());

            congeService.provisionBanquesForType(type, ORG_ID);

            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);
            verify(banqueCongeRepository).save(captor.capture());
            assertThat(captor.getValue().getQuota()).isNull();
        }

        @Test
        @DisplayName("is idempotent : skips employees that already have a banque for this type")
        void provisionForType_idempotent() {
            TypeConge type = buildEnvelopeType(25.0);
            BanqueConge existingForE1 = BanqueConge.builder()
                    .id("b-existing").employeId("e1").typeCongeId(TYPE_ID)
                    .quota(99.0)  // a manual override that must NOT be touched
                    .organisationId(ORG_ID).build();
            when(employeRepository.findByOrganisationId(ORG_ID))
                    .thenReturn(List.of(buildEmploye("e1"), buildEmploye("e2")));
            when(banqueCongeRepository.findByTypeCongeId(TYPE_ID)).thenReturn(List.of(existingForE1));

            congeService.provisionBanquesForType(type, ORG_ID);

            // Only e2 should get a new banque ; e1's override is preserved
            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);
            verify(banqueCongeRepository, times(1)).save(captor.capture());
            assertThat(captor.getValue().getEmployeId()).isEqualTo("e2");
        }

        @Test
        @DisplayName("no-ops when org has no employees")
        void provisionForType_noEmployes() {
            TypeConge type = buildEnvelopeType(25.0);
            when(employeRepository.findByOrganisationId(ORG_ID)).thenReturn(List.of());

            congeService.provisionBanquesForType(type, ORG_ID);

            verify(banqueCongeRepository, never()).save(any());
        }

        @Test
        @DisplayName("uses 0 as fallback when ENVELOPPE_ANNUELLE has null quota_annuel")
        void provisionForType_envelopeWithNullQuota_fallsBackToZero() {
            TypeConge type = buildEnvelopeType(0.0);
            type.setQuotaAnnuel(null);
            when(employeRepository.findByOrganisationId(ORG_ID))
                    .thenReturn(List.of(buildEmploye("e1")));
            when(banqueCongeRepository.findByTypeCongeId(TYPE_ID)).thenReturn(List.of());

            congeService.provisionBanquesForType(type, ORG_ID);

            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);
            verify(banqueCongeRepository).save(captor.capture());
            assertThat(captor.getValue().getQuota()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("provisionBanquesForEmploye()")
    class ProvisionBanquesForEmploye {

        private TypeConge buildType(String id, TypeLimite limite, Double quota) {
            return TypeConge.builder()
                    .id(id).nom("Type " + id)
                    .paye(true).unite(UniteConge.jours)
                    .typeLimite(limite).quotaAnnuel(quota)
                    .organisationId(ORG_ID).build();
        }

        @Test
        @DisplayName("creates one banque per existing type when employee joins")
        void provisionForEmploye_createsOnePerType() {
            when(typeCongeRepository.findByOrganisationId(ORG_ID)).thenReturn(List.of(
                    buildType("t1", TypeLimite.ENVELOPPE_ANNUELLE, 25.0),
                    buildType("t2", TypeLimite.AUCUNE, null),
                    buildType("t3", TypeLimite.ACCRUAL, null)
            ));
            when(banqueCongeRepository.findByEmployeIdAndOrganisationId(EMPLOYE_ID, ORG_ID))
                    .thenReturn(List.of());

            congeService.provisionBanquesForEmploye(EMPLOYE_ID, ORG_ID);

            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);
            verify(banqueCongeRepository, times(3)).save(captor.capture());
            // Verify each type's expected initial quota
            var byType = captor.getAllValues().stream()
                    .collect(java.util.stream.Collectors.toMap(BanqueConge::getTypeCongeId, b -> b));
            assertThat(byType.get("t1").getQuota()).isEqualTo(25.0);
            assertThat(byType.get("t2").getQuota()).isNull();
            assertThat(byType.get("t3").getQuota()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("is idempotent : skips types that already have a banque")
        void provisionForEmploye_idempotent() {
            BanqueConge existing = BanqueConge.builder()
                    .employeId(EMPLOYE_ID).typeCongeId("t1")
                    .quota(50.0).organisationId(ORG_ID).build();
            when(typeCongeRepository.findByOrganisationId(ORG_ID)).thenReturn(List.of(
                    buildType("t1", TypeLimite.ENVELOPPE_ANNUELLE, 25.0),
                    buildType("t2", TypeLimite.ENVELOPPE_ANNUELLE, 10.0)
            ));
            when(banqueCongeRepository.findByEmployeIdAndOrganisationId(EMPLOYE_ID, ORG_ID))
                    .thenReturn(List.of(existing));

            congeService.provisionBanquesForEmploye(EMPLOYE_ID, ORG_ID);

            ArgumentCaptor<BanqueConge> captor = ArgumentCaptor.forClass(BanqueConge.class);
            verify(banqueCongeRepository, times(1)).save(captor.capture());
            assertThat(captor.getValue().getTypeCongeId()).isEqualTo("t2");
        }

        @Test
        @DisplayName("no-ops when org has no leave types")
        void provisionForEmploye_noTypes() {
            when(typeCongeRepository.findByOrganisationId(ORG_ID)).thenReturn(List.of());

            congeService.provisionBanquesForEmploye(EMPLOYE_ID, ORG_ID);

            verify(banqueCongeRepository, never()).save(any());
        }
    }
}
