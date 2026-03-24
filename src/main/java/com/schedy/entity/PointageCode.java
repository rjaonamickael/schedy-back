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

    /**
     * Raw PIN — only visible to authenticated admin/managers via PointageCodeDto.
     * Never exposed on public endpoints (KioskPointageCodeResponse excludes it).
     */
    @Column(nullable = false)
    private String pin;

    /**
     * SHA-256 hash of the PIN for O(1) indexed lookup.
     * Used by validateAndResolve() to find codes by PIN without exposing the raw value in queries.
     */
    private String pinHash;

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
