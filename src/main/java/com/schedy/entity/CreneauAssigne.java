package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "creneau_assigne", indexes = {
    @Index(name = "idx_creneau_semaine_org", columnList = "semaine, organisation_id"),
    @Index(name = "idx_creneau_employe_org", columnList = "employeId, organisation_id")
})
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

    @Column(name = "organisation_id")
    private String organisationId;
}
