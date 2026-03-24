package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "pointage_code")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PointageCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String siteId;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String pin;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FrequenceRotation frequence;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime validFrom;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime validTo;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    @Column(name = "organisation_id")
    private String organisationId;

    public enum FrequenceRotation {
        QUOTIDIEN, HEBDOMADAIRE, BI_HEBDOMADAIRE, MENSUEL
    }

    public boolean isExpired() {
        return OffsetDateTime.now(ZoneOffset.UTC).isAfter(validTo);
    }

    public boolean isValid() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return actif && !now.isBefore(validFrom) && !now.isAfter(validTo);
    }
}
