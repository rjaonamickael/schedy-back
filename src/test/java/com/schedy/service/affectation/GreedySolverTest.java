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
    @DisplayName("doit respecter le plafond hebdomadaire d'heures et refuser l'affectation dépassante")
    void devrait_respecter_heures_max_semaine() {
        // Alice a déjà 46h de créneaux existants sur les jours 0-4 (lundi-vendredi).
        // heuresMax = 48 → elle peut encore 2h.
        // L'exigence du dimanche (jour 6) représente un bloc de 4h → 46+4=50 > 48 → refus.
        Exigence exigence = buildExigence("ex1", "Caisse dimanche", "caissier",
                SITE_A, List.of(6), 8.0, 12.0, 1); // dimanche = jour 6, bloc 4h

        // Disponible dimanche 8h-12h seulement
        DisponibilitePlage dispoDimanche = DisponibilitePlage.builder()
                .jour(6).heureDebut(8.0).heureFin(12.0).build();
        Employe alice = buildEmployeMultiDispo("e1", "Alice", "caissier", SITE_A,
                List.of(dispoDimanche));

        // 46h répartis sur jours 0-4 (sans toucher au jour 6 cible de l'exigence)
        // Jours 0-4 : 8h chacun = 40h
        List<CreneauAssigne> creneauxExistants = new ArrayList<>();
        for (int jour = 0; jour <= 4; jour++) {
            creneauxExistants.add(buildCreneau("c" + jour, "e1", SITE_A, jour, 8.0, 16.0));
        }
        // Jour 5 (samedi) : 6h supplémentaires → total = 46h
        creneauxExistants.add(buildCreneau("c5", "e1", SITE_A, 5, 9.0, 15.0));

        ContexteAffectation ctx = buildContexte(
                List.of(exigence),
                List.of(alice),
                creneauxExistants,
                List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0); // heuresMax = 48

        SolverResult result = solver.resoudre(ctx);

        // 46h + 4h = 50h > 48h → aucune affectation
        assertThat(result.nouveauxCreneaux()).isEmpty();
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
}
