package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

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

    /**
     * V39 : gender-based eligibility filter. When the set is empty or null,
     * the type is open to every employee regardless of gender (the default).
     * When the set contains one or more {@link Genre} values, the
     * provisioning logic in {@code CongeService} will only create
     * {@code BanqueConge} rows for employees whose {@code genre} matches.
     *
     * <p>Use cases :
     * <ul>
     *   <li>Congé maternité → {@code {FEMME}}</li>
     *   <li>Congé paternité → {@code {HOMME, AUTRE}} (or {@code {HOMME}} if strict)</li>
     *   <li>Tous les autres congés → empty set (open to all)</li>
     * </ul>
     *
     * <p>Employees with {@code genre = null} are <b>excluded</b> from any
     * restricted type — the admin is expected to set their genre explicitly
     * before the type applies to them. This is safer than the alternative
     * (silently granting maternity leave to a profile we can't verify).
     *
     * <p>EAGER + BatchSize follows the project rule on @ElementCollection
     * (see CLAUDE.md).
     */
    @ElementCollection(fetch = FetchType.EAGER, targetClass = Genre.class)
    @CollectionTable(
        name = "type_conge_genres_eligibles",
        joinColumns = @JoinColumn(name = "type_conge_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "genre", nullable = false, length = 16)
    @BatchSize(size = 50)
    @Builder.Default
    private Set<Genre> genresEligibles = new HashSet<>();

    /**
     * Returns {@code true} when this type can be assigned to an employee
     * whose gender is {@code empGenre}. A type with no {@code genresEligibles}
     * is open to all ; a restricted type rejects employees with a null or
     * non-matching gender.
     */
    @Transient
    public boolean isEligibleFor(Genre empGenre) {
        if (genresEligibles == null || genresEligibles.isEmpty()) return true;
        return empGenre != null && genresEligibles.contains(empGenre);
    }
}
