package com.schedy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

/**
 * Embeddable break rule for tiered convention collective support.
 * Each rule defines ONE break entitlement for a shift duration range.
 * Multiple rules form a self-contained tier (e.g., 8h shift = 1 REPAS + 1 PAUSE).
 */
@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReglePause {

    /** Minimum shift duration (hours) for this rule to apply. */
    @Column(name = "seuil_min_heures")
    private double seuilMinHeures;

    @Column(name = "seuil_max_heures")
    private Double seuilMaxHeures;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private TypePause type;

    @Column(name = "duree_minutes")
    private int dureeMinutes;

    @Column(name = "payee")
    private boolean payee;

    @Column(name = "divisible")
    @Builder.Default
    private boolean divisible = false;

    @Column(name = "fraction_min_minutes")
    private Integer fractionMinMinutes;

    @Column(name = "fenetre_debut")
    private Double fenetreDebut;

    @Column(name = "fenetre_fin")
    private Double fenetreFin;

    @Column(name = "ordre")
    @Builder.Default
    private int ordre = 0;
}
