package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pointage", indexes = {
    @Index(name = "idx_pointage_employe_org", columnList = "employeId, organisation_id"),
    @Index(name = "idx_pointage_horodatage", columnList = "horodatage"),
    @Index(name = "idx_pointage_site_org", columnList = "siteId, organisation_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Pointage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String employeId;

    @Column(nullable = false)
    private String type; // entree, sortie

    @Column(nullable = false)
    private LocalDateTime horodatage;

    @Column(nullable = false)
    private String methode; // pin, web, qr, manuel

    @Column(nullable = false)
    @Builder.Default
    private String statut = "valide"; // valide, anomalie, corrige

    private String anomalie;

    private String siteId;

    @Column(name = "organisation_id")
    private String organisationId;
}
