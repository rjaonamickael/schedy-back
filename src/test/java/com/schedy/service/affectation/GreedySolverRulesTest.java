package com.schedy.service.affectation;

import com.schedy.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.schedy.service.affectation.SolverTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GreedySolver} — priority rules: seniority, age,
 * combined rules, multi-day seniority stability, configurable rule order,
 * and v24 MRV enrichment.
 *
 * <p>Reference week: 2025-W02, Monday = 2025-01-06 (jour index 0).
 *
 * <p>Setup shared by section 18 tests (buildContexteRegles):
 * <ul>
 *   <li>SENIOR  : hired 2010, born 1975, 4h available (8h-12h), 0h worked
 *   <li>JUNIOR  : hired 2023, born 1995, 8h available (8h-16h), 0h worked
 *   <li>VETERAN : hired 2008, born 1970, 4h available (8h-12h), 10h worked
 * </ul>
 * Each rule produces a different winner:
 * disponibilite → JUNIOR, equite → SENIOR or JUNIOR, anciennete → VETERAN, age → VETERAN.
 */
@DisplayName("GreedySolver — règles de priorité (ancienneté, âge, combinées, ordre configurable)")
class GreedySolverRulesTest {

    private GreedySolver solver;

    @BeforeEach
    void setUp() {
        solver = new GreedySolver();
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
        assertAllInvariantsHold(result, ctx);

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
        assertAllInvariantsHold(result, ctx);

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
        assertAllInvariantsHold(result, ctx);

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
        assertAllInvariantsHold(result, ctx);

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
        assertAllInvariantsHold(result, ctx);

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
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("Ancienneté avant équité : le senior doit être choisi à heures égales")
                .isEqualTo("e-senior");
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
        assertAllInvariantsHold(result, ctx);

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
        assertAllInvariantsHold(result, ctx);

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
    // 18. Ordre configurable des 4 règles — helpers
    // =========================================================================

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
                1.0, 1.0, regles, 48.0, 10.0, 0.0, 0.0, 0,
                null, null, List.of(),
                LUNDI, SEMAINE, SITE_A, ORG);
    }

    // =========================================================================
    // 18. Tests des 4 règles individuelles
    // =========================================================================

    @Test
    @DisplayName("règle seule : disponibilite → le plus disponible gagne (JUNIOR 8h)")
    void regle_seule_disponibilite() {
        ContexteAffectation ctx = buildContexteRegles(List.of("disponibilite"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("disponibilite seule : JUNIOR (8h dispo) > SENIOR/VETERAN (4h dispo)")
                .isEqualTo("e-junior");
    }

    @Test
    @DisplayName("règle seule : equite → celui avec le moins d'heures gagne (JUNIOR, 0h, tiebreaker ID)")
    void regle_seule_equite() {
        ContexteAffectation ctx = buildContexteRegles(List.of("equite"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        // SENIOR et JUNIOR ont 0h, VETERAN a 10h → VETERAN exclu
        // Entre SENIOR (e-senior) et JUNIOR (e-junior) à 0h, tiebreaker par ID : "e-junior" < "e-senior"
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("equite seule : JUNIOR (e-junior, ID alphabétiquement premier parmi les 0h) doit gagner")
                .isEqualTo("e-junior");
    }

    @Test
    @DisplayName("règle seule : anciennete → le plus ancien gagne (VETERAN 2008)")
    void regle_seule_anciennete() {
        ContexteAffectation ctx = buildContexteRegles(List.of("anciennete"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("anciennete seule : VETERAN (2008) > SENIOR (2010) > JUNIOR (2023)")
                .isEqualTo("e-veteran");
    }

    @Test
    @DisplayName("règle seule : age → le plus âgé gagne (VETERAN 1970)")
    void regle_seule_age() {
        ContexteAffectation ctx = buildContexteRegles(List.of("age"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("age seule : VETERAN (1970) > SENIOR (1975) > JUNIOR (1995)")
                .isEqualTo("e-veteran");
    }

    // =========================================================================
    // 18. Tests prouvant que l'ORDRE change le résultat
    // =========================================================================

    @Test
    @DisplayName("ORDRE : [disponibilite, anciennete] → JUNIOR gagne (plus dispo)")
    void ordre_disponibilite_puis_anciennete() {
        ContexteAffectation ctx = buildContexteRegles(List.of("disponibilite", "anciennete"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("[dispo, anciennete] : JUNIOR (8h) bat VETERAN (4h) avant même d'évaluer ancienneté")
                .isEqualTo("e-junior");
    }

    @Test
    @DisplayName("ORDRE : [anciennete, disponibilite] → VETERAN gagne (plus ancien)")
    void ordre_anciennete_puis_disponibilite() {
        ContexteAffectation ctx = buildContexteRegles(List.of("anciennete", "disponibilite"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("[anciennete, dispo] : VETERAN (2008) bat JUNIOR (2023) avant même d'évaluer dispo")
                .isEqualTo("e-veteran");
    }

    @Test
    @DisplayName("ORDRE : [equite, anciennete] → SENIOR gagne (equite filtre VETERAN, ancienneté départage)")
    void ordre_equite_puis_anciennete() {
        ContexteAffectation ctx = buildContexteRegles(List.of("equite", "anciennete"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        // equite élimine VETERAN (10h), ancienneté départage SENIOR (2010) vs JUNIOR (2023) → SENIOR gagne
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("[equite, anciennete] : equite élimine VETERAN (10h), anciennete → SENIOR (2010) gagne")
                .isEqualTo("e-senior");
    }

    @Test
    @DisplayName("ORDRE : [anciennete, equite] → VETERAN gagne malgré ses 10h")
    void ordre_anciennete_puis_equite() {
        ContexteAffectation ctx = buildContexteRegles(List.of("anciennete", "equite"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("[anciennete, equite] : VETERAN (2008) gagne même avec 10h — ancienneté prime")
                .isEqualTo("e-veteran");
    }

    @Test
    @DisplayName("ORDRE : [age, equite] → VETERAN (1970, le plus vieux) gagne")
    void ordre_age_puis_equite() {
        ContexteAffectation ctx = buildContexteRegles(List.of("age", "equite"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("[age, equite] : VETERAN (1970) est le plus âgé → gagne")
                .isEqualTo("e-veteran");
    }

    @Test
    @DisplayName("ORDRE : [equite, age] → SENIOR (0h, 1975) gagne, pas VETERAN (10h)")
    void ordre_equite_puis_age() {
        ContexteAffectation ctx = buildContexteRegles(List.of("equite", "age"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("[equite, age] : equite élimine VETERAN (10h), age départage → SENIOR (1975 < 1995)")
                .isEqualTo("e-senior");
    }

    // =========================================================================
    // 18. Tests des 4 règles simultanées
    // =========================================================================

    @Test
    @DisplayName("4 règles simultanées : [disponibilite, anciennete, age, equite] → JUNIOR (dispo prime)")
    void quatre_regles_dispo_en_tete() {
        ContexteAffectation ctx = buildContexteRegles(List.of("disponibilite", "anciennete", "age", "equite"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("4 règles avec dispo en tête : JUNIOR (8h dispo) gagne")
                .isEqualTo("e-junior");
    }

    @Test
    @DisplayName("4 règles simultanées : [anciennete, age, equite, disponibilite] → VETERAN (ancienneté prime)")
    void quatre_regles_anciennete_en_tete() {
        ContexteAffectation ctx = buildContexteRegles(List.of("anciennete", "age", "equite", "disponibilite"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("4 règles avec ancienneté en tête : VETERAN (2008) gagne")
                .isEqualTo("e-veteran");
    }

    @Test
    @DisplayName("4 règles simultanées : [equite, anciennete, age, disponibilite] → SENIOR (equite filtre, ancienneté départage)")
    void quatre_regles_equite_en_tete() {
        ContexteAffectation ctx = buildContexteRegles(List.of("equite", "anciennete", "age", "disponibilite"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("4 règles avec equite en tête : VETERAN (10h) exclu, ancienneté → SENIOR (2010)")
                .isEqualTo("e-senior");
    }

    // =========================================================================
    // 18. Cas limites des règles
    // =========================================================================

    @Test
    @DisplayName("aucune règle configurée — l'algo fonctionne quand même (tiebreaker par ID)")
    void aucune_regle_configuree() {
        ContexteAffectation ctx = buildContexteRegles(List.of());
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        // Sans règle, tri par ID (tiebreaker déterministe)
        assertThat(result.nouveauxCreneaux()).hasSize(1);
        // L'algo ne plante pas et produit un résultat
    }

    @Test
    @DisplayName("règle inconnue dans la liste — ignorée silencieusement")
    void regle_inconnue_ignoree() {
        ContexteAffectation ctx = buildContexteRegles(List.of("regle_inexistante", "anciennete"));
        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        // La règle inconnue est ignorée, ancienneté prend le relais
        assertThat(result.nouveauxCreneaux().get(0).getEmployeId())
                .as("Règle inconnue ignorée, ancienneté active → VETERAN")
                .isEqualTo("e-veteran");
    }

    @Test
    @DisplayName("même règle répétée — pas de crash, résultat identique")
    void regle_repetee_pas_de_crash() {
        ContexteAffectation ctx1 = buildContexteRegles(List.of("anciennete"));
        ContexteAffectation ctx2 = buildContexteRegles(List.of("anciennete", "anciennete", "anciennete"));
        SolverResult r1 = solver.resoudre(ctx1);
        SolverResult r2 = solver.resoudre(ctx2);
        assertAllInvariantsHold(r1, ctx1);
        assertAllInvariantsHold(r2, ctx2);

        assertThat(r1.nouveauxCreneaux().get(0).getEmployeId())
                .isEqualTo(r2.nouveauxCreneaux().get(0).getEmployeId());
    }

    // =========================================================================
    // 18. Tiebreaker en cascade sur plusieurs jours
    // =========================================================================

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
        assertAllInvariantsHold(result, ctx);

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
        assertAllInvariantsHold(result, ctx);

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

    // =========================================================================
    // v24 — ALG-NEW-10 : MRV enrichi priorise correctement
    // =========================================================================

    @Test
    @DisplayName("ALG-NEW-10 — MRV enrichi priorise l'exigence avec le seul candidat éligible")
    void mrv_enrichi_priorise_exigence_plus_contrainte() {
        // ex1 : 2 candidats potentiels (emp1, emp2)
        // ex2 : 1 seul candidat (emp1) — emp2 est en congé pour ex2
        // Sans enrichissement MRV, ex2 aurait le même ratio que ex1.
        // Avec enrichissement, ex2 est plus contrainte → traitée en premier.
        Exigence ex1 = buildExigence("ex1", "Matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);
        Exigence ex2 = buildExigence("ex2", "Après-midi", "caissier",
                SITE_A, List.of(0), 14.0, 18.0, 1);

        DisponibilitePlage dispoFull = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(18.0).build();

        Employe emp1 = buildEmployeMultiDispo("e1", "Alice", "caissier", SITE_A, List.of(dispoFull));
        Employe emp2 = buildEmployeMultiDispo("e2", "Bob", "caissier", SITE_A, List.of(dispoFull));

        // emp2 en congé l'après-midi → seul emp1 peut couvrir ex2
        DemandeConge conge = DemandeConge.builder()
                .id("cg1").employeId("e2").typeCongeId("cp")
                .dateDebut(LocalDate.of(2025, 1, 6))
                .dateFin(LocalDate.of(2025, 1, 6))
                .heureDebut(14.0).heureFin(18.0)
                .duree(0.5).statut(StatutDemande.approuve)
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(ex1, ex2), List.of(emp1, emp2),
                List.of(), List.of(conge), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        // Les 2 exigences doivent être couvertes
        assertThat(result.nouveauxCreneaux()).hasSize(2);

        // emp1 doit couvrir ex2 (14h-18h) car c'est le seul candidat
        boolean ex2CouverteParEmp1 = result.nouveauxCreneaux().stream()
                .anyMatch(c -> c.getHeureDebut() >= 14.0 - 1e-9 && c.getEmployeId().equals("e1"));
        assertThat(ex2CouverteParEmp1)
                .as("emp1 doit couvrir ex2 (seul candidat dispo l'après-midi)")
                .isTrue();
    }
}
