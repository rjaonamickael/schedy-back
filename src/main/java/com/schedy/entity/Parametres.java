package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "parametres")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Parametres {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    private int heureDebut = 6;

    @Builder.Default
    private int heureFin = 22;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "parametres_jours_actifs", joinColumns = @JoinColumn(name = "parametres_id"))
    @Column(name = "jour")
    @BatchSize(size = 50)
    @Builder.Default
    private List<Integer> joursActifs = new ArrayList<>();

    @Builder.Default
    private int premierJour = 1;

    @Builder.Default
    private double dureeMinAffectation = 1.0;

    @Builder.Default
    @Column(columnDefinition = "double precision default 48.0")
    private Double heuresMaxSemaine = 48.0;

    @Column(unique = true)
    private String siteId;

    @Column(name = "organisation_id")
    private String organisationId;

    private String taillePolice;
    private String planningVue;

    @Builder.Default
    private double planningGranularite = 1.0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "parametres_regles_affectation", joinColumns = @JoinColumn(name = "parametres_id"))
    @Column(name = "regle")
    @BatchSize(size = 50)
    @Builder.Default
    private List<String> reglesAffectation = new ArrayList<>();

    @Builder.Default
    @Column(name = "seuil_absence_vs_conge_heures", columnDefinition = "integer default 48")
    private Integer seuilAbsenceVsCongeHeures = 48;

    @Builder.Default
    @Column(name = "delai_signalement_absence_minutes", columnDefinition = "integer default 60")
    private Integer delaiSignalementAbsenceMinutes = 60;
}
