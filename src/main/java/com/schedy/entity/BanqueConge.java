package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "banque_conge", indexes = {
    @Index(name = "idx_banque_employe_type", columnList = "employeId, typeCongeId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BanqueConge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String employeId;

    @Column(nullable = false)
    private String typeCongeId;

    private Double quota;

    @Builder.Default
    private double utilise = 0;

    @Builder.Default
    private double enAttente = 0;

    private LocalDate dateDebut;

    private LocalDate dateFin;

    @Column(name = "organisation_id")
    private String organisationId;
}
