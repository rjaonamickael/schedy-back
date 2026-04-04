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
 * Unit tests for {@link GreedySolver} — integration scenarios:
 * multi-role, shortage, cross-site, stress test, and v24 complex scenarios.
 *
 * <p>Reference week: 2025-W02, Monday = 2025-01-06 (jour index 0).
 */
@DisplayName("GreedySolver — scénarios d'intégration (multi-rôles, pénurie, cross-site, stress)")
class GreedySolverIntegrationTest {

    private GreedySolver solver;

    @BeforeEach
    void setUp() {
        solver = new GreedySolver();
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
        assertAllInvariantsHold(result, ctx);

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
        assertAllInvariantsHold(result, ctx);

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
        assertAllInvariantsHold(result, ctx);

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
        assertAllInvariantsHold(result, ctx);

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
        assertAllInvariantsHold(result, ctx);

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
                1.0, 1.0, List.of("equite"), 48.0, 10.0, 0.0, 0.0, 0,
                null, null, List.of(),
                LUNDI, SEMAINE, null, ORG);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

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
                1.0, 1.0, List.of(), 48.0, 10.0, 0.0, 0.0, 0,
                null, null, List.of(),
                LUNDI, SEMAINE, null, ORG);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

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
                1.0, 1.0, List.of("anciennete", "equite"), 40.0, 10.0, 0.0, 0.0, 0,
                null, null, List.of(),
                LUNDI, SEMAINE, null, ORG);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

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
    // v24 — ALG-NEW-04 : exigences chevauchantes additives
    // =========================================================================

    @Test
    @DisplayName("ALG-NEW-04 — deux exigences même rôle/site/heure : couverture additive")
    void exigences_chevauchantes_couverture_additive() {
        // Exigence A : 2 caissiers 8h-12h + Exigence B : 1 caissier 8h-12h
        // Total requis = 3 caissiers
        Exigence exA = buildExigence("exA", "Caisse principale", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 2);
        Exigence exB = buildExigence("exB", "Caisse secondaire", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        DisponibilitePlage dispo = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(12.0).build();

        Employe emp1 = buildEmployeMultiDispo("e1", "Alice", "caissier", SITE_A, List.of(dispo));
        Employe emp2 = buildEmployeMultiDispo("e2", "Bob", "caissier", SITE_A, List.of(dispo));
        Employe emp3 = buildEmployeMultiDispo("e3", "Carol", "caissier", SITE_A, List.of(dispo));

        ContexteAffectation ctx = buildContexte(
                List.of(exA, exB),
                List.of(emp1, emp2, emp3),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        // Les 3 employés doivent être affectés (2 + 1 = 3 requis)
        assertThat(result.nouveauxCreneaux())
                .as("2 + 1 exigences chevauchantes = 3 employés requis au total")
                .hasSize(3);

        List<String> employes = result.nouveauxCreneaux().stream()
                .map(CreneauAssigne::getEmployeId).distinct().toList();
        assertThat(employes).hasSize(3);
    }

    // =========================================================================
    // v24 — ALG-NEW-05 : déterminisme du 2-swap (TreeMap)
    // =========================================================================

    @Test
    @DisplayName("ALG-NEW-05 — 2-swap produit le même résultat sur 10 exécutions")
    void deux_swap_determinisme() {
        Exigence ex1 = buildExigence("ex1", "Poste 1", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);
        Exigence ex2 = buildExigence("ex2", "Poste 2", "caissier",
                SITE_A, List.of(0), 12.0, 16.0, 1);

        DisponibilitePlage dispoFull = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(16.0).build();

        Employe emp1 = buildEmployeMultiDispo("e1", "Alice", "caissier", SITE_A, List.of(dispoFull));
        Employe emp2 = buildEmployeMultiDispo("e2", "Bob", "caissier", SITE_A, List.of(dispoFull));

        ContexteAffectation ctx = buildContexte(
                List.of(ex1, ex2), List.of(emp1, emp2),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        // Exécuter 10 fois et vérifier que le résultat est identique
        SolverResult reference = solver.resoudre(ctx);
        assertAllInvariantsHold(reference, ctx);
        for (int i = 0; i < 10; i++) {
            SolverResult run = solver.resoudre(ctx);
            assertThat(run.nouveauxCreneaux()).hasSameSizeAs(reference.nouveauxCreneaux());
            for (int j = 0; j < run.nouveauxCreneaux().size(); j++) {
                assertThat(run.nouveauxCreneaux().get(j).getEmployeId())
                        .isEqualTo(reference.nouveauxCreneaux().get(j).getEmployeId());
            }
        }
    }

    // =========================================================================
    // v24 — ALG-NEW-08 : fusion des créneaux contigus
    // =========================================================================

    @Test
    @DisplayName("ALG-NEW-08 — créneaux contigus fusionnés en un seul bloc")
    void creneaux_contigus_fusionnes() {
        // Exigence 8h-12h, 2 requis. emp1 seul disponible → couvrira en 1 pass
        // emp2 disponible aussi → greedy assigne les 2 en blocs complets
        // Pas de fragmentation possible ici. Testons avec un scénario de fragmentation.
        // Exigence 8h-16h, emp1 dispo 8h-16h. Granularité 1h, dureeMin 2h.
        // Si une autre exigence consomme les heures 10h-12h d'abord, emp1 sera
        // assigné en 2 blocs [8,10] et [12,16]. Les blocs non-contigus restent séparés.
        // Mais les contigus [8,10]+[10,12] seraient fusionnés si l'algo les crée.
        Exigence exigence = buildExigence("ex1", "Journée", "caissier",
                SITE_A, List.of(0), 8.0, 14.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 14.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        // Doit être un seul créneau continu 8h-14h (pas fragmenté)
        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getHeureDebut()).isEqualTo(8.0);
        assertThat(result.nouveauxCreneaux().get(0).getHeureFin()).isEqualTo(14.0);
    }

    // =========================================================================
    // v24 — Sous-couverture (M4)
    // =========================================================================

    @Test
    @DisplayName("M4 — sous-couverture quand il n'y a pas assez d'employés")
    void sous_couverture_ne_crash_pas() {
        // Exigence 3 caissiers mais seulement 2 disponibles
        Exigence exigence = buildExigence("ex1", "Rush", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 3);

        Employe emp1 = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 12.0);
        Employe emp2 = buildEmploye("e2", "Bob", "caissier", SITE_A, 0, 8.0, 12.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(emp1, emp2),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        // 2 employés affectés (le max possible), pas 3
        assertThat(result.nouveauxCreneaux())
                .as("Doit affecter le maximum possible (2/3) sans crash")
                .hasSize(2);
    }

    // =========================================================================
    // v24 — Exigences partiellement chevauchantes
    // =========================================================================

    @Test
    @DisplayName("exigences partiellement chevauchantes — couverture additive sur la zone de chevauchement")
    void exigences_partiellement_chevauchantes() {
        // Exigence A : 1 caissier 8h-12h
        // Exigence B : 1 caissier 10h-14h
        // Zone chevauchement 10h-12h : total requis = 2
        // Zone exclusive A (8h-10h) : 1 requis
        // Zone exclusive B (12h-14h) : 1 requis
        Exigence exA = buildExigence("exA", "Matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);
        Exigence exB = buildExigence("exB", "Mi-journée", "caissier",
                SITE_A, List.of(0), 10.0, 14.0, 1);

        DisponibilitePlage dispo = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(14.0).build();

        Employe emp1 = buildEmployeMultiDispo("e1", "Alice", "caissier", SITE_A, List.of(dispo));
        Employe emp2 = buildEmployeMultiDispo("e2", "Bob", "caissier", SITE_A, List.of(dispo));

        ContexteAffectation ctx = buildContexte(
                List.of(exA, exB), List.of(emp1, emp2),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        // Les 2 employés doivent être utilisés pour couvrir la zone de chevauchement
        assertThat(result.nouveauxCreneaux())
                .as("2 exigences chevauchantes nécessitent 2 employés dans la zone 10h-12h")
                .hasSizeGreaterThanOrEqualTo(2);
    }

    // =========================================================================
    // v24 — Stabilité : même input → même output
    // =========================================================================

    @Test
    @DisplayName("stabilité — 20 exécutions identiques sur scénario complexe")
    void stabilite_resultats_identiques() {
        List<Exigence> exigences = new ArrayList<>();
        for (int jour = 0; jour <= 4; jour++) {
            exigences.add(buildExigence("ex-" + jour, "Jour " + jour, "caissier",
                    SITE_A, List.of(jour), 8.0, 16.0, 2));
        }

        List<DisponibilitePlage> dispoSemaine = new ArrayList<>();
        for (int j = 0; j <= 4; j++) {
            dispoSemaine.add(DisponibilitePlage.builder().jour(j).heureDebut(8.0).heureFin(16.0).build());
        }

        List<Employe> employes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            employes.add(Employe.builder()
                    .id("e" + i).nom("Emp" + i).role("caissier")
                    .siteIds(List.of(SITE_A))
                    .disponibilites(new ArrayList<>(dispoSemaine))
                    .dateEmbauche(LocalDate.of(2015 + i, 1, 1))
                    .build());
        }

        ContexteAffectation ctx = buildContexte(
                exigences, employes,
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("anciennete", "equite"), 48.0);

        SolverResult ref = solver.resoudre(ctx);
        assertAllInvariantsHold(ref, ctx);
        for (int run = 0; run < 20; run++) {
            SolverResult r = solver.resoudre(ctx);
            assertThat(r.totalAffectes()).isEqualTo(ref.totalAffectes());
            for (int j = 0; j < r.nouveauxCreneaux().size(); j++) {
                assertThat(r.nouveauxCreneaux().get(j).getEmployeId())
                        .isEqualTo(ref.nouveauxCreneaux().get(j).getEmployeId());
            }
        }
    }

    // --- Multi-role 2-swap determinism (kills HashMap→TreeMap mutation) ---

    @Test
    @DisplayName("2-swap multi-role — résultat déterministe avec 3 rôles différents")
    void deux_swap_multi_role_deterministe() {
        // 3 rôles × 2 exigences × 2 employés chacun — le 2-swap doit itérer
        // les rôles dans le même ordre à chaque exécution (TreeMap = alphabétique)
        List<Exigence> exigences = List.of(
                buildExigence("exC1", "Caisse1", "caissier", SITE_A, List.of(0), 8.0, 14.0, 1),
                buildExigence("exC2", "Caisse2", "caissier", SITE_A, List.of(0), 14.0, 20.0, 1),
                buildExigence("exG1", "Gerant1", "gerant", SITE_A, List.of(0), 8.0, 14.0, 1),
                buildExigence("exG2", "Gerant2", "gerant", SITE_A, List.of(0), 14.0, 20.0, 1),
                buildExigence("exS1", "Serveur1", "serveur", SITE_A, List.of(0), 8.0, 14.0, 1),
                buildExigence("exS2", "Serveur2", "serveur", SITE_A, List.of(0), 14.0, 20.0, 1));

        DisponibilitePlage dispoFull = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(20.0).build();

        List<Employe> employes = List.of(
                buildEmployeMultiDispo("c1", "Caissier1", "caissier", SITE_A, List.of(dispoFull)),
                buildEmployeMultiDispo("c2", "Caissier2", "caissier", SITE_A, List.of(dispoFull)),
                buildEmployeMultiDispo("g1", "Gerant1", "gerant", SITE_A, List.of(dispoFull)),
                buildEmployeMultiDispo("g2", "Gerant2", "gerant", SITE_A, List.of(dispoFull)),
                buildEmployeMultiDispo("s1", "Serveur1", "serveur", SITE_A, List.of(dispoFull)),
                buildEmployeMultiDispo("s2", "Serveur2", "serveur", SITE_A, List.of(dispoFull)));

        ContexteAffectation ctx = buildContexte(
                exigences, employes, List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0);

        SolverResult ref = solver.resoudre(ctx);
        assertAllInvariantsHold(ref, ctx);
        assertThat(ref.nouveauxCreneaux()).hasSize(6);

        // 10 runs must produce identical results (determinism across role groups)
        for (int run = 0; run < 10; run++) {
            SolverResult r = solver.resoudre(ctx);
            for (int j = 0; j < r.nouveauxCreneaux().size(); j++) {
                assertThat(r.nouveauxCreneaux().get(j).getEmployeId())
                        .as("Run %d, creneau %d: employeId doit être identique", run, j)
                        .isEqualTo(ref.nouveauxCreneaux().get(j).getEmployeId());
            }
        }
    }
}
