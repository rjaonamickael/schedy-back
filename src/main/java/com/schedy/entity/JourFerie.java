package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "jour_ferie")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JourFerie {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(nullable = false)
    private String nom;

    @NotNull
    @Column(nullable = false)
    private LocalDate date;

    @Builder.Default
    private boolean recurrent = false;

    @Column(name = "organisation_id")
    private String organisationId;
}
