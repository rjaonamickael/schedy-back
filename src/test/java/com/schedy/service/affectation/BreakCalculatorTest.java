package com.schedy.service.affectation;

import com.schedy.entity.Parametres;
import com.schedy.entity.ReglePause;
import com.schedy.entity.TypePause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BreakCalculator}.
 * Pure static utility — no Spring context needed.
 */
@DisplayName("BreakCalculator — calcul des déductions pause")
class BreakCalculatorTest {

    // =========================================================================
    // Simple mode
    // =========================================================================

    @Test
    @DisplayName("simple: shift sous le seuil → aucune déduction")
    void simple_sous_seuil_aucune_deduction() {
        var params = buildSimple(5.0, 30, false);
        var result = BreakCalculator.computeDeduction(4.5, params);
        assertThat(result.totalMinutes()).isZero();
        assertThat(result.unpaidMinutes()).isZero();
        assertThat(result.paidMinutes()).isZero();
    }

    @Test
    @DisplayName("simple: shift >= seuil, pause non payée → déduction unpaid")
    void simple_seuil_atteint_non_payee() {
        var params = buildSimple(5.0, 30, false);
        var result = BreakCalculator.computeDeduction(6.0, params);
        assertThat(result.totalMinutes()).isEqualTo(30);
        assertThat(result.unpaidMinutes()).isEqualTo(30);
        assertThat(result.paidMinutes()).isZero();
        assertThat(result.netPayableHours(6.0)).isEqualTo(5.5);
    }

    @Test
    @DisplayName("simple: shift >= seuil, pause payée → pas de déduction unpaid")
    void simple_seuil_atteint_payee() {
        var params = buildSimple(5.0, 30, true);
        var result = BreakCalculator.computeDeduction(6.0, params);
        assertThat(result.totalMinutes()).isEqualTo(30);
        assertThat(result.unpaidMinutes()).isZero();
        assertThat(result.paidMinutes()).isEqualTo(30);
        assertThat(result.netPayableHours(6.0)).isEqualTo(6.0); // paid break = no deduction
    }

    @Test
    @DisplayName("simple: seuil = 0 (désactivé) → aucune déduction")
    void simple_desactive() {
        var params = buildSimple(0.0, 30, false);
        var result = BreakCalculator.computeDeduction(8.0, params);
        assertThat(result.totalMinutes()).isZero();
    }

    @Test
    @DisplayName("simple: shift exactement au seuil → déduction appliquée")
    void simple_exactement_au_seuil() {
        var params = buildSimple(5.0, 30, false);
        var result = BreakCalculator.computeDeduction(5.0, params);
        assertThat(result.totalMinutes()).isEqualTo(30);
    }

    // =========================================================================
    // Advanced mode (tiered)
    // =========================================================================

    @Test
    @DisplayName("avancé: shift 6h avec palier 4h-8h → 1 pause payée 30min")
    void avance_palier_milieu() {
        var params = buildSAQ();
        var result = BreakCalculator.computeDeduction(6.0, params);
        assertThat(result.totalMinutes()).isEqualTo(30);
        assertThat(result.paidMinutes()).isEqualTo(30);
        assertThat(result.unpaidMinutes()).isZero();
    }

    @Test
    @DisplayName("avancé: shift 8h avec palier SAQ → 1 repas 60min non-payé + 1 pause 30min payée")
    void avance_saq_8h() {
        var params = buildSAQ();
        var result = BreakCalculator.computeDeduction(8.0, params);
        assertThat(result.totalMinutes()).isEqualTo(90); // 60 + 30
        assertThat(result.unpaidMinutes()).isEqualTo(60); // repas non-payé
        assertThat(result.paidMinutes()).isEqualTo(30);   // pause payée
        assertThat(result.netPayableHours(8.0)).isEqualTo(7.0); // 8h - 60min unpaid
    }

    @Test
    @DisplayName("avancé: shift 10h avec palier SAQ → 2 repas + 2 pauses")
    void avance_saq_10h() {
        var params = buildSAQ();
        var result = BreakCalculator.computeDeduction(10.0, params);
        assertThat(result.totalMinutes()).isEqualTo(180); // 60+30+60+30
        assertThat(result.unpaidMinutes()).isEqualTo(120); // 2 repas non-payés
        assertThat(result.paidMinutes()).isEqualTo(60);    // 2 pauses payées
        assertThat(result.netPayableHours(10.0)).isEqualTo(8.0); // 10h - 120min unpaid
    }

    @Test
    @DisplayName("avancé: shift 3h sous tous les seuils → aucune déduction")
    void avance_sous_tous_seuils() {
        var params = buildSAQ();
        var result = BreakCalculator.computeDeduction(3.0, params);
        assertThat(result.totalMinutes()).isZero();
    }

    @Test
    @DisplayName("avancé: shift 9.5h dans le palier 8h-10h (pas le palier 10h+)")
    void avance_entre_paliers() {
        var params = buildSAQ();
        var result = BreakCalculator.computeDeduction(9.5, params);
        // Le palier 8h-9.99h s'applique, pas le 10h+
        assertThat(result.totalMinutes()).isEqualTo(90); // 60 + 30
    }

    @Test
    @DisplayName("avancé: règles vides → aucune déduction")
    void avance_regles_vides() {
        var params = Parametres.builder().pauseAvancee(true).reglesPause(new ArrayList<>()).build();
        var result = BreakCalculator.computeDeduction(8.0, params);
        assertThat(result.totalMinutes()).isZero();
    }

    @Test
    @DisplayName("avancé: paliers auto-contenus (pas d'héritage)")
    void avance_auto_contenus_pas_heritage() {
        // Palier 4h: 1 pause 15min payée. Palier 8h: 1 repas 30min non-payé.
        // Un shift de 8h ne doit PAS hériter du palier 4h.
        var params = Parametres.builder()
                .pauseAvancee(true)
                .reglesPause(List.of(
                        ReglePause.builder().seuilMinHeures(4.0).seuilMaxHeures(8.0)
                                .type(TypePause.PAUSE).dureeMinutes(15).payee(true).ordre(0).build(),
                        ReglePause.builder().seuilMinHeures(8.0).seuilMaxHeures(null)
                                .type(TypePause.REPAS).dureeMinutes(30).payee(false).ordre(0).build()
                ))
                .build();
        var result = BreakCalculator.computeDeduction(8.0, params);
        // Seul le palier 8h s'applique (repas 30min non-payé), PAS le palier 4h (pause 15min)
        assertThat(result.totalMinutes()).isEqualTo(30);
        assertThat(result.unpaidMinutes()).isEqualTo(30);
        assertThat(result.paidMinutes()).isZero();
    }

    // =========================================================================
    // BreakDeduction.netPayableHours
    // =========================================================================

    @Test
    @DisplayName("netPayableHours: NONE → brut = net")
    void none_brut_egal_net() {
        assertThat(BreakCalculator.BreakDeduction.NONE.netPayableHours(8.0)).isEqualTo(8.0);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Parametres buildSimple(double seuil, int duree, boolean payee) {
        return Parametres.builder()
                .pauseAvancee(false)
                .pauseSeuilHeures(seuil)
                .pauseDureeMinutes(duree)
                .pausePayee(payee)
                .build();
    }

    /** SAQ-like tiered break structure. */
    private Parametres buildSAQ() {
        return Parametres.builder()
                .pauseAvancee(true)
                .reglesPause(List.of(
                        // Palier 4h-7.99h: 1 pause payée 30min
                        ReglePause.builder().seuilMinHeures(4.0).seuilMaxHeures(8.0)
                                .type(TypePause.PAUSE).dureeMinutes(30).payee(true).ordre(0).build(),
                        // Palier 8h-9.99h: 1 repas non-payé 60min + 1 pause payée 30min
                        ReglePause.builder().seuilMinHeures(8.0).seuilMaxHeures(10.0)
                                .type(TypePause.REPAS).dureeMinutes(60).payee(false).ordre(0).build(),
                        ReglePause.builder().seuilMinHeures(8.0).seuilMaxHeures(10.0)
                                .type(TypePause.PAUSE).dureeMinutes(30).payee(true).ordre(1).build(),
                        // Palier 10h+: 2 repas non-payés + 2 pauses payées
                        ReglePause.builder().seuilMinHeures(10.0).seuilMaxHeures(null)
                                .type(TypePause.REPAS).dureeMinutes(60).payee(false).ordre(0).build(),
                        ReglePause.builder().seuilMinHeures(10.0).seuilMaxHeures(null)
                                .type(TypePause.PAUSE).dureeMinutes(30).payee(true).ordre(1).build(),
                        ReglePause.builder().seuilMinHeures(10.0).seuilMaxHeures(null)
                                .type(TypePause.REPAS).dureeMinutes(60).payee(false).ordre(2).build(),
                        ReglePause.builder().seuilMinHeures(10.0).seuilMaxHeures(null)
                                .type(TypePause.PAUSE).dureeMinutes(30).payee(true).ordre(3).build()
                ))
                .build();
    }
}
