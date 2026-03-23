package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "site", indexes = {
    @Index(name = "idx_site_org", columnList = "organisationId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(nullable = false)
    private String nom;

    private String adresse;

    private String telephone;

    @Column(nullable = false)
    private String organisationId;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;
}
