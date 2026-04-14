package com.schedy.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Payload de creation/edition d'un type de conge.
 * {@code typeLimite} est une chaine ('ENVELOPPE_ANNUELLE' | 'ACCRUAL' | 'AUCUNE').
 * {@code accrualFrequence} est une chaine ('mensuel' | 'hebdomadaire' | 'annuel') ou null.
 */
public record TypeCongeDto(
    String id,
    @NotBlank String nom,
    boolean paye,
    @NotBlank String unite,
    String couleur,
    @NotBlank String typeLimite,
    Double quotaAnnuel,
    Double accrualMontant,
    String accrualFrequence,
    boolean autoriserDepassement,
    LocalDate dateDebutValidite,
    LocalDate dateFinValidite,
    String organisationId
) {}
