package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "demande_conge", indexes = {
    @Index(name = "idx_demande_employe_org", columnList = "employeId, organisation_id"),
    @Index(name = "idx_demande_statut", columnList = "statut")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DemandeConge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String employeId;

    @Column(nullable = false)
    private String typeCongeId;

    @Column(nullable = false)
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin;

    private Double heureDebut;

    private Double heureFin;

    @Column(nullable = false)
    private double duree;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutDemande statut = StatutDemande.en_attente;

    private String motif;

    private String noteApprobation;

    @Column(name = "organisation_id")
    private String organisationId;
}
