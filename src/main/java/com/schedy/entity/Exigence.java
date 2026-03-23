package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exigence")
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
}
