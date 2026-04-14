package com.schedy.entity;

/**
 * Strategie de limitation appliquee a un type de conge.
 *
 * <ul>
 *   <li>{@link #ENVELOPPE_ANNUELLE} — enveloppe fixe renouvelee chaque annee (ex: 25j CP/an).
 *       Utilise le champ {@code quotaAnnuel} comme valeur par defaut des banques.</li>
 *   <li>{@link #ACCRUAL} — acquis progressivement selon {@code accrualMontant} et
 *       {@code accrualFrequence} (ex: Loi sur les normes du travail du Quebec).</li>
 *   <li>{@link #AUCUNE} — pas de limite stricte (ex: maladie avec justificatif, deuil).</li>
 * </ul>
 */
public enum TypeLimite {
    ENVELOPPE_ANNUELLE,
    ACCRUAL,
    AUCUNE
}
