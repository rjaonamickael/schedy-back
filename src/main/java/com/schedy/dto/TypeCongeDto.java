package com.schedy.dto;

import com.schedy.entity.Genre;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.Set;

/**
 * Payload de creation/edition d'un type de conge.
 * {@code typeLimite} est une chaine ('ENVELOPPE_ANNUELLE' | 'ACCRUAL' | 'AUCUNE').
 * {@code accrualFrequence} est une chaine ('mensuel' | 'hebdomadaire' | 'annuel') ou null.
 *
 * <p>V39 : {@code genresEligibles} est un filtre de genre optionnel.
 * {@code null} ou {@code empty} → type ouvert a tous les genres. Un set
 * peuple (par ex. {@code [FEMME]} pour un conge maternite) restreint la
 * provision de banques aux seuls employes dont le genre correspond.
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
    String organisationId,
    Set<Genre> genresEligibles
) {}
