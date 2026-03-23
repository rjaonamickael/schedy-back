package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "organisation")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Organisation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String nom;

    private String domaine;

    private String adresse;

    private String telephone;
}
