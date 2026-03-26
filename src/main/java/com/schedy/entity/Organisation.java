package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.OffsetDateTime;

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

    @Column(length = 3)
    private String pays;

    // Added by V7 migration — platform-level metadata
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "status", length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(columnDefinition = "TEXT")
    private String notes;
}
