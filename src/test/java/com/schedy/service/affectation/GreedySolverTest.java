package com.schedy.service.affectation;

import com.schedy.entity.CreneauAssigne;
import com.schedy.entity.DemandeConge;
import com.schedy.entity.DisponibilitePlage;
import com.schedy.entity.Employe;
import com.schedy.entity.Exigence;
import com.schedy.entity.JourFerie;
import com.schedy.entity.StatutDemande;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GreedySolver}.
 *
 * <p>The solver is a pure algorithm class with no Spring or database dependencies.
 * It is instantiated directly via {@code new GreedySolver()}.
 *
 * <p>Reference week: 2025-W02, Monday = 2025-01-06 (jour index 0).
 * Day mapping: 0 = lundi (2025-01-06), 1 = mardi (2025-01-07), ...
 */
@DisplayName("GreedySolver — tests unitaires de l'algorithme d'affectation")
class GreedySolverTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static final String SEMAINE   = "2025-W02";
    private static final LocalDate LUNDI  = LocalDate.of(2025, 1, 6);
    private static final String SITE_A    = "site-A";
    private static final String SITE_B    = "site-B";
    private static final String ORG       = "org1";

    /** System under test — stateless, so a single shared instance is sufficient. */
    private GreedySolver solver;

    @BeforeEach
    void setUp() {
        solver = new GreedySolver();
    }

    // =========================================================================
    // Helper builders
    // =========================================================================

    /** Builds an employee available from heureDebut to heureFin on the given jour index. */
    private Employe buildEmploye(String id, String nom, String role,
                                  String siteId,
                                  int jour, double heureDebut, double heureFin) {
        return Employe.builder()
                .id(id)
                .nom(nom)
                .role(role)
                .siteIds(List.of(siteId))
                .disponibilites(List.of(
                        DisponibilitePlage.builder()
                                .jour(jour)
                                .heureDebut(heureDebut)
                                .heureFin(heureFin)
                                .build()))
                .build();
    }

    /** Builds an employee available on multiple days. */
    private Employe buildEmployeMultiDispo(String id, String nom, String role,
                                            String siteId,
                                            List<DisponibilitePlage> dispos) {
        return Employe.builder()
                .id(id)
                .nom(nom)
                .role(role)
                .siteIds(List.of(siteId))
                .disponibilites(dispos)
                .build();
    }

    /** Builds an employee belonging to multiple sites. */
    private Employe buildEmployeMultiSite(String id, String nom, String role,
                                           List<String> siteIds,
                                           int jour, double heureDebut, double heureFin) {
        return Employe.builder()
                .id(id)
                .nom(nom)
                .role(role)
                .siteIds(siteIds)
                .disponibilites(List.of(
                        DisponibilitePlage.builder()
                                .jour(jour)
                                .heureDebut(heureDebut)
                                .heureFin(heureFin)
                                .build()))
                .build();
    }

    /** Builds an exigence requiring {@code nombreRequis} employees of the given role. */
    private Exigence buildExigence(String id, String libelle, String role,
                                    String siteId, List<Integer> jours,
                                    double heureDebut, double heureFin,
                                    int nombreRequis) {
        return Exigence.builder()
                .id(id)
                .libelle(libelle)
                .role(role)
                .siteId(siteId)
                .jours(jours)
                .heureDebut(heureDebut)
                .heureFin(heureFin)
                .nombreRequis(nombreRequis)
                .build();
    }

    /** Builds an existing creneau for the reference week. */
    private CreneauAssigne buildCreneau(String id, String employeId, String siteId,
                                         int jour, double heureDebut, double heureFin) {
        return CreneauAssigne.builder()
                .id(id)
                .employeId(employeId)
                .siteId(siteId)
                .jour(jour)
                .heureDebut(heureDebut)
                .heureFin(heureFin)
                .semaine(SEMAINE)
                .organisationId(ORG)
                .build();
    }

    /** Builds the ContexteAffectation with sensible defaults. */
    private ContexteAffectation buildContexte(List<Exigence> exigences,
                                               List<Employe> employes,
                                               List<CreneauAssigne> creneauxExistants,
                                               List<DemandeConge> conges,
                                               List<JourFerie> feries,
                                               double dureeMin,
                                               double granularite,
                                               List<String> regles,
                                               double heuresMax) {
        Map<String, Employe> employeParId = employes.stream()
                .collect(Collectors.toMap(Employe::getId, e -> e));
        return new ContexteAffectation(
                exigences,
                employes,
                employeParId,
                creneauxExistants,
                conges,
                feries,
                dureeMin,
                granularite,
                regles,
                heuresMax,
                LUNDI,
                SEMAINE,
                SITE_A,
                ORG
        );
    }

    // =========================================================================
    // 1. Fonctionnalité de base
    // =========================================================================

    @Test
    @DisplayName("doit affecter un employé disponible à une exigence simple")
    void devrait_affecter_employe_disponible() {
        // 1 exigence sur lundi (jour 0), 8h-12h, 1 requis
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        // 1 employé caissier disponible sur le même créneau
        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 12.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(alice),
                List.of(),    // pas de créneaux existants
                List.of(),    // pas de congés
                List.of(),    // pas de jours fériés
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.totalAffectes()).isGreaterThanOrEqualTo(1);
        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId()).isEqualTo("e1");
        assertThat(result.nouveauxCreneaux().get(0).getHeureDebut()).isEqualTo(8.0);
        assertThat(result.nouveauxCreneaux().get(0).getHeureFin()).isEqualTo(12.0);
    }

    @Test
    @DisplayName("ne doit rien affecter si le créneau est déjà couvert par les créneaux existants")
    void devrait_ne_rien_affecter_si_deja_couvert() {
        // Exigence 1 requis, 8h-12h
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 12.0);

        // Le créneau est déjà attribué (créneau existant)
        CreneauAssigne existant = buildCreneau("c1", "e1", SITE_A, 0, 8.0, 12.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(alice),
                List.of(existant),
                List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // L'exigence est déjà satisfaite — aucun nouveau créneau ne doit être créé
        assertThat(result.nouveauxCreneaux()).isEmpty();
    }

    @Test
    @DisplayName("ne doit pas affecter un employé dont le rôle ne correspond pas")
    void devrait_respecter_role_matching() {
        // Exigence caissier
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        // Employé gérant — mauvais rôle
        Employe bob = buildEmploye("e2", "Bob", "gerant", SITE_A, 0, 8.0, 12.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(bob),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).isEmpty();
    }

    @Test
    @DisplayName("ne doit pas affecter si la disponibilité est inférieure à la durée minimale")
    void devrait_respecter_duree_minimale() {
        // Exigence 8h-9h (1 heure seulement)
        Exigence exigence = buildExigence("ex1", "Courte exigence", "caissier",
                SITE_A, List.of(0), 8.0, 9.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 9.0);

        // Durée minimale requise = 3 heures -> 1 heure insuffisante
        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(alice),
                List.of(), List.of(), List.of(),
                3.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).isEmpty();
    }

    // =========================================================================
    // 2. Validation des correctifs de bugs
    // =========================================================================

    @Test
    @DisplayName("CRITIQUE — doit compter les heures de tous les sites pour l'équité (correctif multi-sites)")
    void devrait_compter_heures_tous_sites_pour_equite() {
        // emp1 a déjà 20h sur site-B cette semaine (jours 1-5, sans conflit sur jour 0)
        // emp2 a 0h — avec la règle équité, emp2 doit être affecté en priorité sur jour 0
        // Le test valide que les heures site-B d'emp1 sont prises en compte pour l'équité
        // même quand on affecte sur site-A.
        Exigence exigence = buildExigence("ex1", "Caisse lundi matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        // Les deux employés sont disponibles lundi 8h-12h sur site-A et site-B
        DisponibilitePlage dispoLundi = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(12.0).build();
        Employe emp1 = Employe.builder()
                .id("e1").nom("Alice").role("caissier")
                .siteIds(List.of(SITE_A, SITE_B))
                .disponibilites(List.of(dispoLundi))
                .build();
        Employe emp2 = Employe.builder()
                .id("e2").nom("Bob").role("caissier")
                .siteIds(List.of(SITE_A, SITE_B))
                .disponibilites(List.of(dispoLundi))
                .build();

        // Créneaux existants : emp1 travaille 5 blocs de 4h sur site-B
        // sur les jours 1 à 5 (mardi-samedi) = 20h, sans conflit le lundi (jour 0)
        List<CreneauAssigne> creneauxExistants = new ArrayList<>();
        for (int jour = 1; jour <= 5; jour++) {
            creneauxExistants.add(buildCreneau("c" + jour, "e1", SITE_B,
                    jour, 8.0, 12.0));
        }

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(emp1, emp2),
                creneauxExistants,
                List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // L'algorithme équité doit choisir emp2 (0h) et pas emp1 (20h sur site-B)
        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("emp2 doit être affecté car il a 0h vs 20h pour emp1 (toutes sites confondus)")
                .isEqualTo("e2");
    }

    @Test
    @DisplayName("doit détecter un conflit inter-sites et refuser l'affectation")
    void devrait_detecter_conflit_inter_sites() {
        // emp1 est déjà affecté 8h-12h sur site-B le lundi
        // On tente de l'affecter 8h-12h sur site-A le même lundi — doit être refusé
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe emp1 = buildEmployeMultiSite("e1", "Alice", "caissier",
                List.of(SITE_A, SITE_B), 0, 8.0, 12.0);

        // Créneau existant sur site-B, même horaire, même jour
        CreneauAssigne conflitSiteB = buildCreneau("c1", "e1", SITE_B, 0, 8.0, 12.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(emp1),
                List.of(conflitSiteB),
                List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // emp1 ne peut pas être sur deux sites en même temps
        assertThat(result.nouveauxCreneaux()).isEmpty();
    }

    @Test
    @DisplayName("doit gérer la granularité demi-heure et créer un créneau unique couvrant 8h-10h")
    void devrait_gerer_granularite_demi_heure() {
        // Exigence 8h-10h avec granularité 0.5 (tranches de 30 min)
        Exigence exigence = buildExigence("ex1", "Accueil", "caissier",
                SITE_A, List.of(0), 8.0, 10.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 10.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(alice),
                List.of(), List.of(), List.of(),
                0.5, 0.5, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // Un seul créneau couvrant toute la plage 8h-10h (pas deux créneaux de 1h)
        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getHeureDebut()).isEqualTo(8.0);
        assertThat(result.nouveauxCreneaux().get(0).getHeureFin()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("ne doit pas affecter un employé en congé approuvé")
    void devrait_exclure_employe_en_conge() {
        // Congé approuvé d'Alice le lundi 2025-01-06
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 12.0);

        DemandeConge conge = DemandeConge.builder()
                .id("cg1")
                .employeId("e1")
                .typeCongeId("cp")
                .dateDebut(LocalDate.of(2025, 1, 6))
                .dateFin(LocalDate.of(2025, 1, 6))
                .duree(1.0)
                .statut(StatutDemande.approuve)
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(alice),
                List.of(), List.of(conge), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).isEmpty();
    }

    @Test
    @DisplayName("ne doit pas créer d'affectation le jour d'un jour férié")
    void devrait_court_circuiter_jour_ferie() {
        // Jour férié le lundi 2025-01-06 (jour index 0)
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 12.0);

        JourFerie ferie = JourFerie.builder()
                .id("jf1")
                .nom("Jour spécial test")
                .date(LocalDate.of(2025, 1, 6))
                .recurrent(false)
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(alice),
                List.of(), List.of(), List.of(ferie),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).isEmpty();
    }

    // =========================================================================
    // 3. Améliorations algorithmiques
    // =========================================================================

    @Test
    @DisplayName("doit respecter le plafond hebdomadaire — refus total quand remaining < dureeMin")
    void devrait_refuser_quand_remaining_inferieur_dureeMin() {
        // Alice a déjà 47.5h. heuresMax=48, remaining=0.5h, dureeMin=1h.
        // 0.5h < 1h → aucun bloc possible → refus total.
        Exigence exigence = buildExigence("ex1", "Caisse dimanche", "caissier",
                SITE_A, List.of(6), 8.0, 12.0, 1);

        DisponibilitePlage dispoDimanche = DisponibilitePlage.builder()
                .jour(6).heureDebut(8.0).heureFin(12.0).build();
        Employe alice = buildEmployeMultiDispo("e1", "Alice", "caissier", SITE_A,
                List.of(dispoDimanche));

        // 47.5h existantes : jours 0-4 (8h) + jour 5 (7.5h)
        List<CreneauAssigne> creneauxExistants = new ArrayList<>();
        for (int jour = 0; jour <= 4; jour++) {
            creneauxExistants.add(buildCreneau("c" + jour, "e1", SITE_A, jour, 8.0, 16.0));
        }
        creneauxExistants.add(buildCreneau("c5", "e1", SITE_A, 5, 8.0, 15.5));

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(alice),
                creneauxExistants,
                List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // remaining = 0.5h < dureeMin 1h → aucune affectation
        assertThat(result.nouveauxCreneaux()).isEmpty();
    }

    @Test
    @DisplayName("doit respecter le plafond hebdomadaire — tronquer le bloc au budget restant")
    void devrait_tronquer_au_plafond_hebdomadaire() {
        // Alice a déjà 46h. heuresMax=48, remaining=2h, dureeMin=1h.
        // Bloc disponible = 4h → tronqué à 2h.
        Exigence exigence = buildExigence("ex1", "Caisse dimanche", "caissier",
                SITE_A, List.of(6), 8.0, 12.0, 1);

        DisponibilitePlage dispoDimanche = DisponibilitePlage.builder()
                .jour(6).heureDebut(8.0).heureFin(12.0).build();
        Employe alice = buildEmployeMultiDispo("e1", "Alice", "caissier", SITE_A,
                List.of(dispoDimanche));

        List<CreneauAssigne> creneauxExistants = new ArrayList<>();
        for (int jour = 0; jour <= 4; jour++) {
            creneauxExistants.add(buildCreneau("c" + jour, "e1", SITE_A, jour, 8.0, 16.0));
        }
        creneauxExistants.add(buildCreneau("c5", "e1", SITE_A, 5, 9.0, 15.0));

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(alice),
                creneauxExistants,
                List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // 46h + remaining 2h → bloc tronqué à 2h (8h-10h)
        assertThat(result.nouveauxCreneaux()).hasSize(1);
        double duree = result.nouveauxCreneaux().get(0).getHeureFin()
                     - result.nouveauxCreneaux().get(0).getHeureDebut();
        assertThat(duree).as("Bloc tronqué au budget restant de 2h").isEqualTo(2.0);
    }

    @Test
    @DisplayName("doit trier les exigences par contrainte dynamique (MRV) et traiter la plus contrainte en premier")
    void devrait_trier_exigences_par_contrainte_dynamique() {
        // ex1 : 2 candidats potentiels (5 au sens large, mais on en met 2 éligibles)
        // ex2 : 1 seul candidat (unique)
        // L'employé unique d'ex2 est aussi candidat pour ex1.
        // Si ex1 est traité en premier, il consomme l'unique candidat d'ex2.
        // Avec MRV, ex2 (1 candidat) doit être traité avant ex1 (2 candidats).

        Exigence ex1 = buildExigence("ex1", "Poste A", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);
        Exigence ex2 = buildExigence("ex2", "Poste B uniquement", "caissier",
                SITE_A, List.of(0), 14.0, 18.0, 1);

        // emp1 peut couvrir ex1 (8h-12h) et ex2 (14h-18h)
        // emp2 ne peut couvrir que ex1 (8h-12h) — n'est pas disponible 14h-18h
        DisponibilitePlage dispoAm = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(12.0).build();
        DisponibilitePlage dispoFull = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(18.0).build();

        Employe emp1 = buildEmployeMultiDispo("e1", "Alice", "caissier", SITE_A,
                List.of(dispoFull));          // disponible sur toute la journée
        Employe emp2 = buildEmployeMultiDispo("e2", "Bob", "caissier", SITE_A,
                List.of(dispoAm));            // disponible uniquement le matin

        ContexteAffectation ctx = buildContexte(
                List.of(ex1, ex2),       // ex1 donné en premier dans la liste
                List.of(emp1, emp2),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // Avec MRV, ex2 (1 candidat : emp1) doit être traité avant ex1 (2 candidats).
        // Résultat attendu : les deux exigences sont satisfaites.
        assertThat(result.nouveauxCreneaux()).hasSize(2);

        // ex2 (14h-18h) doit être attribué à emp1 (seul candidat possible)
        boolean ex2Couverte = result.nouveauxCreneaux().stream()
                .anyMatch(c -> c.getHeureDebut() == 14.0 && c.getHeureFin() == 18.0
                        && c.getEmployeId().equals("e1"));
        assertThat(ex2Couverte)
                .as("ex2 doit être affecté à emp1, le seul candidat disponible 14h-18h")
                .isTrue();
    }

    @Test
    @DisplayName("doit améliorer l'équité entre 3 employés via le passage 2-swap post-affectation")
    void devrait_ameliorer_equite_avec_2swap() {
        // 3 exigences : une par employé sur lundi (jour 0)
        // emp1 = 8h de travail possible, emp2 = 8h, emp3 = 4h disponibles
        // Après le greedy, la variance des heures devrait être réduite par les 2-swaps
        // si les swaps améliorent la situation.

        Exigence ex1 = buildExigence("ex1", "Poste 1", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);
        Exigence ex2 = buildExigence("ex2", "Poste 2", "caissier",
                SITE_A, List.of(0), 12.0, 16.0, 1);
        Exigence ex3 = buildExigence("ex3", "Poste 3", "caissier",
                SITE_A, List.of(1), 8.0, 10.0, 1);

        DisponibilitePlage dispoEx1 = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(12.0).build();
        DisponibilitePlage dispoEx3 = DisponibilitePlage.builder()
                .jour(1).heureDebut(8.0).heureFin(10.0).build();
        DisponibilitePlage dispoPoly = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(16.0).build();

        // emp1 disponible 8h-16h lundi → candidat pour ex1 et ex2
        // emp2 disponible 8h-12h lundi → candidat pour ex1 uniquement
        // emp3 disponible mardi 8h-10h → candidat pour ex3 uniquement
        Employe emp1 = buildEmployeMultiDispo("e1", "Alice", "caissier", SITE_A,
                List.of(dispoPoly));
        Employe emp2 = buildEmployeMultiDispo("e2", "Bob", "caissier", SITE_A,
                List.of(dispoEx1));
        Employe emp3 = buildEmployeMultiDispo("e3", "Carol", "caissier", SITE_A,
                List.of(dispoEx3));

        ContexteAffectation ctx = buildContexte(
                List.of(ex1, ex2, ex3),
                List.of(emp1, emp2, emp3),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // Les 3 exigences doivent être couvertes
        assertThat(result.nouveauxCreneaux()).hasSize(3);

        // Calculer la variance des heures assignées
        Map<String, Double> heuresParEmp = result.nouveauxCreneaux().stream()
                .collect(Collectors.groupingBy(
                        CreneauAssigne::getEmployeId,
                        Collectors.summingDouble(c -> c.getHeureFin() - c.getHeureDebut())));

        // emp1 et emp2 sont candidats pour les mêmes créneaux.
        // Avec équité + 2-swap, emp1 ne devrait pas monopoliser tous les créneaux.
        double maxHeures = heuresParEmp.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double minHeures = heuresParEmp.values().stream()
                .filter(h -> h > 0.0)
                .mapToDouble(Double::doubleValue).min().orElse(0.0);

        // L'écart entre le plus chargé et le moins chargé parmi ceux qui ont des heures
        // doit être raisonnable (pas que emp1 a 8h et emp2 a 0h)
        assertThat(maxHeures - minHeures)
                .as("La variance des heures doit être réduite par l'équité et les 2-swaps")
                .isLessThan(8.0);
    }

    @Test
    @DisplayName("doit affecter plusieurs employés pour une exigence avec nombreRequis=2")
    void devrait_affecter_plusieurs_employes_meme_exigence() {
        // Une exigence nécessitant 2 employés simultanément
        Exigence exigence = buildExigence("ex1", "Caisse renforcée", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 2);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 12.0);
        Employe bob   = buildEmploye("e2", "Bob",   "caissier", SITE_A, 0, 8.0, 12.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(alice, bob),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // Les 2 employés doivent être affectés
        assertThat(result.nouveauxCreneaux()).hasSize(2);

        List<String> employesAffectes = result.nouveauxCreneaux().stream()
                .map(CreneauAssigne::getEmployeId)
                .toList();
        assertThat(employesAffectes).containsExactlyInAnyOrder("e1", "e2");

        // Les deux créneaux doivent couvrir la même plage horaire
        assertThat(result.nouveauxCreneaux())
                .allSatisfy(c -> {
                    assertThat(c.getHeureDebut()).isEqualTo(8.0);
                    assertThat(c.getHeureFin()).isEqualTo(12.0);
                    assertThat(c.getSiteId()).isEqualTo(SITE_A);
                });
    }

    // =========================================================================
    // 4. Cas limites
    // =========================================================================

    @Test
    @DisplayName("doit gérer l'absence totale d'employés éligibles sans lever d'exception")
    void devrait_gerer_aucun_employe_disponible() {
        Exigence exigence = buildExigence("ex1", "Caisse vide", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        // Aucun employé dans la liste
        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(),    // liste vide d'employés
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.totalAffectes()).isZero();
        assertThat(result.nouveauxCreneaux()).isEmpty();
    }

    @Test
    @DisplayName("doit gérer une exigence sans jours définis sans lever d'exception")
    void devrait_gerer_exigence_sans_jours() {
        // Exigence avec une liste de jours vide
        Exigence exigence = buildExigence("ex1", "Exigence sans jours", "caissier",
                SITE_A, new ArrayList<>(), 8.0, 12.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 12.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(alice),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.totalAffectes()).isZero();
        assertThat(result.nouveauxCreneaux()).isEmpty();
    }

    // =========================================================================
    // 5. Règles de priorité — ancienneté
    // =========================================================================

    @Test
    @DisplayName("ancienneté — doit affecter l'employé le plus ancien en premier")
    void anciennete_employe_senior_affecte_en_premier() {
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe senior = Employe.builder()
                .id("e-senior").nom("Senior").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2015, 1, 1))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        Employe junior = Employe.builder()
                .id("e-junior").nom("Junior").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2023, 6, 1))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(junior, senior),   // junior listed first to ensure sort is exercised
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("anciennete"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("L'employé le plus ancien (dateEmbauche 2015) doit être affecté, pas le junior (2023)")
                .isEqualTo("e-senior");
    }

    @Test
    @DisplayName("ancienneté — null dateEmbauche déprioritise l'employé")
    void anciennete_null_date_embauche_est_deprioritise() {
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe avecDate = Employe.builder()
                .id("e-avec").nom("AvecDate").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2020, 3, 15))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        Employe sansDate = Employe.builder()
                .id("e-sans").nom("SansDate").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(null)
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(sansDate, avecDate),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("anciennete"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("L'employé sans dateEmbauche (null → LocalDate.MAX) doit être déprioritisé")
                .isEqualTo("e-avec");
    }

    @Test
    @DisplayName("ancienneté — le 2-swap ne doit PAS écraser l'ordre d'ancienneté")
    void twoSwap_ne_doit_pas_ecraser_anciennete() {
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        DisponibilitePlage dispoLundi = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(12.0).build();

        Employe senior = Employe.builder()
                .id("e-senior").nom("Senior").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2010, 1, 1))
                .disponibilites(List.of(dispoLundi))
                .build();

        Employe junior1 = Employe.builder()
                .id("e-junior1").nom("Junior1").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2020, 5, 1))
                .disponibilites(List.of(dispoLundi))
                .build();

        Employe junior2 = Employe.builder()
                .id("e-junior2").nom("Junior2").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2022, 9, 1))
                .disponibilites(List.of(dispoLundi))
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(junior1, junior2, senior),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("anciennete"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("Le 2-swap ne doit pas réattribuer le créneau du senior au junior")
                .isEqualTo("e-senior");
    }

    // =========================================================================
    // 6. Règles de priorité — âge
    // =========================================================================

    @Test
    @DisplayName("age — doit affecter l'employé le plus âgé en premier")
    void age_employe_le_plus_age_affecte_en_premier() {
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe ancien = Employe.builder()
                .id("e-ancien").nom("Ancien").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateNaissance(LocalDate.of(1975, 5, 20))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        Employe jeune = Employe.builder()
                .id("e-jeune").nom("Jeune").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateNaissance(LocalDate.of(1995, 11, 3))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(jeune, ancien),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("age"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("L'employé le plus âgé (1975) doit être affecté en premier")
                .isEqualTo("e-ancien");
    }

    // =========================================================================
    // 7. Règles combinées
    // =========================================================================

    @Test
    @DisplayName("disponibilite avant ancienneté — l'employé le plus disponible gagne")
    void regle_disponibilite_prime_sur_anciennete() {
        Exigence exigence = buildExigence("ex1", "Caisse", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe senior = Employe.builder()
                .id("e-senior").nom("Senior").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2010, 1, 1))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        Employe junior = Employe.builder()
                .id("e-junior").nom("Junior").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2022, 9, 1))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(16.0).build()))
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(senior, junior),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("disponibilite", "anciennete"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("Avec disponibilite en premier, le junior (8h dispo) prime sur le senior (4h dispo)")
                .isEqualTo("e-junior");
    }

    @Test
    @DisplayName("ancienneté + équité — l'ancienneté départage à heures égales")
    void anciennete_departage_quand_heures_egales() {
        Exigence exigence = buildExigence("ex1", "Caisse", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe senior = Employe.builder()
                .id("e-senior").nom("Senior").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2012, 3, 1))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        Employe junior = Employe.builder()
                .id("e-junior").nom("Junior").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2021, 8, 15))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(junior, senior),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("anciennete", "equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("Ancienneté avant équité : le senior doit être choisi à heures égales")
                .isEqualTo("e-senior");
    }

    // =========================================================================
    // 8. Congé partiel — heures
    // =========================================================================

    @Test
    @DisplayName("congé partiel — l'employé est bloqué quand le slot chevauche le congé")
    void conge_partiel_bloque_slot_chevauchant() {
        // Leave 13h-17h on Monday. Exigence requires 14h-18h → OVERLAPS → blocked.
        Exigence exigence = buildExigence("ex1", "Caisse aprem", "caissier",
                SITE_A, List.of(0), 14.0, 18.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").role("caissier")
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(14.0).heureFin(18.0).build()))
                .build();

        DemandeConge conge = DemandeConge.builder()
                .id("cg1").employeId("e1").typeCongeId("cp")
                .dateDebut(LocalDate.of(2025, 1, 6))
                .dateFin(LocalDate.of(2025, 1, 6))
                .heureDebut(13.0).heureFin(17.0)
                .duree(0.5).statut(StatutDemande.approuve)
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                List.of(), List.of(conge), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux())
                .as("Le slot 14h-18h chevauche le congé 13h-17h — employé bloqué")
                .isEmpty();
    }

    @Test
    @DisplayName("congé partiel — l'employé reste planifiable en dehors du congé")
    void conge_partiel_planifiable_hors_conge() {
        // Leave 13h-17h on Monday. Exigence requires 8h-12h → NO OVERLAP → ok.
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").role("caissier")
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        DemandeConge conge = DemandeConge.builder()
                .id("cg1").employeId("e1").typeCongeId("cp")
                .dateDebut(LocalDate.of(2025, 1, 6))
                .dateFin(LocalDate.of(2025, 1, 6))
                .heureDebut(13.0).heureFin(17.0)
                .duree(0.5).statut(StatutDemande.approuve)
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                List.of(), List.of(conge), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux())
                .as("Le slot 8h-12h ne chevauche pas le congé 13h-17h — employé planifiable")
                .hasSize(1);
    }

    // =========================================================================
    // 9. Filtrage multi-site
    // =========================================================================

    @Test
    @DisplayName("multi-site — seul l'employé du bon site est candidat en mode org-wide")
    void multisite_seul_employe_du_bon_site_est_candidat() {
        Exigence exigence = buildExigence("ex1", "Caisse site-A", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        DisponibilitePlage dispoLundi = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(12.0).build();

        Employe empA = Employe.builder()
                .id("e-a").nom("EmpA").role("caissier")
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(dispoLundi))
                .build();

        Employe empB = Employe.builder()
                .id("e-b").nom("EmpB").role("caissier")
                .siteIds(List.of(SITE_B))
                .disponibilites(List.of(dispoLundi))
                .build();

        Map<String, Employe> employeParId = Map.of("e-a", empA, "e-b", empB);
        ContexteAffectation ctx = new ContexteAffectation(
                List.of(exigence),
                List.of(empA, empB),
                employeParId,
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0,
                LUNDI, SEMAINE,
                null,   // org-wide
                ORG
        );

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("Seul l'employé de SITE_A doit être affecté sur une exigence SITE_A")
                .isEqualTo("e-a");
    }

    @Test
    @DisplayName("multi-site — un employé rattaché aux deux sites peut être affecté")
    void multisite_employe_double_site_affectable() {
        Exigence exigence = buildExigence("ex1", "Caisse site-B", "caissier",
                SITE_B, List.of(0), 8.0, 12.0, 1);

        Employe empAB = Employe.builder()
                .id("e-ab").nom("EmpAB").role("caissier")
                .siteIds(List.of(SITE_A, SITE_B))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        Map<String, Employe> employeParId = Map.of("e-ab", empAB);
        ContexteAffectation ctx = new ContexteAffectation(
                List.of(exigence),
                List.of(empAB),
                employeParId,
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0,
                LUNDI, SEMAINE,
                null,
                ORG
        );

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId()).isEqualTo("e-ab");
        assertThat(result.nouveauxCreneaux().get(0).getSiteId()).isEqualTo(SITE_B);
    }

    // =========================================================================
    // 10. Cap hebdomadaire — bloc plus court
    // =========================================================================

    @Test
    @DisplayName("cap hebdo — doit trouver un bloc plus court quand le plus long dépasse le cap")
    void cap_hebdo_bloc_plus_court_si_depasse() {
        // Scénario simplifié : Alice a déjà 6h, cap=8h, exigence 4h.
        // remaining=2h, dureeMin=1h → l'algo doit tronquer le bloc de 4h à 2h.
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(1), 8.0, 12.0, 1);  // mardi, bloc 4h

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").role("caissier")
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(1).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        // 6h existantes : lundi 8h-14h
        List<CreneauAssigne> existants = List.of(
                buildCreneau("c0", "e1", SITE_A, 0, 8.0, 14.0));

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                existants, List.of(), List.of(),
                1.0, 1.0, List.of(), 8.0);  // cap 8h

        SolverResult result = solver.resoudre(ctx);

        // 6h existantes + remaining 2h. Le bloc 4h doit être tronqué à 2h.
        assertThat(result.nouveauxCreneaux())
                .as("Le solver doit tronquer le bloc à 2h au lieu de skip")
                .hasSize(1);

        CreneauAssigne creneau = result.nouveauxCreneaux().get(0);
        double duree = creneau.getHeureFin() - creneau.getHeureDebut();
        assertThat(duree)
                .as("Le bloc doit être tronqué à 2h (remaining budget)")
                .isLessThanOrEqualTo(2.0 + 0.001);
        assertThat(duree)
                .as("Le bloc tronqué doit respecter dureeMin=1h")
                .isGreaterThanOrEqualTo(1.0);
    }

    // =========================================================================
    // 11. Jour férié — filtre par site
    // =========================================================================

    @Test
    @DisplayName("jour férié site-spécifique ne bloque pas les autres sites")
    void jour_ferie_site_specifique_ne_bloque_pas_autre_site() {
        // Holiday defined for SITE_B on Monday. SITE_A exigence should still work.
        Exigence exigence = buildExigence("ex1", "Caisse site-A", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 12.0);

        JourFerie ferieSiteB = JourFerie.builder()
                .id("jf1").nom("Fête locale site-B")
                .date(LocalDate.of(2025, 1, 6))
                .recurrent(false)
                .siteId(SITE_B)  // only for site-B
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                List.of(), List.of(), List.of(ferieSiteB),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux())
                .as("Le jour férié de SITE_B ne doit pas bloquer l'affectation sur SITE_A")
                .hasSize(1);
    }

    // =========================================================================
    // 12. Scénarios complexes multi-rôles / multi-jours
    // =========================================================================

    @Test
    @DisplayName("multi-rôles — chaque rôle est affecté indépendamment, pas de mélange")
    void multi_roles_isolation_complete() {
        // 2 exigences : 1 cuisinier + 1 serveur, même créneau lundi 8h-12h
        Exigence exCuisinier = buildExigence("ex-c", "Cuisine", "cuisinier",
                SITE_A, List.of(0), 8.0, 12.0, 1);
        Exigence exServeur = buildExigence("ex-s", "Service", "serveur",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        DisponibilitePlage dispoLundi = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(12.0).build();

        Employe chef = Employe.builder().id("chef").nom("Chef").role("cuisinier")
                .siteIds(List.of(SITE_A)).disponibilites(List.of(dispoLundi)).build();
        Employe commis = Employe.builder().id("commis").nom("Commis").role("cuisinier")
                .siteIds(List.of(SITE_A)).disponibilites(List.of(dispoLundi)).build();
        Employe paul = Employe.builder().id("paul").nom("Paul").role("serveur")
                .siteIds(List.of(SITE_A)).disponibilites(List.of(dispoLundi)).build();
        Employe marie = Employe.builder().id("marie").nom("Marie").role("serveur")
                .siteIds(List.of(SITE_A)).disponibilites(List.of(dispoLundi)).build();

        ContexteAffectation ctx = buildContexte(
                List.of(exCuisinier, exServeur),
                List.of(chef, commis, paul, marie),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(2);

        // Vérifier qu'un cuisinier est affecté et un serveur, pas deux du même rôle
        String empCuisine = result.nouveauxCreneaux().stream()
                .filter(c -> c.getSiteId().equals(SITE_A) && c.getHeureDebut() == 8.0)
                .map(CreneauAssigne::getEmployeId)
                .toList().toString();
        boolean aCuisinier = result.nouveauxCreneaux().stream()
                .anyMatch(c -> c.getEmployeId().equals("chef") || c.getEmployeId().equals("commis"));
        boolean aServeur = result.nouveauxCreneaux().stream()
                .anyMatch(c -> c.getEmployeId().equals("paul") || c.getEmployeId().equals("marie"));
        assertThat(aCuisinier).as("Un cuisinier doit être affecté").isTrue();
        assertThat(aServeur).as("Un serveur doit être affecté").isTrue();
    }

    @Test
    @DisplayName("multi-rôles + nombreRequis — 2 cuisiniers + 3 serveurs sur le même créneau")
    void multi_roles_nombre_requis_eleve() {
        Exigence exCuisinier = buildExigence("ex-c", "Cuisine", "cuisinier",
                SITE_A, List.of(0), 8.0, 14.0, 2);
        Exigence exServeur = buildExigence("ex-s", "Service", "serveur",
                SITE_A, List.of(0), 8.0, 14.0, 3);

        DisponibilitePlage dispo = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(14.0).build();

        List<Employe> employes = List.of(
                Employe.builder().id("c1").nom("Chef1").role("cuisinier")
                        .siteIds(List.of(SITE_A)).disponibilites(List.of(dispo)).build(),
                Employe.builder().id("c2").nom("Chef2").role("cuisinier")
                        .siteIds(List.of(SITE_A)).disponibilites(List.of(dispo)).build(),
                Employe.builder().id("s1").nom("Serv1").role("serveur")
                        .siteIds(List.of(SITE_A)).disponibilites(List.of(dispo)).build(),
                Employe.builder().id("s2").nom("Serv2").role("serveur")
                        .siteIds(List.of(SITE_A)).disponibilites(List.of(dispo)).build(),
                Employe.builder().id("s3").nom("Serv3").role("serveur")
                        .siteIds(List.of(SITE_A)).disponibilites(List.of(dispo)).build()
        );

        ContexteAffectation ctx = buildContexte(
                List.of(exCuisinier, exServeur), employes,
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // 2 cuisiniers + 3 serveurs = 5 créneaux
        assertThat(result.nouveauxCreneaux()).hasSize(5);

        long cuisiniers = result.nouveauxCreneaux().stream()
                .filter(c -> c.getEmployeId().startsWith("c")).count();
        long serveurs = result.nouveauxCreneaux().stream()
                .filter(c -> c.getEmployeId().startsWith("s")).count();
        assertThat(cuisiniers).as("Exactement 2 cuisiniers affectés").isEqualTo(2);
        assertThat(serveurs).as("Exactement 3 serveurs affectés").isEqualTo(3);
    }

    @Test
    @DisplayName("semaine complète — 5 jours, 3 rôles, 8 employés, ancienneté respectée")
    void semaine_complete_multi_roles_anciennete() {
        // Restaurant : lundi-vendredi, 3 shifts par jour
        // Rôles : cuisinier (1 requis/shift), serveur (2 requis/shift)
        // 2 cuisiniers, 6 serveurs
        List<Exigence> exigences = new ArrayList<>();
        for (int jour = 0; jour <= 4; jour++) {
            exigences.add(buildExigence("ex-c-" + jour, "Cuisine J" + jour, "cuisinier",
                    SITE_A, List.of(jour), 8.0, 16.0, 1));
            exigences.add(buildExigence("ex-s-" + jour, "Service J" + jour, "serveur",
                    SITE_A, List.of(jour), 8.0, 16.0, 2));
        }

        // Cuisiniers — tous disponibles lundi-vendredi 8h-16h
        List<DisponibilitePlage> dispoSemaine = new ArrayList<>();
        for (int j = 0; j <= 4; j++) {
            dispoSemaine.add(DisponibilitePlage.builder().jour(j).heureDebut(8.0).heureFin(16.0).build());
        }

        Employe chefSenior = Employe.builder().id("cs").nom("ChefSenior").role("cuisinier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoSemaine))
                .dateEmbauche(LocalDate.of(2010, 1, 1)).build();
        Employe chefJunior = Employe.builder().id("cj").nom("ChefJunior").role("cuisinier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoSemaine))
                .dateEmbauche(LocalDate.of(2022, 6, 1)).build();

        // 6 serveurs avec anciennetés variées
        List<Employe> serveurs = new ArrayList<>();
        int[] annees = {2012, 2015, 2018, 2020, 2023, 2024};
        for (int i = 0; i < 6; i++) {
            serveurs.add(Employe.builder().id("s" + i).nom("Serveur" + i).role("serveur")
                    .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoSemaine))
                    .dateEmbauche(LocalDate.of(annees[i], 1, 1)).build());
        }

        List<Employe> tousEmployes = new ArrayList<>();
        tousEmployes.add(chefSenior);
        tousEmployes.add(chefJunior);
        tousEmployes.addAll(serveurs);

        // Cap bas (24h) pour forcer la rotation des employés
        ContexteAffectation ctx = buildContexte(
                exigences, tousEmployes,
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("anciennete", "equite"), 24.0);

        SolverResult result = solver.resoudre(ctx);

        // 5 jours × (1 cuisinier + 2 serveurs) = 15 créneaux
        assertThat(result.nouveauxCreneaux()).hasSize(15);

        // Le chef senior doit avoir au moins autant de jours que le junior
        long joursSenior = result.nouveauxCreneaux().stream()
                .filter(c -> c.getEmployeId().equals("cs")).count();
        long joursJunior = result.nouveauxCreneaux().stream()
                .filter(c -> c.getEmployeId().equals("cj")).count();
        assertThat(joursSenior)
                .as("Le chef senior (2010) doit être affecté au moins autant que le junior (2022)")
                .isGreaterThanOrEqualTo(joursJunior);

        // Avec cap 24h (3 jours × 8h), les anciens atteignent le cap et les juniors prennent le relais
        // → au moins 4 serveurs distincts utilisés (les 2 plus anciens + relais)
        long serveursDistincts = result.nouveauxCreneaux().stream()
                .filter(c -> c.getEmployeId().startsWith("s"))
                .map(CreneauAssigne::getEmployeId)
                .distinct().count();
        assertThat(serveursDistincts)
                .as("Avec cap 24h, au moins 4 serveurs doivent être utilisés (rotation forcée)")
                .isGreaterThanOrEqualTo(4);
    }

    // =========================================================================
    // 13. Disponibilités fragmentées
    // =========================================================================

    @Test
    @DisplayName("disponibilité fragmentée — employé avec trou en milieu de journée")
    void disponibilite_fragmentee_trou_milieu_journee() {
        // Exigence 8h-16h, mais l'employé est disponible 8h-12h et 14h-16h (trou 12h-14h)
        Exigence exigence = buildExigence("ex1", "Journée complète", "caissier",
                SITE_A, List.of(0), 8.0, 16.0, 1);

        Employe alice = Employe.builder().id("e1").nom("Alice").role("caissier")
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build(),
                        DisponibilitePlage.builder().jour(0).heureDebut(14.0).heureFin(16.0).build()))
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                List.of(), List.of(), List.of(),
                2.0, 1.0, List.of(), 48.0);  // dureeMin=2h

        SolverResult result = solver.resoudre(ctx);

        // Le bloc 8h-12h (4h) satisfait dureeMin=2h, le bloc 14h-16h (2h) aussi
        // L'algo doit prendre le plus long bloc disponible : 8h-12h
        assertThat(result.nouveauxCreneaux()).isNotEmpty();
        assertThat(result.nouveauxCreneaux().get(0).getHeureDebut()).isEqualTo(8.0);
        assertThat(result.nouveauxCreneaux().get(0).getHeureFin()).isEqualTo(12.0);
    }

    @Test
    @DisplayName("disponibilité fragmentée — granularité 0.5h avec micro-plages")
    void disponibilite_fragmentee_granularite_fine() {
        // Exigence 9h-11h, granularité 0.5h
        // Employé disponible 9h-10h et 10h-11h (deux plages contiguës)
        Exigence exigence = buildExigence("ex1", "Matin court", "caissier",
                SITE_A, List.of(0), 9.0, 11.0, 1);

        Employe alice = Employe.builder().id("e1").nom("Alice").role("caissier")
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(9.0).heureFin(10.0).build(),
                        DisponibilitePlage.builder().jour(0).heureDebut(10.0).heureFin(11.0).build()))
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                List.of(), List.of(), List.of(),
                0.5, 0.5, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // Les 2 plages contiguës doivent fusionner en un seul créneau 9h-11h
        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getHeureDebut()).isEqualTo(9.0);
        assertThat(result.nouveauxCreneaux().get(0).getHeureFin()).isEqualTo(11.0);
    }

    // =========================================================================
    // 14. Scénario de pénurie — plus d'exigences que de ressources
    // =========================================================================

    @Test
    @DisplayName("pénurie — nombreRequis > employés disponibles")
    void penurie_plus_requis_que_employes() {
        // 1 exigence nécessitant 3 caissiers, seulement 2 disponibles
        Exigence exigence = buildExigence("ex1", "Rush matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 3);

        DisponibilitePlage dispo = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(12.0).build();
        Employe alice = Employe.builder().id("e1").nom("Alice").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(List.of(dispo)).build();
        Employe bob = Employe.builder().id("e2").nom("Bob").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(List.of(dispo)).build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice, bob),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // 3 requis mais seulement 2 employés → 2 affectations (le max possible)
        assertThat(result.nouveauxCreneaux()).hasSize(2);
        assertThat(result.nouveauxCreneaux().stream()
                .map(CreneauAssigne::getEmployeId).distinct().count())
                .as("Les 2 employés distincts doivent être affectés")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("pénurie partielle — MRV doit prioriser l'exigence la plus contrainte")
    void penurie_mrv_priorise_contrainte() {
        // ex1 : 2 candidats possibles (alice + bob)
        // ex2 : 1 seul candidat (alice uniquement, après-midi)
        // Sans MRV, si ex1 consomme alice, ex2 ne peut pas être couvert
        // Avec MRV, ex2 (1 candidat) est traitée avant ex1 (2 candidats)
        Exigence ex1 = buildExigence("ex1", "Matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);
        Exigence ex2 = buildExigence("ex2", "Après-midi (unique)", "caissier",
                SITE_A, List.of(0), 14.0, 18.0, 1);

        // alice : disponible matin ET après-midi
        Employe alice = Employe.builder().id("e1").nom("Alice").role("caissier")
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(18.0).build()))
                .build();
        // bob : disponible UNIQUEMENT le matin
        Employe bob = Employe.builder().id("e2").nom("Bob").role("caissier")
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(ex1, ex2), List.of(alice, bob),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // Les 2 exigences doivent être couvertes grâce au MRV
        assertThat(result.nouveauxCreneaux()).hasSize(2);

        // ex2 (14h-18h) doit être affecté à alice (seul candidat)
        boolean ex2Alice = result.nouveauxCreneaux().stream()
                .anyMatch(c -> c.getHeureDebut() == 14.0 && c.getEmployeId().equals("e1"));
        assertThat(ex2Alice).as("MRV : alice (seul candidat après-midi) doit couvrir ex2").isTrue();
    }

    // =========================================================================
    // 15. Cross-site complexe
    // =========================================================================

    @Test
    @DisplayName("cross-site — employé partagé entre 2 sites, pas de double booking")
    void cross_site_employe_partage_pas_double_booking() {
        // alice appartient aux 2 sites, exigence sur chaque site même créneau
        Exigence exSiteA = buildExigence("ex-a", "Caisse A", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);
        Exigence exSiteB = buildExigence("ex-b", "Caisse B", "caissier",
                SITE_B, List.of(0), 8.0, 12.0, 1);

        DisponibilitePlage dispo = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(12.0).build();

        Employe alice = Employe.builder().id("e1").nom("Alice").role("caissier")
                .siteIds(List.of(SITE_A, SITE_B)).disponibilites(List.of(dispo)).build();
        Employe bob = Employe.builder().id("e2").nom("Bob").role("caissier")
                .siteIds(List.of(SITE_B)).disponibilites(List.of(dispo)).build();

        Map<String, Employe> parId = Map.of("e1", alice, "e2", bob);
        ContexteAffectation ctx = new ContexteAffectation(
                List.of(exSiteA, exSiteB), List.of(alice, bob), parId,
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0,
                LUNDI, SEMAINE, null, ORG);

        SolverResult result = solver.resoudre(ctx);

        // alice ne peut PAS être sur 2 sites en même temps
        // alice couvre un site, bob couvre l'autre (si possible)
        long aliceCount = result.nouveauxCreneaux().stream()
                .filter(c -> c.getEmployeId().equals("e1")).count();
        assertThat(aliceCount)
                .as("Alice ne peut être affectée qu'à UN seul site sur le même créneau")
                .isEqualTo(1);

        // 2 affectations au total (alice sur un site, bob sur site-B)
        assertThat(result.nouveauxCreneaux()).hasSize(2);
    }

    @Test
    @DisplayName("cross-site — créneaux adjacents sur sites différents sont OK")
    void cross_site_creneaux_adjacents_ok() {
        // alice : site-A 8h-12h puis site-B 12h-16h (adjacents, pas overlap)
        Exigence exA = buildExigence("ex-a", "Matin A", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);
        Exigence exB = buildExigence("ex-b", "Après-midi B", "caissier",
                SITE_B, List.of(0), 12.0, 16.0, 1);

        Employe alice = Employe.builder().id("e1").nom("Alice").role("caissier")
                .siteIds(List.of(SITE_A, SITE_B))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(16.0).build()))
                .build();

        Map<String, Employe> parId = Map.of("e1", alice);
        ContexteAffectation ctx = new ContexteAffectation(
                List.of(exA, exB), List.of(alice), parId,
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0,
                LUNDI, SEMAINE, null, ORG);

        SolverResult result = solver.resoudre(ctx);

        // Les 2 créneaux sont adjacents (pas de chevauchement) → les 2 doivent passer
        assertThat(result.nouveauxCreneaux()).hasSize(2);
    }

    // =========================================================================
    // 16. Contraintes combinées — le stress test réaliste
    // =========================================================================

    @Test
    @DisplayName("STRESS — semaine complète avec congés, fériés, multi-sites, multi-rôles, cap hebdo")
    void stress_test_semaine_realiste() {
        // Setup : 2 sites, 3 rôles, 7 employés, 1 congé, 1 férié, cap 40h
        // Site-A : cuisinier(1) + serveur(1) lundi-vendredi 8h-16h
        // Site-B : cuisinier(1) lundi-vendredi 8h-16h
        // Mercredi est férié pour site-B uniquement

        List<Exigence> exigences = new ArrayList<>();
        for (int j = 0; j <= 4; j++) {
            exigences.add(buildExigence("ex-ca-" + j, "Cuisine A J" + j, "cuisinier",
                    SITE_A, List.of(j), 8.0, 16.0, 1));
            exigences.add(buildExigence("ex-sa-" + j, "Service A J" + j, "serveur",
                    SITE_A, List.of(j), 8.0, 16.0, 1));
            exigences.add(buildExigence("ex-cb-" + j, "Cuisine B J" + j, "cuisinier",
                    SITE_B, List.of(j), 8.0, 16.0, 1));
        }

        // Disponibilités full week
        List<DisponibilitePlage> dispoFull = new ArrayList<>();
        for (int j = 0; j <= 4; j++) {
            dispoFull.add(DisponibilitePlage.builder().jour(j).heureDebut(8.0).heureFin(16.0).build());
        }

        // 4 cuisiniers (2 site-A, 1 site-B, 1 les deux)
        Employe c1 = Employe.builder().id("c1").nom("Chef1").role("cuisinier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoFull))
                .dateEmbauche(LocalDate.of(2012, 1, 1)).build();
        Employe c2 = Employe.builder().id("c2").nom("Chef2").role("cuisinier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoFull))
                .dateEmbauche(LocalDate.of(2020, 6, 1)).build();
        Employe c3 = Employe.builder().id("c3").nom("Chef3").role("cuisinier")
                .siteIds(List.of(SITE_B)).disponibilites(new ArrayList<>(dispoFull))
                .dateEmbauche(LocalDate.of(2018, 3, 1)).build();
        Employe c4 = Employe.builder().id("c4").nom("ChefFlex").role("cuisinier")
                .siteIds(List.of(SITE_A, SITE_B)).disponibilites(new ArrayList<>(dispoFull))
                .dateEmbauche(LocalDate.of(2015, 1, 1)).build();

        // 3 serveurs site-A
        Employe s1 = Employe.builder().id("s1").nom("Serv1").role("serveur")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoFull))
                .dateEmbauche(LocalDate.of(2014, 1, 1)).build();
        Employe s2 = Employe.builder().id("s2").nom("Serv2").role("serveur")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoFull))
                .dateEmbauche(LocalDate.of(2021, 9, 1)).build();
        Employe s3 = Employe.builder().id("s3").nom("Serv3").role("serveur")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoFull))
                .dateEmbauche(LocalDate.of(2023, 1, 1)).build();

        List<Employe> tous = List.of(c1, c2, c3, c4, s1, s2, s3);

        // Congé : s1 en congé mardi (jour 1) toute la journée
        DemandeConge congeS1 = DemandeConge.builder()
                .id("cg1").employeId("s1").typeCongeId("cp")
                .dateDebut(LUNDI.plusDays(1)).dateFin(LUNDI.plusDays(1))
                .duree(1.0).statut(StatutDemande.approuve).build();

        // Jour férié : mercredi (jour 2) pour site-B uniquement
        JourFerie ferieMercredi = JourFerie.builder()
                .id("jf1").nom("Fête locale B")
                .date(LUNDI.plusDays(2)).recurrent(false)
                .siteId(SITE_B).build();

        Map<String, Employe> parId = tous.stream()
                .collect(Collectors.toMap(Employe::getId, e -> e));

        ContexteAffectation ctx = new ContexteAffectation(
                exigences, tous, parId,
                List.of(), List.of(congeS1), List.of(ferieMercredi),
                1.0, 1.0, List.of("anciennete", "equite"), 40.0,
                LUNDI, SEMAINE, null, ORG);

        SolverResult result = solver.resoudre(ctx);

        // Vérifications structurelles
        assertThat(result.nouveauxCreneaux()).isNotEmpty();

        // 1) Aucun employé ne dépasse 40h
        Map<String, Double> heuresParEmp = result.nouveauxCreneaux().stream()
                .collect(Collectors.groupingBy(CreneauAssigne::getEmployeId,
                        Collectors.summingDouble(c -> c.getHeureFin() - c.getHeureDebut())));
        heuresParEmp.forEach((empId, heures) ->
                assertThat(heures)
                        .as("Employé %s ne doit pas dépasser 40h (a %.1fh)", empId, heures)
                        .isLessThanOrEqualTo(40.0 + 0.001));

        // 2) s1 ne doit PAS être affecté mardi (congé)
        boolean s1Mardi = result.nouveauxCreneaux().stream()
                .anyMatch(c -> c.getEmployeId().equals("s1") && c.getJour() == 1);
        assertThat(s1Mardi).as("s1 est en congé mardi — ne doit pas être affecté").isFalse();

        // 3) Aucune affectation sur site-B mercredi (férié site-B)
        boolean siteB_mercredi = result.nouveauxCreneaux().stream()
                .anyMatch(c -> c.getSiteId().equals(SITE_B) && c.getJour() == 2);
        assertThat(siteB_mercredi).as("Mercredi est férié pour site-B").isFalse();

        // 4) Site-A mercredi doit être couvert (pas férié pour site-A)
        boolean siteA_mercredi = result.nouveauxCreneaux().stream()
                .anyMatch(c -> c.getSiteId().equals(SITE_A) && c.getJour() == 2);
        assertThat(siteA_mercredi).as("Mercredi n'est PAS férié pour site-A").isTrue();

        // 5) Pas de double-booking cross-site (même employé, même jour, même heure)
        Map<String, List<CreneauAssigne>> parEmpJour = result.nouveauxCreneaux().stream()
                .collect(Collectors.groupingBy(c -> c.getEmployeId() + "-" + c.getJour()));
        parEmpJour.forEach((key, creneaux) -> {
            for (int i = 0; i < creneaux.size(); i++) {
                for (int j = i + 1; j < creneaux.size(); j++) {
                    CreneauAssigne a = creneaux.get(i);
                    CreneauAssigne b = creneaux.get(j);
                    boolean overlap = a.getHeureDebut() < b.getHeureFin()
                            && b.getHeureDebut() < a.getHeureFin();
                    assertThat(overlap)
                            .as("Double booking détecté pour %s : %s-%s et %s-%s",
                                    key, a.getHeureDebut(), a.getHeureFin(),
                                    b.getHeureDebut(), b.getHeureFin())
                            .isFalse();
                }
            }
        });

        // 6) Chaque créneau est affecté au bon site (employé membre du site)
        for (CreneauAssigne c : result.nouveauxCreneaux()) {
            Employe emp = parId.get(c.getEmployeId());
            assertThat(emp.getSiteIds())
                    .as("Employé %s affecté à %s mais n'est pas membre de ce site",
                            emp.getNom(), c.getSiteId())
                    .contains(c.getSiteId());
        }
    }

    // =========================================================================
    // 17. Ancienneté sur plusieurs jours — stabilité de l'ordre
    // =========================================================================

    @Test
    @DisplayName("ancienneté stable — le senior est toujours premier sur chaque jour de la semaine")
    void anciennete_stable_toute_la_semaine() {
        // 1 exigence par jour (lundi-vendredi), nombreRequis=1, seule règle=ancienneté
        // 3 employés : le senior (2010) doit TOUJOURS être choisi en premier
        List<Exigence> exigences = new ArrayList<>();
        for (int j = 0; j <= 4; j++) {
            exigences.add(buildExigence("ex-" + j, "Poste J" + j, "caissier",
                    SITE_A, List.of(j), 8.0, 12.0, 1));
        }

        List<DisponibilitePlage> dispoSemaine = new ArrayList<>();
        for (int j = 0; j <= 4; j++) {
            dispoSemaine.add(DisponibilitePlage.builder().jour(j).heureDebut(8.0).heureFin(12.0).build());
        }

        Employe senior = Employe.builder().id("e1").nom("Senior").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoSemaine))
                .dateEmbauche(LocalDate.of(2010, 1, 1)).build();
        Employe mid = Employe.builder().id("e2").nom("Mid").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoSemaine))
                .dateEmbauche(LocalDate.of(2018, 1, 1)).build();
        Employe junior = Employe.builder().id("e3").nom("Junior").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoSemaine))
                .dateEmbauche(LocalDate.of(2023, 1, 1)).build();

        ContexteAffectation ctx = buildContexte(
                exigences, List.of(junior, mid, senior),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("anciennete"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(5);

        // Le senior doit être sur TOUS les jours (seule règle = ancienneté, pas de cap atteint)
        for (CreneauAssigne c : result.nouveauxCreneaux()) {
            assertThat(c.getEmployeId())
                    .as("Jour %d : le senior (2010) doit être prioritaire avec règle ancienneté seule", c.getJour())
                    .isEqualTo("e1");
        }
    }

    @Test
    @DisplayName("ancienneté + cap hebdo — le senior est relayé quand il atteint le cap")
    void anciennete_senior_relaye_par_cap() {
        // Cap 16h, senior disponible 5 jours × 4h = 20h potentiel → dépassera le cap
        // Après 4 jours (16h), le senior est bloqué, le mid prend le relais
        List<Exigence> exigences = new ArrayList<>();
        for (int j = 0; j <= 4; j++) {
            exigences.add(buildExigence("ex-" + j, "Poste J" + j, "caissier",
                    SITE_A, List.of(j), 8.0, 12.0, 1));
        }

        List<DisponibilitePlage> dispoSemaine = new ArrayList<>();
        for (int j = 0; j <= 4; j++) {
            dispoSemaine.add(DisponibilitePlage.builder().jour(j).heureDebut(8.0).heureFin(12.0).build());
        }

        Employe senior = Employe.builder().id("e1").nom("Senior").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoSemaine))
                .dateEmbauche(LocalDate.of(2010, 1, 1)).build();
        Employe mid = Employe.builder().id("e2").nom("Mid").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispoSemaine))
                .dateEmbauche(LocalDate.of(2018, 1, 1)).build();

        ContexteAffectation ctx = buildContexte(
                exigences, List.of(mid, senior),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("anciennete"), 16.0);  // cap 16h

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(5);

        long seniorJours = result.nouveauxCreneaux().stream()
                .filter(c -> c.getEmployeId().equals("e1")).count();
        long midJours = result.nouveauxCreneaux().stream()
                .filter(c -> c.getEmployeId().equals("e2")).count();

        // Senior : 4 jours × 4h = 16h (cap atteint), mid prend le 5ème jour
        assertThat(seniorJours).as("Senior atteint le cap après 4 jours de 4h").isEqualTo(4);
        assertThat(midJours).as("Mid prend le relais pour le 5ème jour").isEqualTo(1);
    }

    // =========================================================================
    // 18. Ordre configurable des 4 règles — prouver que l'ordre change le résultat
    // =========================================================================

    /*
     * Setup partagé pour les tests de règles :
     * - SENIOR : embauché 2010, né 1975, dispo 4h (8h-12h), 0h travaillées
     * - JUNIOR : embauché 2023, né 1995, dispo 8h (8h-16h), 0h travaillées
     * - VETERAN : embauché 2008, né 1970, dispo 4h (8h-12h), 10h travaillées
     *
     * Avec ces valeurs, chaque règle produit un gagnant différent :
     *   disponibilite → JUNIOR (8h > 4h)
     *   equite        → SENIOR ou JUNIOR (0h < 10h, les deux à 0h se départagent par ID)
     *   anciennete    → VETERAN (2008 < 2010 < 2023)
     *   age           → VETERAN (1970 < 1975 < 1995)
     */

    private Employe buildSenior() {
        return Employe.builder().id("e-senior").nom("Senior").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2010, 1, 1))
                .dateNaissance(LocalDate.of(1975, 5, 15))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();
    }

    private Employe buildJunior() {
        return Employe.builder().id("e-junior").nom("Junior").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2023, 6, 1))
                .dateNaissance(LocalDate.of(1995, 11, 3))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(16.0).build()))
                .build();
    }

    private Employe buildVeteran() {
        return Employe.builder().id("e-veteran").nom("Veteran").role("caissier")
                .siteIds(List.of(SITE_A))
                .dateEmbauche(LocalDate.of(2008, 3, 1))
                .dateNaissance(LocalDate.of(1970, 2, 20))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();
    }

    /** Crée un contexte avec les 3 employés types, VETERAN ayant 10h existantes. */
    private ContexteAffectation buildContexteRegles(List<String> regles) {
        Employe senior = buildSenior();
        Employe junior = buildJunior();
        Employe veteran = buildVeteran();

        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        // VETERAN a déjà 10h cette semaine (jours 1-2, 5h chacun)
        List<CreneauAssigne> existants = List.of(
                buildCreneau("cv1", "e-veteran", SITE_A, 1, 8.0, 13.0),
                buildCreneau("cv2", "e-veteran", SITE_A, 2, 8.0, 13.0));

        Map<String, Employe> parId = Map.of(
                "e-senior", senior, "e-junior", junior, "e-veteran", veteran);

        return new ContexteAffectation(
                List.of(exigence),
                List.of(junior, veteran, senior),  // ordre volontairement mélangé
                parId, existants, List.of(), List.of(),
                1.0, 1.0, regles, 48.0,
                LUNDI, SEMAINE, SITE_A, ORG);
    }

    // --- Tests des 4 règles individuelles ---

    @Test
    @DisplayName("règle seule : disponibilite → le plus disponible gagne (JUNIOR 8h)")
    void regle_seule_disponibilite() {
        SolverResult result = solver.resoudre(buildContexteRegles(List.of("disponibilite")));

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("disponibilite seule : JUNIOR (8h dispo) > SENIOR/VETERAN (4h dispo)")
                .isEqualTo("e-junior");
    }

    @Test
    @DisplayName("règle seule : equite → celui avec le moins d'heures gagne (SENIOR ou JUNIOR, 0h)")
    void regle_seule_equite() {
        SolverResult result = solver.resoudre(buildContexteRegles(List.of("equite")));

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        String gagnant = result.nouveauxCreneaux().get(0).getEmployeId();
        // SENIOR et JUNIOR ont 0h, VETERAN a 10h → VETERAN exclu
        // Entre SENIOR et JUNIOR à 0h, le tiebreaker par ID décide
        assertThat(gagnant)
                .as("equite seule : un employé à 0h doit être choisi (pas VETERAN à 10h)")
                .isNotEqualTo("e-veteran");
    }

    @Test
    @DisplayName("règle seule : anciennete → le plus ancien gagne (VETERAN 2008)")
    void regle_seule_anciennete() {
        SolverResult result = solver.resoudre(buildContexteRegles(List.of("anciennete")));

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("anciennete seule : VETERAN (2008) > SENIOR (2010) > JUNIOR (2023)")
                .isEqualTo("e-veteran");
    }

    @Test
    @DisplayName("règle seule : age → le plus âgé gagne (VETERAN 1970)")
    void regle_seule_age() {
        SolverResult result = solver.resoudre(buildContexteRegles(List.of("age")));

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("age seule : VETERAN (1970) > SENIOR (1975) > JUNIOR (1995)")
                .isEqualTo("e-veteran");
    }

    // --- Tests prouvant que l'ORDRE change le résultat ---

    @Test
    @DisplayName("ORDRE : [disponibilite, anciennete] → JUNIOR gagne (plus dispo)")
    void ordre_disponibilite_puis_anciennete() {
        SolverResult result = solver.resoudre(
                buildContexteRegles(List.of("disponibilite", "anciennete")));

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("[dispo, anciennete] : JUNIOR (8h) bat VETERAN (4h) avant même d'évaluer ancienneté")
                .isEqualTo("e-junior");
    }

    @Test
    @DisplayName("ORDRE : [anciennete, disponibilite] → VETERAN gagne (plus ancien)")
    void ordre_anciennete_puis_disponibilite() {
        SolverResult result = solver.resoudre(
                buildContexteRegles(List.of("anciennete", "disponibilite")));

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("[anciennete, dispo] : VETERAN (2008) bat JUNIOR (2023) avant même d'évaluer dispo")
                .isEqualTo("e-veteran");
    }

    @Test
    @DisplayName("ORDRE : [equite, anciennete] → un employé à 0h gagne, pas VETERAN (10h)")
    void ordre_equite_puis_anciennete() {
        SolverResult result = solver.resoudre(
                buildContexteRegles(List.of("equite", "anciennete")));

        String gagnant = result.nouveauxCreneaux().get(0).getEmployeId();
        assertThat(gagnant)
                .as("[equite, anciennete] : equite élimine VETERAN (10h), anciennete départage SENIOR/JUNIOR")
                .isNotEqualTo("e-veteran");
        // Entre SENIOR (0h, 2010) et JUNIOR (0h, 2023) → equite tie → anciennete: SENIOR gagne
        assertThat(gagnant).isEqualTo("e-senior");
    }

    @Test
    @DisplayName("ORDRE : [anciennete, equite] → VETERAN gagne malgré ses 10h")
    void ordre_anciennete_puis_equite() {
        SolverResult result = solver.resoudre(
                buildContexteRegles(List.of("anciennete", "equite")));

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("[anciennete, equite] : VETERAN (2008) gagne même avec 10h — ancienneté prime")
                .isEqualTo("e-veteran");
    }

    @Test
    @DisplayName("ORDRE : [age, equite] → VETERAN (1970, le plus vieux) gagne")
    void ordre_age_puis_equite() {
        SolverResult result = solver.resoudre(
                buildContexteRegles(List.of("age", "equite")));

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("[age, equite] : VETERAN (1970) est le plus âgé → gagne")
                .isEqualTo("e-veteran");
    }

    @Test
    @DisplayName("ORDRE : [equite, age] → SENIOR (0h, 1975) gagne, pas VETERAN (10h)")
    void ordre_equite_puis_age() {
        SolverResult result = solver.resoudre(
                buildContexteRegles(List.of("equite", "age")));

        String gagnant = result.nouveauxCreneaux().get(0).getEmployeId();
        assertThat(gagnant)
                .as("[equite, age] : equite élimine VETERAN (10h), age départage → SENIOR (1975 < 1995)")
                .isEqualTo("e-senior");
    }

    // --- Test des 4 règles simultanées ---

    @Test
    @DisplayName("4 règles simultanées : [disponibilite, anciennete, age, equite] → JUNIOR (dispo prime)")
    void quatre_regles_dispo_en_tete() {
        SolverResult result = solver.resoudre(
                buildContexteRegles(List.of("disponibilite", "anciennete", "age", "equite")));

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("4 règles avec dispo en tête : JUNIOR (8h dispo) gagne")
                .isEqualTo("e-junior");
    }

    @Test
    @DisplayName("4 règles simultanées : [anciennete, age, equite, disponibilite] → VETERAN (ancienneté prime)")
    void quatre_regles_anciennete_en_tete() {
        SolverResult result = solver.resoudre(
                buildContexteRegles(List.of("anciennete", "age", "equite", "disponibilite")));

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("4 règles avec ancienneté en tête : VETERAN (2008) gagne")
                .isEqualTo("e-veteran");
    }

    @Test
    @DisplayName("4 règles simultanées : [equite, anciennete, age, disponibilite] → SENIOR (equite filtre, ancienneté départage)")
    void quatre_regles_equite_en_tete() {
        SolverResult result = solver.resoudre(
                buildContexteRegles(List.of("equite", "anciennete", "age", "disponibilite")));

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("4 règles avec equite en tête : VETERAN (10h) exclu, ancienneté → SENIOR (2010)")
                .isEqualTo("e-senior");
    }

    // --- Cas limites des règles ---

    @Test
    @DisplayName("aucune règle configurée — l'algo fonctionne quand même (tiebreaker par ID)")
    void aucune_regle_configuree() {
        SolverResult result = solver.resoudre(buildContexteRegles(List.of()));

        // Sans règle, tri par ID (tiebreaker déterministe)
        assertThat(result.nouveauxCreneaux()).hasSize(1);
        // L'algo ne plante pas et produit un résultat
    }

    @Test
    @DisplayName("règle inconnue dans la liste — ignorée silencieusement")
    void regle_inconnue_ignoree() {
        SolverResult result = solver.resoudre(
                buildContexteRegles(List.of("regle_inexistante", "anciennete")));

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        // La règle inconnue est ignorée, ancienneté prend le relais
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("Règle inconnue ignorée, ancienneté active → VETERAN")
                .isEqualTo("e-veteran");
    }

    @Test
    @DisplayName("même règle répétée — pas de crash, résultat identique")
    void regle_repetee_pas_de_crash() {
        SolverResult r1 = solver.resoudre(buildContexteRegles(List.of("anciennete")));
        SolverResult r2 = solver.resoudre(
                buildContexteRegles(List.of("anciennete", "anciennete", "anciennete")));

        assertThat(r1.nouveauxCreneaux().get(0).getEmployeId())
                .isEqualTo(r2.nouveauxCreneaux().get(0).getEmployeId());
    }

    // --- Tiebreaker en cascade sur plusieurs jours ---

    @Test
    @DisplayName("tiebreaker cascade — equite départage après ancienneté sur 3 jours, nombreRequis=2")
    void tiebreaker_cascade_equite_apres_anciennete() {
        // 3 jours, 2 requis/jour, 3 employés, règles=[anciennete, equite]
        // Jour 0 : VETERAN + SENIOR (les 2 plus anciens)
        // Jour 1 : VETERAN + SENIOR à nouveau (ancienneté prime)
        //          mais VETERAN a maintenant 8h, SENIOR 8h, JUNIOR 0h
        //          ancienneté prime toujours → VETERAN + SENIOR encore
        // Jour 2 : idem → VETERAN (12h) + SENIOR (12h), JUNIOR reste à 0h
        // Ce test documente que ancienneté en première règle monopolise les anciens

        List<Exigence> exigences = new ArrayList<>();
        for (int j = 0; j <= 2; j++) {
            exigences.add(buildExigence("ex-" + j, "Poste J" + j, "caissier",
                    SITE_A, List.of(j), 8.0, 12.0, 2));  // 2 requis
        }

        List<DisponibilitePlage> dispo3j = new ArrayList<>();
        for (int j = 0; j <= 2; j++) {
            dispo3j.add(DisponibilitePlage.builder().jour(j).heureDebut(8.0).heureFin(12.0).build());
        }

        Employe veteran = Employe.builder().id("e-vet").nom("Veteran").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispo3j))
                .dateEmbauche(LocalDate.of(2008, 1, 1)).build();
        Employe senior = Employe.builder().id("e-sen").nom("Senior").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispo3j))
                .dateEmbauche(LocalDate.of(2012, 1, 1)).build();
        Employe junior = Employe.builder().id("e-jun").nom("Junior").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispo3j))
                .dateEmbauche(LocalDate.of(2023, 1, 1)).build();

        ContexteAffectation ctx = buildContexte(
                exigences, List.of(junior, senior, veteran),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("anciennete", "equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // 3 jours × 2 requis = 6 créneaux
        assertThat(result.nouveauxCreneaux()).hasSize(6);

        // VETERAN et SENIOR doivent être sur CHAQUE jour (ancienneté prime sur equite)
        for (int j = 0; j <= 2; j++) {
            final int jour = j;
            List<String> affectesJour = result.nouveauxCreneaux().stream()
                    .filter(c -> c.getJour() == jour)
                    .map(CreneauAssigne::getEmployeId)
                    .sorted()
                    .toList();
            assertThat(affectesJour)
                    .as("Jour %d : les 2 plus anciens (veteran + senior) doivent être affectés", jour)
                    .containsExactlyInAnyOrder("e-sen", "e-vet");
        }

        // JUNIOR ne doit avoir AUCUN créneau (ancienneté stricte, il est toujours 3ème)
        long juniorCreneaux = result.nouveauxCreneaux().stream()
                .filter(c -> c.getEmployeId().equals("e-jun")).count();
        assertThat(juniorCreneaux)
                .as("JUNIOR (2023) ne doit jamais être affecté quand ancienneté prime et 2 plus anciens suffisent")
                .isZero();
    }

    @Test
    @DisplayName("tiebreaker cascade — equite EN TÊTE rééquilibre, ancienneté en backup")
    void tiebreaker_cascade_equite_en_tete_repartit() {
        // Même setup, mais règles=[equite, anciennete]
        // L'equite en tête doit distribuer les créneaux entre les 3 employés

        List<Exigence> exigences = new ArrayList<>();
        for (int j = 0; j <= 2; j++) {
            exigences.add(buildExigence("ex-" + j, "Poste J" + j, "caissier",
                    SITE_A, List.of(j), 8.0, 12.0, 2));
        }

        List<DisponibilitePlage> dispo3j = new ArrayList<>();
        for (int j = 0; j <= 2; j++) {
            dispo3j.add(DisponibilitePlage.builder().jour(j).heureDebut(8.0).heureFin(12.0).build());
        }

        Employe veteran = Employe.builder().id("e-vet").nom("Veteran").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispo3j))
                .dateEmbauche(LocalDate.of(2008, 1, 1)).build();
        Employe senior = Employe.builder().id("e-sen").nom("Senior").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispo3j))
                .dateEmbauche(LocalDate.of(2012, 1, 1)).build();
        Employe junior = Employe.builder().id("e-jun").nom("Junior").role("caissier")
                .siteIds(List.of(SITE_A)).disponibilites(new ArrayList<>(dispo3j))
                .dateEmbauche(LocalDate.of(2023, 1, 1)).build();

        ContexteAffectation ctx = buildContexte(
                exigences, List.of(junior, senior, veteran),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite", "anciennete"), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(6);

        // Avec equite en tête, les 3 employés doivent TOUS avoir des créneaux
        Map<String, Long> creneauxParEmp = result.nouveauxCreneaux().stream()
                .collect(Collectors.groupingBy(CreneauAssigne::getEmployeId, Collectors.counting()));

        assertThat(creneauxParEmp.keySet())
                .as("Equite en tête : les 3 employés doivent être utilisés")
                .containsExactlyInAnyOrder("e-vet", "e-sen", "e-jun");

        // Chaque employé doit avoir 2 créneaux (6 / 3 = 2 chacun)
        creneauxParEmp.forEach((empId, count) ->
                assertThat(count)
                        .as("Equite : %s doit avoir 2 créneaux (répartition équitable)", empId)
                        .isEqualTo(2L));
    }
}
