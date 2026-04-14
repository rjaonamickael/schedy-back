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
 * Unit tests for {@link GreedySolver} — constraint handling:
 * partial leave, multi-site filtering, weekly cap, public holidays,
 * fragmented availability, and v24 constraint scenarios.
 *
 * <p>Reference week: 2025-W02, Monday = 2025-01-06 (jour index 0).
 */
@DisplayName("GreedySolver — contraintes (congé partiel, multi-site, cap, férié, disponibilités)")
class GreedySolverConstraintsTest {

    private GreedySolver solver;

    @BeforeEach
    void setUp() {
        solver = new GreedySolver();
    }

    // =========================================================================
    // 8. Congé partiel — heures
    // =========================================================================

    @Test
    @DisplayName("congé partiel — l'employé est planifiable UNIQUEMENT hors congé (fix ALG-NEW-03)")
    void conge_partiel_bloque_slot_chevauchant() {
        // Leave 13h-17h on Monday. Exigence requires 14h-18h.
        // With per-slot leave check (ALG-NEW-03): slots 14h-17h are blocked,
        // but slot 17h-18h is outside the leave → Alice can work 17h-18h (1h).
        Exigence exigence = buildExigence("ex1", "Caisse aprem", "caissier",
                SITE_A, List.of(0), 14.0, 18.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
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
        assertAllInvariantsHold(result, ctx);

        // Alice can work 17h-18h (outside leave window) — exactly 1 creneau
        assertThat(result.nouveauxCreneaux())
                .as("Per-slot leave: Alice est planifiable 17h-18h (hors congé 13h-17h)")
                .hasSize(1);

        // No creneau must overlap the leave window 13h-17h
        for (var c : result.nouveauxCreneaux()) {
            assertThat(c.getHeureDebut())
                    .as("Aucun créneau ne doit commencer avant 17h (congé 13h-17h)")
                    .isGreaterThanOrEqualTo(17.0 - 1e-9);
        }
    }

    @Test
    @DisplayName("congé partiel — l'employé reste planifiable en dehors du congé")
    void conge_partiel_planifiable_hors_conge() {
        // Leave 13h-17h on Monday. Exigence requires 8h-12h → NO OVERLAP → ok.
        Exigence exigence = buildExigence("ex1", "Caisse matin", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
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
        assertAllInvariantsHold(result, ctx);

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
                .id("e-a").nom("EmpA").roles(List.of("caissier"))
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(dispoLundi))
                .build();

        Employe empB = Employe.builder()
                .id("e-b").nom("EmpB").roles(List.of("caissier"))
                .siteIds(List.of(SITE_B))
                .disponibilites(List.of(dispoLundi))
                .build();

        Map<String, Employe> employeParId = Map.of("e-a", empA, "e-b", empB);
        ContexteAffectation ctx = new ContexteAffectation(
                List.of(exigence),
                List.of(empA, empB),
                employeParId,
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0, 10.0, 0.0, 0.0, 0,
                null, null, List.of(),
                LUNDI, SEMAINE,
                null,   // org-wide
                ORG
        );

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

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
                .id("e-ab").nom("EmpAB").roles(List.of("caissier"))
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
                1.0, 1.0, List.of(), 48.0, 10.0, 0.0, 0.0, 0,
                null, null, List.of(),
                LUNDI, SEMAINE,
                null,
                ORG
        );

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

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
                .id("e1").nom("Alice").roles(List.of("caissier"))
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
        assertAllInvariantsHold(result, ctx);

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
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux())
                .as("Le jour férié de SITE_B ne doit pas bloquer l'affectation sur SITE_A")
                .hasSize(1);
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

        Employe alice = Employe.builder().id("e1").nom("Alice").roles(List.of("caissier"))
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
        assertAllInvariantsHold(result, ctx);

        // L'algo assigne le plus long bloc d'abord (8h-12h), puis la boucle
        // while(changed) réentre et assigne le second bloc (14h-16h) car l'exigence
        // requiert une couverture 8h-16h. Les 2 blocs sont non-contigus (trou 12h-14h).
        assertThat(result.nouveauxCreneaux()).hasSize(2);
        assertThat(result.nouveauxCreneaux().get(0).getHeureDebut()).isEqualTo(8.0);
        assertThat(result.nouveauxCreneaux().get(0).getHeureFin()).isEqualTo(12.0);
        assertThat(result.nouveauxCreneaux().get(1).getHeureDebut()).isEqualTo(14.0);
        assertThat(result.nouveauxCreneaux().get(1).getHeureFin()).isEqualTo(16.0);
    }

    @Test
    @DisplayName("disponibilité fragmentée — granularité 0.5h avec micro-plages")
    void disponibilite_fragmentee_granularite_fine() {
        // Exigence 9h-11h, granularité 0.5h
        // Employé disponible 9h-10h et 10h-11h (deux plages contiguës)
        Exigence exigence = buildExigence("ex1", "Matin court", "caissier",
                SITE_A, List.of(0), 9.0, 11.0, 1);

        Employe alice = Employe.builder().id("e1").nom("Alice").roles(List.of("caissier"))
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
        assertAllInvariantsHold(result, ctx);

        // Les 2 plages contiguës doivent fusionner en un seul créneau 9h-11h
        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getHeureDebut()).isEqualTo(9.0);
        assertThat(result.nouveauxCreneaux().get(0).getHeureFin()).isEqualTo(11.0);
    }

    // =========================================================================
    // v24 — ALG-NEW-03 : congé partiel ne bloque plus l'entière exigence
    // =========================================================================

    @Test
    @DisplayName("ALG-NEW-03 — congé partiel 10h-12h : employé planifiable 8h-10h ET 12h-16h")
    void conge_partiel_ne_bloque_plus_exigence_entiere() {
        // Exigence 8h-16h, congé 10h-12h → employé peut couvrir 8h-10h et 12h-16h
        Exigence exigence = buildExigence("ex1", "Journée", "caissier",
                SITE_A, List.of(0), 8.0, 16.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(16.0).build()))
                .build();

        DemandeConge conge = DemandeConge.builder()
                .id("cg1").employeId("e1").typeCongeId("cp")
                .dateDebut(LocalDate.of(2025, 1, 6))
                .dateFin(LocalDate.of(2025, 1, 6))
                .heureDebut(10.0).heureFin(12.0)
                .duree(0.25).statut(StatutDemande.approuve)
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                List.of(), List.of(conge), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        // L'employé doit être affecté sur les heures HORS congé (8h-10h et/ou 12h-16h)
        assertThat(result.nouveauxCreneaux())
                .as("L'employé avec congé partiel doit être planifiable hors congé")
                .isNotEmpty();

        // Aucun créneau ne doit chevaucher la plage de congé 10h-12h
        for (var c : result.nouveauxCreneaux()) {
            boolean overlap = c.getHeureDebut() < 12.0 - 1e-9 && 10.0 < c.getHeureFin() - 1e-9;
            assertThat(overlap)
                    .as("Aucun créneau ne doit chevaucher le congé 10h-12h (créneau %.1f-%.1f)",
                            c.getHeureDebut(), c.getHeureFin())
                    .isFalse();
        }
    }

    // =========================================================================
    // v24 — dureeMaxJour : cap journalier
    // =========================================================================

    @Test
    @DisplayName("dureeMaxJour — employé ne dépasse pas la durée max par jour")
    void dureeMaxJour_respecte() {
        // Exigence 8h-20h (12h), mais dureeMaxJour=8h → bloc tronqué à 8h
        Exigence exigence = buildExigence("ex1", "Journée longue", "caissier",
                SITE_A, List.of(0), 8.0, 20.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 20.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0, 8.0);  // dureeMaxJour = 8h

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        double totalJour = result.nouveauxCreneaux().stream()
                .filter(c -> c.getEmployeId().equals("e1") && c.getJour() == 0)
                .mapToDouble(c -> c.getHeureFin() - c.getHeureDebut())
                .sum();
        assertThat(totalJour)
                .as("L'employé ne doit pas dépasser dureeMaxJour=8h sur un jour")
                .isLessThanOrEqualTo(8.0 + 0.001);
    }

    @Test
    @DisplayName("dureeMaxJour — skip employé si déjà au cap journalier")
    void dureeMaxJour_skip_si_atteint() {
        // Alice a déjà 8h lundi (existant). dureeMaxJour=8h. Exigence mardi.
        Exigence exigenceMardi = buildExigence("ex1", "Mardi", "caissier",
                SITE_A, List.of(1), 8.0, 12.0, 1);
        Exigence exigenceLundiSoir = buildExigence("ex2", "Lundi soir", "caissier",
                SITE_A, List.of(0), 16.0, 20.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(20.0).build(),
                        DisponibilitePlage.builder().jour(1).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        // Alice a déjà 8h lundi
        CreneauAssigne existant = buildCreneau("c0", "e1", SITE_A, 0, 8.0, 16.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exigenceLundiSoir, exigenceMardi), List.of(alice),
                List.of(existant), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0, 8.0);  // dureeMaxJour = 8h

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        // Lundi soir ne doit PAS être assigné (déjà 8h)
        boolean lundiSoirAssigne = result.nouveauxCreneaux().stream()
                .anyMatch(c -> c.getJour() == 0);
        assertThat(lundiSoirAssigne)
                .as("Alice ne doit pas être affectée lundi soir (déjà 8h = cap)")
                .isFalse();

        // Mardi doit être assigné (jour différent)
        boolean mardiAssigne = result.nouveauxCreneaux().stream()
                .anyMatch(c -> c.getJour() == 1);
        assertThat(mardiAssigne)
                .as("Alice doit être affectée mardi (nouveau jour, cap remis à 0)")
                .isTrue();
    }

    // =========================================================================
    // v24 — Cap hebdo exactement atteint (M6)
    // =========================================================================

    @Test
    @DisplayName("M6 — cap hebdomadaire atteint exactement (remaining = 0)")
    void cap_hebdo_exactement_atteint() {
        // Alice a exactement 48h. Exigence dimanche.
        Exigence exigence = buildExigence("ex1", "Dimanche", "caissier",
                SITE_A, List.of(6), 8.0, 12.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(6).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        // 48h exactes : 6 jours × 8h
        List<CreneauAssigne> existants = new ArrayList<>();
        for (int jour = 0; jour <= 5; jour++) {
            existants.add(buildCreneau("c" + jour, "e1", SITE_A, jour, 8.0, 16.0));
        }

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                existants, List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // 48h atteintes, remaining = 0 → aucune affectation
        assertThat(result.nouveauxCreneaux())
                .as("Cap 48h exactement atteint → aucune affectation possible")
                .isEmpty();
    }

    // =========================================================================
    // v24 — Employé avec siteIds = null (M7)
    // =========================================================================

    @Test
    @DisplayName("M7 — employé avec siteIds null est exclu sans NPE")
    void employe_siteids_null_exclu_sans_npe() {
        Exigence exigence = buildExigence("ex1", "Caisse", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe sansites = Employe.builder()
                .id("e1").nom("SansSites").roles(List.of("caissier"))
                .siteIds(null)
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(sansites),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0);

        // Ne doit pas lever de NPE
        SolverResult result = solver.resoudre(ctx);
        assertThat(result.nouveauxCreneaux()).isEmpty();
    }

    // =========================================================================
    // v24 — Congé multi-jours (M2)
    // =========================================================================

    @Test
    @DisplayName("M2 — congé multi-jours bloque l'employé sur tous les jours intermédiaires")
    void conge_multi_jours_bloque_jours_intermediaires() {
        // Congé lundi-mercredi (3 jours). Exigences lundi et mardi.
        Exigence exLundi = buildExigence("ex1", "Lundi", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);
        Exigence exMardi = buildExigence("ex2", "Mardi", "caissier",
                SITE_A, List.of(1), 8.0, 12.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(12.0).build(),
                        DisponibilitePlage.builder().jour(1).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        // Congé lundi à mercredi (jours entiers)
        DemandeConge conge = DemandeConge.builder()
                .id("cg1").employeId("e1").typeCongeId("cp")
                .dateDebut(LocalDate.of(2025, 1, 6))   // lundi
                .dateFin(LocalDate.of(2025, 1, 8))     // mercredi
                .duree(3.0).statut(StatutDemande.approuve)
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exLundi, exMardi), List.of(alice),
                List.of(), List.of(conge), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // Alice est en congé lundi et mardi → aucune affectation
        assertThat(result.nouveauxCreneaux())
                .as("Alice en congé lundi-mercredi ne doit pas être affectée lundi ni mardi")
                .isEmpty();
    }

    // =========================================================================
    // v24 — Granularité 0.5 sans float drift (M1)
    // =========================================================================

    @Test
    @DisplayName("M1 — granularité 0.5 sur 8h d'amplitude sans drift")
    void granularite_fine_sans_drift() {
        // Exigence 8h-16h avec granularité 0.5 (16 slots)
        Exigence exigence = buildExigence("ex1", "Journée", "caissier",
                SITE_A, List.of(0), 8.0, 16.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 16.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                List.of(), List.of(), List.of(),
                0.5, 0.5, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        // Un seul créneau couvrant toute la plage (fusionné)
        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getHeureDebut()).isEqualTo(8.0);
        assertThat(result.nouveauxCreneaux().get(0).getHeureFin()).isEqualTo(16.0);
    }

    // =========================================================================
    // v24 — Jour férié récurrent cross-année (M5)
    // =========================================================================

    @Test
    @DisplayName("M5 — jour férié récurrent mois+jour bloque sur une autre année")
    void ferie_recurrent_cross_annee() {
        // Jour férié récurrent le 6 janvier (ex: Épiphanie)
        // La semaine test est 2025-W02, lundi = 2025-01-06
        Exigence exigence = buildExigence("ex1", "Caisse", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 12.0);

        JourFerie ferie = JourFerie.builder()
                .id("jf1").nom("Épiphanie")
                .date(LocalDate.of(2020, 1, 6))  // année différente mais même mois/jour
                .recurrent(true)
                .build();

        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                List.of(), List.of(), List.of(ferie),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        // Le 6 janvier est férié récurrent → pas d'affectation
        assertThat(result.nouveauxCreneaux())
                .as("Jour férié récurrent 6 janvier doit bloquer même en 2025")
                .isEmpty();
    }

    // =========================================================================
    // L1 — Repos inter-shifts (country-configurable)
    // =========================================================================

    @Test
    @DisplayName("L1 — repos 11h : employé finissant à 22h ne peut pas commencer avant 9h le lendemain")
    void repos_inter_shifts_bloque_si_gap_insuffisant() {
        // Lundi 14h-22h existant. Exigence mardi 6h-10h. Repos min = 11h.
        // Gap = 6h + 24 - 22h = 8h < 11h → BLOQUE.
        Exigence exMardi = buildExigence("ex1", "Mardi matin", "caissier",
                SITE_A, List.of(1), 6.0, 10.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(14.0).heureFin(22.0).build(),
                        DisponibilitePlage.builder().jour(1).heureDebut(6.0).heureFin(10.0).build()))
                .build();

        CreneauAssigne lundiSoir = buildCreneau("c1", "e1", SITE_A, 0, 14.0, 22.0);

        // reposMinEntreShifts = 11h
        ContexteAffectation ctx = buildContexte(
                List.of(exMardi), List.of(alice),
                List.of(lundiSoir), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0, 10.0, 11.0, 0.0, 0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux())
                .as("Gap 8h < repos min 11h → employé bloqué mardi matin")
                .isEmpty();
    }

    @Test
    @DisplayName("L1 — repos 11h respecté : employé finissant à 18h peut commencer à 6h le lendemain")
    void repos_inter_shifts_ok_si_gap_suffisant() {
        // Lundi 10h-18h existant. Exigence mardi 6h-10h. Gap = 6+24-18 = 12h > 11h → OK.
        Exigence exMardi = buildExigence("ex1", "Mardi matin", "caissier",
                SITE_A, List.of(1), 6.0, 10.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(1).heureDebut(6.0).heureFin(10.0).build()))
                .build();

        CreneauAssigne lundiJour = buildCreneau("c1", "e1", SITE_A, 0, 10.0, 18.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exMardi), List.of(alice),
                List.of(lundiJour), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0, 10.0, 11.0, 0.0, 0);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux())
                .as("Gap 12h >= repos min 11h → employé autorisé mardi matin")
                .hasSize(1);
    }

    @Test
    @DisplayName("L1 — repos 11h : edge case lundi (jour=0) avec shift dimanche (jour=6)")
    void repos_inter_shifts_lundi_dimanche_wrap_around() {
        // Dimanche 20h-23h existant. Exigence lundi 6h-10h. Repos min = 11h.
        // Gap = 6h + 24 - 23h = 7h < 11h → BLOQUE (wrap-around jour 6→0).
        Exigence exLundi = buildExigence("ex1", "Lundi matin", "caissier",
                SITE_A, List.of(0), 6.0, 10.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(6).heureDebut(20.0).heureFin(23.0).build(),
                        DisponibilitePlage.builder().jour(0).heureDebut(6.0).heureFin(10.0).build()))
                .build();

        CreneauAssigne dimancheSoir = buildCreneau("c1", "e1", SITE_A, 6, 20.0, 23.0);

        ContexteAffectation ctx = buildContexte(
                List.of(exLundi), List.of(alice),
                List.of(dimancheSoir), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0, 10.0, 11.0, 0.0, 0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux())
                .as("Dimanche 23h → Lundi 6h = gap 7h < repos min 11h → BLOQUE (wrap jour 6→0)")
                .isEmpty();
    }

    // =========================================================================
    // L2 — Repos hebdomadaire
    // =========================================================================

    @Test
    @DisplayName("L2 — repos hebdo 35h : employé travaillant 7j/7 est bloqué")
    void repos_hebdo_bloque_7_jours_sur_7() {
        // Alice travaille déjà lundi-samedi. Exigence dimanche. Repos hebdo = 35h.
        // Aucun jour libre → pas de bloc de 35h → BLOQUE.
        Exigence exDimanche = buildExigence("ex1", "Dimanche", "caissier",
                SITE_A, List.of(6), 8.0, 12.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(6).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        List<CreneauAssigne> existants = new ArrayList<>();
        for (int j = 0; j <= 5; j++) {
            existants.add(buildCreneau("c" + j, "e1", SITE_A, j, 8.0, 16.0));
        }

        // reposHebdoMin = 35h (FR: 24h + 11h)
        ContexteAffectation ctx = buildContexte(
                List.of(exDimanche), List.of(alice),
                existants, List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0, 10.0, 0.0, 35.0, 0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux())
                .as("7j/7 → aucun bloc de 35h de repos → employé bloqué dimanche")
                .isEmpty();
    }

    @Test
    @DisplayName("L2 — repos hebdo 24h : employé avec 1 jour libre passe")
    void repos_hebdo_ok_avec_jour_libre() {
        // Alice travaille lundi-vendredi. Samedi libre. Exigence dimanche. Repos = 24h.
        // Samedi = 1 jour libre = 24h → OK.
        Exigence exDimanche = buildExigence("ex1", "Dimanche", "caissier",
                SITE_A, List.of(6), 8.0, 12.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(6).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        List<CreneauAssigne> existants = new ArrayList<>();
        for (int j = 0; j <= 4; j++) { // lundi-vendredi seulement
            existants.add(buildCreneau("c" + j, "e1", SITE_A, j, 8.0, 16.0));
        }

        // reposHebdoMin = 24h (MG)
        ContexteAffectation ctx = buildContexte(
                List.of(exDimanche), List.of(alice),
                existants, List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0, 10.0, 0.0, 24.0, 0);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        assertThat(result.nouveauxCreneaux())
                .as("Samedi libre = 24h repos → dimanche autorisé")
                .hasSize(1);
    }

    // =========================================================================
    // L5 — Max jours consécutifs
    // =========================================================================

    @Test
    @DisplayName("L5 — max 6 jours consécutifs : 7ème jour bloqué")
    void max_jours_consecutifs_bloque_7eme_jour() {
        Exigence exDimanche = buildExigence("ex1", "Dimanche", "caissier",
                SITE_A, List.of(6), 8.0, 12.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(6).heureDebut(8.0).heureFin(12.0).build()))
                .build();

        List<CreneauAssigne> existants = new ArrayList<>();
        for (int j = 0; j <= 5; j++) {
            existants.add(buildCreneau("c" + j, "e1", SITE_A, j, 8.0, 16.0));
        }

        // maxJoursConsecutifs = 6
        ContexteAffectation ctx = buildContexte(
                List.of(exDimanche), List.of(alice),
                existants, List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0, 10.0, 0.0, 0.0, 6);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux())
                .as("6 jours consécutifs déjà travaillés → 7ème jour bloqué")
                .isEmpty();
    }

    // =========================================================================
    // B1 — 2-swap vérifie dureeMaxJour
    // =========================================================================

    @Test
    @DisplayName("B1 — 2-swap ne viole pas dureeMaxJour pour empMin")
    void deux_swap_respecte_dureeMaxJour() {
        // empA : 8h cette semaine (1 créneau 8h lundi), empB : 4h (1 créneau 4h lundi)
        // dureeMaxJour = 6h. empB a déjà 4h lundi. Swap de 4h → 4+4=8 > 6 → INTERDIT.
        Exigence ex1 = buildExigence("ex1", "Lundi matin", "caissier",
                SITE_A, List.of(0), 8.0, 16.0, 1);
        Exigence ex2 = buildExigence("ex2", "Lundi aprem", "caissier",
                SITE_A, List.of(0), 16.0, 20.0, 1);

        DisponibilitePlage dispoFull = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(20.0).build();

        Employe empA = buildEmployeMultiDispo("eA", "EmpA", "caissier", SITE_A, List.of(dispoFull));
        Employe empB = buildEmployeMultiDispo("eB", "EmpB", "caissier", SITE_A, List.of(dispoFull));

        // dureeMaxJour = 6h, règle equité → 2-swap actif
        ContexteAffectation ctx = buildContexte(
                List.of(ex1, ex2), List.of(empA, empB),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite"), 48.0, 6.0);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        // Vérifier que personne ne dépasse 6h/jour
        for (var c : result.nouveauxCreneaux()) {
            double heuresJour = result.nouveauxCreneaux().stream()
                    .filter(cc -> cc.getEmployeId().equals(c.getEmployeId()) && cc.getJour() == c.getJour())
                    .mapToDouble(cc -> cc.getHeureFin() - cc.getHeureDebut())
                    .sum();
            assertThat(heuresJour)
                    .as("Employé %s jour %d ne doit pas dépasser dureeMaxJour=6h (a %.1fh)",
                            c.getEmployeId(), c.getJour(), heuresJour)
                    .isLessThanOrEqualTo(6.0 + 0.001);
        }
    }

    // =========================================================================
    // B2 — Validation des entrées
    // =========================================================================

    @Test
    @DisplayName("B2 — granularité 0 lève IllegalArgumentException")
    void granularite_zero_lance_exception() {
        Exigence exigence = buildExigence("ex1", "Test", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);
        Employe emp = buildEmploye("e1", "Test", "caissier", SITE_A, 0, 8.0, 12.0);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                solver.resoudre(buildContexte(
                        List.of(exigence), List.of(emp),
                        List.of(), List.of(), List.of(),
                        1.0, 0.0, List.of(), 48.0)));  // granularite = 0
    }

    // =========================================================================
    // B4 — 2-swap actif quand equité est la règle primaire
    // =========================================================================

    @Test
    @DisplayName("B4 — 2-swap actif pour ['equite', 'anciennete'] (equité primaire)")
    void deux_swap_actif_quand_equite_primaire() {
        // 2 exigences, 2 employés. Avec equité primaire, le 2-swap doit équilibrer.
        Exigence ex1 = buildExigence("ex1", "Poste1", "caissier",
                SITE_A, List.of(0), 8.0, 12.0, 1);
        Exigence ex2 = buildExigence("ex2", "Poste2", "caissier",
                SITE_A, List.of(0), 12.0, 16.0, 1);
        Exigence ex3 = buildExigence("ex3", "Poste3", "caissier",
                SITE_A, List.of(1), 8.0, 12.0, 1);

        DisponibilitePlage dispoLundi = DisponibilitePlage.builder()
                .jour(0).heureDebut(8.0).heureFin(16.0).build();
        DisponibilitePlage dispoMardi = DisponibilitePlage.builder()
                .jour(1).heureDebut(8.0).heureFin(12.0).build();

        Employe emp1 = buildEmployeMultiDispo("e1", "Alice", "caissier", SITE_A,
                List.of(dispoLundi, dispoMardi));
        Employe emp2 = buildEmployeMultiDispo("e2", "Bob", "caissier", SITE_A,
                List.of(dispoLundi, dispoMardi));

        // Règle : equité en premier, ancienneté en tiebreaker
        ContexteAffectation ctx = buildContexte(
                List.of(ex1, ex2, ex3), List.of(emp1, emp2),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of("equite", "anciennete"), 48.0);

        SolverResult result = solver.resoudre(ctx);
        assertAllInvariantsHold(result, ctx);

        // Les 2 employés doivent être utilisés (equité distribue)
        long distinctEmployees = result.nouveauxCreneaux().stream()
                .map(CreneauAssigne::getEmployeId).distinct().count();
        assertThat(distinctEmployees)
                .as("Avec equité primaire + 2-swap actif, les 2 employés doivent être utilisés")
                .isEqualTo(2);
    }

    // =========================================================================
    // Pause fixe collective — exclusion solver
    // =========================================================================

    @Test
    @DisplayName("pause fixe 12h-13h : le solver génère 2 blocs séparés autour de la pause")
    void pause_fixe_genere_blocs_separes() {
        // Exigence 8h-17h, pause fixe 12h-13h le lundi
        Exigence exigence = buildExigence("ex1", "Journée", "caissier",
                SITE_A, List.of(0), 8.0, 17.0, 1);

        Employe alice = Employe.builder()
                .id("e1").nom("Alice").roles(List.of("caissier"))
                .siteIds(List.of(SITE_A))
                .disponibilites(List.of(
                        DisponibilitePlage.builder().jour(0).heureDebut(8.0).heureFin(17.0).build()))
                .build();

        // Contexte avec pause fixe 12h-13h le lundi (jour 0)
        java.util.Map<String, Employe> parId = java.util.Map.of("e1", alice);
        ContexteAffectation ctx = new ContexteAffectation(
                List.of(exigence), List.of(alice), parId,
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0, 10.0,
                0.0, 0.0, 0,
                12.0, 13.0, List.of(0),  // pauseFixe 12h-13h le lundi
                LUNDI, SEMAINE, SITE_A, ORG);

        SolverResult result = solver.resoudre(ctx);

        // Le solver doit produire 2 blocs : 8h-12h et 13h-17h
        assertThat(result.nouveauxCreneaux())
                .as("Pause fixe 12h-13h doit créer un gap — 2 blocs attendus")
                .hasSize(2);

        // Aucun créneau ne doit chevaucher 12h-13h
        for (var c : result.nouveauxCreneaux()) {
            boolean overlap = c.getHeureDebut() < 13.0 - 1e-9 && 12.0 < c.getHeureFin() - 1e-9;
            assertThat(overlap)
                    .as("Créneau [%.1f-%.1f] ne doit pas chevaucher la pause fixe 12h-13h",
                            c.getHeureDebut(), c.getHeureFin())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("pause fixe désactivée : le solver génère un seul bloc continu")
    void pause_fixe_desactivee_bloc_continu() {
        Exigence exigence = buildExigence("ex1", "Journée", "caissier",
                SITE_A, List.of(0), 8.0, 16.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 16.0);

        // Pas de pause fixe (null/null/empty)
        ContexteAffectation ctx = buildContexte(
                List.of(exigence), List.of(alice),
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0);

        SolverResult result = solver.resoudre(ctx);

        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getHeureDebut()).isEqualTo(8.0);
        assertThat(result.nouveauxCreneaux().get(0).getHeureFin()).isEqualTo(16.0);
    }

    @Test
    @DisplayName("pause fixe ne s'applique pas le jour non configuré")
    void pause_fixe_jours_non_configures() {
        // Pause fixe le mardi (jour 1) seulement, exigence le lundi (jour 0)
        Exigence exigence = buildExigence("ex1", "Lundi", "caissier",
                SITE_A, List.of(0), 8.0, 16.0, 1);

        Employe alice = buildEmploye("e1", "Alice", "caissier", SITE_A, 0, 8.0, 16.0);

        java.util.Map<String, Employe> parId = java.util.Map.of("e1", alice);
        ContexteAffectation ctx = new ContexteAffectation(
                List.of(exigence), List.of(alice), parId,
                List.of(), List.of(), List.of(),
                1.0, 1.0, List.of(), 48.0, 10.0,
                0.0, 0.0, 0,
                12.0, 13.0, List.of(1),  // pause fixe le MARDI seulement
                LUNDI, SEMAINE, SITE_A, ORG);

        SolverResult result = solver.resoudre(ctx);

        // Lundi n'est pas dans pauseFixeJours → bloc continu
        assertThat(result.nouveauxCreneaux()).hasSize(1);
        assertThat(result.nouveauxCreneaux().get(0).getHeureFin()).isEqualTo(16.0);
    }
}
