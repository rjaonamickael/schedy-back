package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "absence_imprevue", indexes = {
    @Index(name = "idx_absence_employe_org", columnList = "employe_id, organisation_id"),
    @Index(name = "idx_absence_statut_org", columnList = "statut, organisation_id"),
    @Index(name = "idx_absence_date_org", columnList = "date_absence, organisation_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AbsenceImprevue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    private Long version;

    @NotBlank
    @Column(name = "employe_id", nullable = false)
    private String employeId;

    @NotNull
    @Column(name = "date_absence", nullable = false)
    private LocalDate dateAbsence;

    @NotBlank
    @Column(nullable = false)
    private String motif;

    @Column(name = "message_employe", length = 500)
    private String messageEmploye;

    @Column(name = "signale_par", nullable = false)
    private String signalePar;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Initiateur initiateur;

    @Column(name = "date_signalement", nullable = false)
    private Instant dateSignalement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutAbsenceImprevue statut = StatutAbsenceImprevue.SIGNALEE;

    @Column(name = "valide_par_email")
    private String valideParEmail;

    @Column(name = "date_validation")
    private Instant dateValidation;

    @Column(name = "note_refus", length = 500)
    private String noteRefus;

    @Column(name = "note_manager", length = 500)
    private String noteManager;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "absence_creneau_impacte",
        joinColumns = @JoinColumn(name = "absence_id")
    )
    @Column(name = "creneau_id")
    @BatchSize(size = 50)
    @Builder.Default
    private List<String> creneauIds = new ArrayList<>();

    @Column(name = "site_id")
    private String siteId;

    @Column(name = "organisation_id", nullable = false)
    private String organisationId;

    public enum Initiateur {
        EMPLOYEE, MANAGER
    }
}
