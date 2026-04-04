package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.entity.*;
import com.schedy.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link ReplacementService}.
 *
 * <p>Vérifie les filtres de l'algorithme de suggestion de remplaçants :
 * congés, jours fériés, appartenance site, cap hebdo, conflit cross-site, disponibilité.
 *
 * <p>Semaine de référence : 2025-W02, Lundi = 2025-01-06.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReplacementService — suggestions de remplaçants")
class ReplacementServiceTest {

    private static final String ORG = "org1";
    private static final String SITE_A = "site-A";
    private static final String SITE_B = "site-B";
    private static final String SEMAINE = "2025-W02";
    private static final LocalDate LUNDI = LocalDate.of(2025, 1, 6);

    @Mock private EmployeRepository employeRepo;
    @Mock private CreneauAssigneRepository creneauRepo;
    @Mock private DemandeCongeRepository demandeCongeRepo;
    @Mock private AbsenceImprevueRepository absenceRepo;
    @Mock private JourFerieRepository jourFerieRepo;
    @Mock private ParametresRepository parametresRepo;
    @Mock private TenantContext tenantContext;

    @InjectMocks private ReplacementService service;

    /** Créneau de l'absent : lundi 8h-12h sur SITE_A */
    private CreneauAssigne creneauAbsent;

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.requireOrganisationId()).thenReturn(ORG);

        creneauAbsent = CreneauAssigne.builder()
                .id("creneau-absent")
                .employeId("absent")
                .siteId(SITE_A)
                .jour(0) // lundi
                .heureDebut(8.0)
                .heureFin(12.0)
                .semaine(SEMAINE)
                .organisationId(ORG)
                .build();

        lenient().when(creneauRepo.findByIdAndOrganisationId("creneau-absent", ORG))
                .thenReturn(Optional.of(creneauAbsent));

        // Default: no existing creneaux, no absences, no holidays
        lenient().when(creneauRepo.findBySemaineAndOrganisationId(SEMAINE, ORG))
                .thenReturn(new ArrayList<>(List.of(creneauAbsent)));
        lenient().when(demandeCongeRepo
                .findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        eq(ORG), eq(StatutDemande.approuve), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        lenient().when(jourFerieRepo.findByOrganisationId(ORG))
                .thenReturn(List.of());
        lenient().when(absenceRepo.findByOrganisationIdAndDateAbsence(eq(ORG), any()))
                .thenReturn(List.of());

        // Default parametres
        lenient().when(parametresRepo.findBySiteIdAndOrganisationId(SITE_A, ORG))
                .thenReturn(Optional.of(Parametres.builder()
                        .planningGranularite(1.0)
                        .heuresMaxSemaine(48.0)
                        .build()));
    }

    private Employe buildEmploye(String id, String nom, String role, String siteId,
                                  int jour, double hDebut, double hFin) {
        return Employe.builder()
                .id(id).nom(nom).role(role)
                .siteIds(List.of(siteId))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(jour).heureDebut(hDebut).heureFin(hFin).build()))
                .build();
    }

    // =========================================================================
    // 1. Cas nominal
    // =========================================================================

    @Test
    @DisplayName("cas nominal — un candidat disponible est suggéré")
    void cas_nominal_candidat_suggere() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        Employe candidat = buildEmploye("c1", "Candidat", "caissier", SITE_A, 0, 8.0, 16.0);
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent, candidat));

        var result = service.findReplacements("creneau-absent");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).employeId()).isEqualTo("c1");
        assertThat(result.get(0).score()).isGreaterThan(0);
    }

    // =========================================================================
    // 2. Filtre congé
    // =========================================================================

    @Test
    @DisplayName("congé jour complet — employé en congé exclu des suggestions")
    void conge_jour_complet_exclu() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        Employe enConge = buildEmploye("c1", "EnCongé", "caissier", SITE_A, 0, 8.0, 16.0);
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent, enConge));

        // Congé approuvé couvrant le lundi
        DemandeConge conge = DemandeConge.builder()
                .id("cg1").employeId("c1").typeCongeId("cp")
                .dateDebut(LUNDI).dateFin(LUNDI)
                .duree(1.0).statut(StatutDemande.approuve)
                .build();
        when(demandeCongeRepo
                .findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        ORG, StatutDemande.approuve, LUNDI, LUNDI))
                .thenReturn(List.of(conge));

        var result = service.findReplacements("creneau-absent");

        assertThat(result)
                .as("Employé en congé le lundi ne doit pas être suggéré")
                .isEmpty();
    }

    @Test
    @DisplayName("congé partiel chevauchant — employé exclu si congé overlap le créneau")
    void conge_partiel_chevauchant_exclu() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        Employe enConge = buildEmploye("c1", "EnCongé", "caissier", SITE_A, 0, 8.0, 16.0);
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent, enConge));

        // Congé partiel lundi 10h-14h → chevauche créneau 8h-12h
        DemandeConge conge = DemandeConge.builder()
                .id("cg1").employeId("c1").typeCongeId("cp")
                .dateDebut(LUNDI).dateFin(LUNDI)
                .heureDebut(10.0).heureFin(14.0)
                .duree(0.5).statut(StatutDemande.approuve)
                .build();
        when(demandeCongeRepo
                .findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        ORG, StatutDemande.approuve, LUNDI, LUNDI))
                .thenReturn(List.of(conge));

        var result = service.findReplacements("creneau-absent");

        assertThat(result)
                .as("Congé partiel 10h-14h chevauche créneau 8h-12h → exclu")
                .isEmpty();
    }

    @Test
    @DisplayName("congé partiel hors créneau — employé suggéré si congé ne chevauche pas")
    void conge_partiel_hors_creneau_suggere() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        Employe dispo = buildEmploye("c1", "Dispo", "caissier", SITE_A, 0, 8.0, 16.0);
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent, dispo));

        // Congé partiel lundi 14h-17h → PAS de chevauchement avec créneau 8h-12h
        DemandeConge conge = DemandeConge.builder()
                .id("cg1").employeId("c1").typeCongeId("cp")
                .dateDebut(LUNDI).dateFin(LUNDI)
                .heureDebut(14.0).heureFin(17.0)
                .duree(0.5).statut(StatutDemande.approuve)
                .build();
        when(demandeCongeRepo
                .findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        ORG, StatutDemande.approuve, LUNDI, LUNDI))
                .thenReturn(List.of(conge));

        var result = service.findReplacements("creneau-absent");

        assertThat(result)
                .as("Congé 14h-17h ne chevauche pas créneau 8h-12h → employé suggéré")
                .hasSize(1);
    }

    // =========================================================================
    // 3. Filtre jour férié
    // =========================================================================

    @Test
    @DisplayName("jour férié — aucune suggestion quand le jour est férié")
    void jour_ferie_aucune_suggestion() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        Employe candidat = buildEmploye("c1", "Candidat", "caissier", SITE_A, 0, 8.0, 16.0);
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent, candidat));

        // Lundi 2025-01-06 est férié
        JourFerie ferie = JourFerie.builder()
                .id("jf1").nom("Jour spécial")
                .date(LUNDI).recurrent(false)
                .build();
        when(jourFerieRepo.findByOrganisationId(ORG)).thenReturn(List.of(ferie));

        var result = service.findReplacements("creneau-absent");

        assertThat(result)
                .as("Jour férié → aucune suggestion de remplacement")
                .isEmpty();
    }

    @Test
    @DisplayName("jour férié site-spécifique — ne bloque pas les autres sites")
    void jour_ferie_autre_site_pas_bloque() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        Employe candidat = buildEmploye("c1", "Candidat", "caissier", SITE_A, 0, 8.0, 16.0);
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent, candidat));

        // Férié pour SITE_B uniquement
        JourFerie ferie = JourFerie.builder()
                .id("jf1").nom("Fête locale B")
                .date(LUNDI).recurrent(false)
                .siteId(SITE_B)
                .build();
        when(jourFerieRepo.findByOrganisationId(ORG)).thenReturn(List.of(ferie));

        var result = service.findReplacements("creneau-absent");

        assertThat(result)
                .as("Férié SITE_B ne bloque pas les suggestions pour SITE_A")
                .hasSize(1);
    }

    // =========================================================================
    // 4. Filtre site
    // =========================================================================

    @Test
    @DisplayName("site — employé d'un autre site exclu des suggestions")
    void site_autre_employe_exclu() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        // The site-scoped query returns only the absent employee for SITE_A;
        // autreSite (SITE_B only) would not be returned by the real DB query.
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent));

        var result = service.findReplacements("creneau-absent");

        assertThat(result)
                .as("Employé de SITE_B ne doit pas être suggéré pour un créneau SITE_A")
                .isEmpty();
    }

    @Test
    @DisplayName("site — employé multi-site est suggéré s'il appartient au bon site")
    void site_multi_site_suggere() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        Employe multiSite = Employe.builder()
                .id("c1").nom("MultiSite").role("caissier")
                .siteIds(List.of(SITE_A, SITE_B))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(16.0).build()))
                .build();
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent, multiSite));

        var result = service.findReplacements("creneau-absent");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).memeSite()).isTrue();
    }

    // =========================================================================
    // 5. Filtre cap hebdomadaire
    // =========================================================================

    @Test
    @DisplayName("cap hebdo — employé à la limite exclu")
    void cap_hebdo_employe_exclu() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        Employe surcharge = buildEmploye("c1", "Surchargé", "caissier", SITE_A, 0, 8.0, 16.0);
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent, surcharge));

        // c1 a déjà 45h cette semaine (jours 1-5 : 9h/jour = 45h)
        // + créneau absent 4h = 49h > 48h → exclu
        List<CreneauAssigne> creneaux = new ArrayList<>(List.of(creneauAbsent));
        for (int j = 1; j <= 5; j++) {
            creneaux.add(CreneauAssigne.builder()
                    .id("c-" + j).employeId("c1").siteId(SITE_A)
                    .jour(j).heureDebut(8.0).heureFin(17.0)  // 9h/jour × 5 = 45h
                    .semaine(SEMAINE).organisationId(ORG).build());
        }
        when(creneauRepo.findBySemaineAndOrganisationId(SEMAINE, ORG)).thenReturn(creneaux);

        var result = service.findReplacements("creneau-absent");

        assertThat(result)
                .as("Employé avec 45h ne peut pas prendre 4h de plus (49h > 48h)")
                .isEmpty();
    }

    // =========================================================================
    // 6. Filtre conflit cross-site
    // =========================================================================

    @Test
    @DisplayName("cross-site — employé déjà affecté même horaire sur autre site exclu")
    void cross_site_conflit_exclu() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        Employe dejaAffecte = Employe.builder()
                .id("c1").nom("DejaAffecté").role("caissier")
                .siteIds(List.of(SITE_A, SITE_B))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(16.0).build()))
                .build();
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent, dejaAffecte));

        // c1 est déjà affecté lundi 8h-12h sur SITE_B
        List<CreneauAssigne> creneaux = new ArrayList<>(List.of(creneauAbsent));
        creneaux.add(CreneauAssigne.builder()
                .id("c-conflit").employeId("c1").siteId(SITE_B)
                .jour(0).heureDebut(8.0).heureFin(12.0)
                .semaine(SEMAINE).organisationId(ORG).build());
        when(creneauRepo.findBySemaineAndOrganisationId(SEMAINE, ORG)).thenReturn(creneaux);

        var result = service.findReplacements("creneau-absent");

        assertThat(result)
                .as("Employé déjà affecté 8h-12h sur SITE_B ne peut pas remplacer sur SITE_A")
                .isEmpty();
    }

    // =========================================================================
    // 7. Filtre disponibilité
    // =========================================================================

    @Test
    @DisplayName("disponibilité — employé non disponible sur le créneau exclu")
    void disponibilite_non_disponible_exclu() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        // Disponible seulement l'après-midi, pas le matin
        Employe apremSeulement = buildEmploye("c1", "Aprem", "caissier", SITE_A, 0, 14.0, 18.0);
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent, apremSeulement));

        var result = service.findReplacements("creneau-absent");

        assertThat(result)
                .as("Employé disponible 14h-18h ne peut pas remplacer 8h-12h")
                .isEmpty();
    }

    // =========================================================================
    // 8. Score — rôle correspondant
    // =========================================================================

    @Test
    @DisplayName("score — même rôle obtient un score plus élevé que rôle différent")
    void score_meme_role_plus_eleve() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        Employe memeRole = buildEmploye("c1", "MêmeRôle", "caissier", SITE_A, 0, 8.0, 16.0);
        Employe autreRole = buildEmploye("c2", "AutreRôle", "serveur", SITE_A, 0, 8.0, 16.0);
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent, memeRole, autreRole));

        var result = service.findReplacements("creneau-absent");

        assertThat(result).hasSize(2);
        // Le premier (score le plus élevé) doit être celui avec le même rôle
        assertThat(result.get(0).employeId())
                .as("Employé avec même rôle doit être classé en premier")
                .isEqualTo("c1");
        assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
    }

    // =========================================================================
    // 9. Scénario complexe — combinaison de filtres
    // =========================================================================

    @Test
    @DisplayName("scénario complexe — seul le candidat valide parmi 5 est suggéré")
    void scenario_complexe_un_seul_valide() {
        Employe absent = buildEmploye("absent", "Absent", "caissier", SITE_A, 0, 8.0, 12.0);
        // c1 : en congé → exclu
        Employe enConge = buildEmploye("c1", "EnCongé", "caissier", SITE_A, 0, 8.0, 16.0);
        // c2 : autre site → exclu
        Employe autreSite = buildEmploye("c2", "AutreSite", "caissier", SITE_B, 0, 8.0, 16.0);
        // c3 : pas disponible le matin → exclu
        Employe pasDispo = buildEmploye("c3", "PasDispo", "caissier", SITE_A, 0, 14.0, 18.0);
        // c4 : a un conflit cross-site → exclu
        Employe conflit = Employe.builder()
                .id("c4").nom("Conflit").role("caissier")
                .siteIds(List.of(SITE_A, SITE_B))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(16.0).build()))
                .build();
        // c5 : OK → seul valide
        Employe valide = buildEmploye("c5", "Valide", "caissier", SITE_A, 0, 8.0, 16.0);

        // The site-scoped query returns all employees on SITE_A; autreSite (SITE_B only) is
        // excluded by the site membership filter inside the service stream.
        when(employeRepo.findBySiteIdsContainingAndOrganisationId(SITE_A, ORG))
                .thenReturn(List.of(absent, enConge, autreSite, pasDispo, conflit, valide));

        // c1 en congé lundi
        DemandeConge conge = DemandeConge.builder()
                .id("cg1").employeId("c1").typeCongeId("cp")
                .dateDebut(LUNDI).dateFin(LUNDI)
                .duree(1.0).statut(StatutDemande.approuve)
                .build();
        when(demandeCongeRepo
                .findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        ORG, StatutDemande.approuve, LUNDI, LUNDI))
                .thenReturn(List.of(conge));

        // c4 déjà affecté lundi 8h-12h sur SITE_B
        List<CreneauAssigne> creneaux = new ArrayList<>(List.of(creneauAbsent));
        creneaux.add(CreneauAssigne.builder()
                .id("c-conflit").employeId("c4").siteId(SITE_B)
                .jour(0).heureDebut(8.0).heureFin(12.0)
                .semaine(SEMAINE).organisationId(ORG).build());
        when(creneauRepo.findBySemaineAndOrganisationId(SEMAINE, ORG)).thenReturn(creneaux);

        var result = service.findReplacements("creneau-absent");

        assertThat(result)
                .as("Seul c5 (Valide) passe tous les filtres")
                .hasSize(1);
        assertThat(result.get(0).employeId()).isEqualTo("c5");
    }
}
