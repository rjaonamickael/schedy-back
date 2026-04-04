package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Tracks an employee break/pause occurrence — either detected from
 * clock-in/out gaps, deduced from break rules, or manually entered.
 *
 * <p>Separate from Pointage to preserve audit trail purity (Pointage = physical
 * clock events only). Pause = semantic interpretation of those events.
 */
@Entity
@Table(name = "pause", indexes = {
        @Index(name = "idx_pause_employe_org", columnList = "employe_id, organisation_id"),
        @Index(name = "idx_pause_debut", columnList = "debut"),
        @Index(name = "idx_pause_site", columnList = "site_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Pause {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    private Long version;

    @Column(name = "employe_id", nullable = false)
    private String employeId;

    @Column(name = "site_id")
    private String siteId;

    @Column(name = "organisation_id", nullable = false)
    private String organisationId;

    // ── When the break happened ──────────────────────────────────

    @Column(name = "debut", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime debut;

    @Column(name = "fin", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime fin;

    @Column(name = "duree_minutes")
    private Integer dureeMinutes;

    // ── Classification ───────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TypePause type;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private SourcePause source;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    @Builder.Default
    private StatutPause statut = StatutPause.DETECTE;

    @Column(name = "payee")
    private boolean payee;

    // ── Traceability ─────────────────────────────────────────────

    @Column(name = "pointage_sortie_id")
    private String pointageSortieId;

    @Column(name = "pointage_entree_id")
    private String pointageEntreeId;

    // ── Manager confirmation/contestation ─────────────────────────

    @Column(name = "confirme_par_id")
    private String confirmeParId;

    @Column(name = "confirme_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime confirmeAt;

    @Column(name = "motif_contestation")
    private String motifContestation;
}
