package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "role", indexes = {
    @Index(name = "idx_role_org", columnList = "organisation_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private int importance;

    private String couleur;

    @Column(length = 50)
    private String icone;

    @Column(name = "organisation_id")
    private String organisationId;
}
