package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @Column(nullable = false)
    private LocalDateTime validFrom;

    @Column(nullable = false)
    private LocalDateTime validTo;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    @Column(name = "organisation_id")
    private String organisationId;

    public enum FrequenceRotation {
        QUOTIDIEN, HEBDOMADAIRE, BI_HEBDOMADAIRE, MENSUEL
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(validTo);
    }

    public boolean isValid() {
        LocalDateTime now = LocalDateTime.now();
        return actif && !now.isBefore(validFrom) && !now.isAfter(validTo);
    }
}
