package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypePointage type;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime horodatage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MethodePointage methode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutPointage statut = StatutPointage.valide;

    private String anomalie;

    private String siteId;

    @Column(name = "organisation_id")
    private String organisationId;
}
