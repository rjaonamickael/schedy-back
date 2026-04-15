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

    // Convention 0-indexée alignée sur le frontend : 0=Lundi .. 6=Dimanche.
    @Builder.Default
    private int premierJour = 0;

    @Builder.Default
    private double dureeMinAffectation = 1.0;

    @Builder.Default
    @Column(columnDefinition = "double precision default 48.0")
    private Double heuresMaxSemaine = 48.0;

    @Builder.Default
    @Column(columnDefinition = "double precision default 10.0")
    private Double dureeMaxJour = 10.0;

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

    // ── Labor law constraints (country-agnostic, configured per org/site) ──
    // Preset values by market:
    //   QC: repos=0, hebdo=32, maxJours=6, maxSem=50, maxJour=12
    //   ON: repos=11, hebdo=24, maxJours=6, maxSem=48, maxJour=13
    //   BC: repos=8, hebdo=32, maxJours=6, maxSem=48, maxJour=12
    //   AB: repos=8, hebdo=24, maxJours=6, maxSem=44, maxJour=12
    //   MG: repos=12, hebdo=24, maxJours=6, maxSem=40, maxJour=8

    /** Minimum rest between two shifts in hours. ON: 11h, BC/AB: 8h, MG: 12h, QC: 0 (none). 0 = disabled. */
    @Builder.Default
    @Column(name = "repos_min_entre_shifts", columnDefinition = "double precision default 0")
    private Double reposMinEntreShifts = 0.0;

    /** Minimum weekly rest in hours. QC/BC: 32h, ON/AB/MG: 24h. 0 = disabled. */
    @Builder.Default
    @Column(name = "repos_hebdo_min", columnDefinition = "double precision default 0")
    private Double reposHebdoMin = 0.0;

    /** Maximum consecutive working days. All markets: 6. AB special: 24 then 4 off. 0 = disabled. */
    @Builder.Default
    @Column(name = "max_jours_consecutifs", columnDefinition = "integer default 0")
    private Integer maxJoursConsecutifs = 0;

    // ── Pause management (3 layers) ──

    // Layer 1: Fixed collective break (site-wide window)
    @Column(name = "pause_fixe_heure_debut")
    private Double pauseFixeHeureDebut;

    @Column(name = "pause_fixe_heure_fin")
    private Double pauseFixeHeureFin;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "parametres_pause_fixe_jours", joinColumns = @JoinColumn(name = "parametres_id"))
    @Column(name = "jour")
    @BatchSize(size = 50)
    @Builder.Default
    private List<Integer> pauseFixeJours = new ArrayList<>();

    // Layer 2: Tiered break rules
    @Builder.Default
    @Column(name = "pause_avancee", columnDefinition = "boolean default false")
    private Boolean pauseAvancee = false;

    // Simple mode (pauseAvancee = false)
    @Builder.Default
    @Column(name = "pause_seuil_heures", columnDefinition = "double precision default 0")
    private Double pauseSeuilHeures = 0.0;

    @Builder.Default
    @Column(name = "pause_duree_minutes", columnDefinition = "integer default 0")
    private Integer pauseDureeMinutes = 0;

    @Builder.Default
    @Column(name = "pause_payee", columnDefinition = "boolean default false")
    private Boolean pausePayee = false;

    // Advanced mode (pauseAvancee = true): tiered rules
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "parametres_regles_pause", joinColumns = @JoinColumn(name = "parametres_id"))
    @BatchSize(size = 50)
    @Builder.Default
    private List<ReglePause> reglesPause = new ArrayList<>();

    // Layer 3: Detection window
    @Builder.Default
    @Column(name = "fenetre_pause_min_minutes", columnDefinition = "integer default 15")
    private Integer fenetrePauseMinMinutes = 15;

    @Builder.Default
    @Column(name = "fenetre_pause_max_minutes", columnDefinition = "integer default 90")
    private Integer fenetrePauseMaxMinutes = 90;

    @Builder.Default
    @Column(name = "pause_renoncement_autorise", columnDefinition = "boolean default false")
    private Boolean pauseRenoncementAutorise = false;

    // ── Absence/alerting thresholds ──

    @Builder.Default
    @Column(name = "seuil_absence_vs_conge_heures", columnDefinition = "integer default 48")
    private Integer seuilAbsenceVsCongeHeures = 48;

    @Builder.Default
    @Column(name = "delai_signalement_absence_minutes", columnDefinition = "integer default 60")
    private Integer delaiSignalementAbsenceMinutes = 60;

    // ── Clock-in security (kiosk creneau guard) ─────────────────────
    // An employee is allowed to clock in only when they have an active
    // creneau on the site. These tolerance windows extend the creneau
    // boundaries so employees arriving slightly early / leaving slightly
    // late still succeed. 0 = no tolerance (exact boundaries).

    /** Minutes before heureDebut during which the employee may clock in. */
    @Builder.Default
    @Column(name = "tolerance_avant_shift_minutes", columnDefinition = "integer default 30")
    private Integer toleranceAvantShiftMinutes = 30;

    /** Minutes after heureFin during which the employee may still clock out. */
    @Builder.Default
    @Column(name = "tolerance_apres_shift_minutes", columnDefinition = "integer default 30")
    private Integer toleranceApresShiftMinutes = 30;
}
