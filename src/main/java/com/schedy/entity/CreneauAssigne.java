package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "creneau_assigne",
    indexes = {
        @Index(name = "idx_creneau_semaine_org", columnList = "semaine, organisation_id"),
        @Index(name = "idx_creneau_employe_org", columnList = "employeId, organisation_id")
    },
    uniqueConstraints = {
        // Empêche tout doublon exact (cf. migration V28). Un même employé ne peut
        // avoir deux créneaux avec les mêmes bornes horaires sur le même site dans
        // la même semaine — la réaffectation doit mettre à jour ou supprimer,
        // jamais dupliquer.
        @UniqueConstraint(
            name = "uk_creneau_assigne_employe_slot",
            columnNames = {"organisation_id", "employeId", "semaine", "jour", "siteId", "heureDebut", "heureFin"}
        )
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreneauAssigne {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String employeId;

    @Column(nullable = false)
    private int jour;

    @Column(nullable = false)
    private double heureDebut;

    @Column(nullable = false)
    private double heureFin;

    @Column(nullable = false)
    private String semaine;

    @Column(nullable = false)
    private String siteId;

    /**
     * Sprint 16 / Feature 2 : role filled by the employee on this specific creneau.
     *
     * <p>For multi-role employees (e.g. "cuisinier" primary + "plongeur" secondary),
     * this column records which role is being filled on a given day. Nullable :
     * legacy creneaux from before V33 have no role context and stay at null.</p>
     *
     * <p>Populated from the exigence that triggered an auto-affectation, or from
     * an explicit choice in the manual assignation modal.</p>
     */
    @Column(name = "role")
    private String role;

    @Column(name = "organisation_id")
    private String organisationId;
}
