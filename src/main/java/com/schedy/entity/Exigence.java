package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exigence", indexes = {
    @Index(name = "idx_exigence_org", columnList = "organisation_id"),
    @Index(name = "idx_exigence_site", columnList = "siteId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Exigence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(nullable = false)
    private String libelle;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exigence_jours", joinColumns = @JoinColumn(name = "exigence_id"))
    @Column(name = "jour")
    @BatchSize(size = 50)
    @Builder.Default
    private List<Integer> jours = new ArrayList<>();

    @Column(nullable = false)
    private double heureDebut;

    @Column(nullable = false)
    private double heureFin;

    private String role;

    @Column(nullable = false)
    private int nombreRequis;

    @Column(nullable = false)
    private String siteId;

    @Column(name = "organisation_id")
    private String organisationId;

    /**
     * Sprint 16 / Feature 1 : variable staffing needs by period.
     *
     * <p>Optional start/end dates restrict this exigence to a specific period
     * (e.g. "from 2026-12-15 to 2026-01-05, need 2 extra cooks").</p>
     *
     * <ul>
     *   <li>{@code dateDebut} null means the exigence applies year-round.</li>
     *   <li>{@code dateDebut} and {@code dateFin} both set means the exigence
     *       applies only when the week's Monday falls inside the range.</li>
     *   <li>When two exigences cover the same {@code (role, siteId, jour, heure)}
     *       tuple, the one with the higher {@link #priorite} wins. Base exigences
     *       stay at 0 by default; period overrides use positive integers.</li>
     * </ul>
     */
    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "priorite", nullable = false)
    @Builder.Default
    private int priorite = 0;
}
