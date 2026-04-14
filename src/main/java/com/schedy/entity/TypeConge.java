package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "type_conge", indexes = {
    @Index(name = "idx_type_conge_org", columnList = "organisation_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TypeConge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(nullable = false)
    private String nom;

    /** Est-ce que le salarie est remunere pendant ce type d'absence ? */
    @Column(nullable = false)
    @Builder.Default
    private boolean paye = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UniteConge unite;

    /** Strategie de limite. Voir {@link TypeLimite}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_limite", nullable = false, length = 32)
    @Builder.Default
    private TypeLimite typeLimite = TypeLimite.ENVELOPPE_ANNUELLE;

    /**
     * Quota par defaut pour les nouvelles banques de ce type.
     * Pertinent uniquement si {@code typeLimite == ENVELOPPE_ANNUELLE}.
     * {@code null} sinon.
     */
    @Column(name = "quota_annuel")
    private Double quotaAnnuel;

    /**
     * Montant credite a chaque tick pour les types ACCRUAL.
     * {@code null} pour ENVELOPPE_ANNUELLE et AUCUNE.
     */
    @Column(name = "accrual_montant")
    private Double accrualMontant;

    /**
     * Frequence de credit pour les types ACCRUAL (mensuel / hebdomadaire / annuel).
     * {@code null} pour ENVELOPPE_ANNUELLE et AUCUNE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "accrual_frequence")
    private FrequenceAccrual accrualFrequence;

    /**
     * Autorise la prise de conge au-dela du quota (solde negatif temporaire).
     * Pertinent uniquement si {@code typeLimite == ENVELOPPE_ANNUELLE}.
     */
    @Column(name = "autoriser_depassement", nullable = false)
    @Builder.Default
    private boolean autoriserDepassement = false;

    /** Date a partir de laquelle ce type devient actif. {@code null} = actif immediatement. */
    @Column(name = "date_debut_validite")
    private LocalDate dateDebutValidite;

    /** Date apres laquelle ce type n'est plus actif. {@code null} = sans fin. */
    @Column(name = "date_fin_validite")
    private LocalDate dateFinValidite;

    @Column(name = "organisation_id")
    private String organisationId;

    private String couleur;
}
