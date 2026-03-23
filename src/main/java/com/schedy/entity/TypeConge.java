package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

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

    @Column(nullable = false)
    private String categorie; // paye, non_paye

    @Column(nullable = false)
    private String unite; // heures, jours

    @Column(name = "organisation_id")
    private String organisationId;

    private String couleur;

    private String modeQuota;

    @Builder.Default
    private boolean quotaIllimite = false;

    @Builder.Default
    private boolean autoriserNegatif = false;

    private Double accrualMontant;
    private String accrualFrequence; // mensuel, hebdomadaire, annuel
    private Double reportMax;
    private Integer reportDuree; // en mois
}
