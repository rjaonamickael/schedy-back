package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "jour_ferie", indexes = {
    @Index(name = "idx_jour_ferie_org", columnList = "organisation_id")
})
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

    private String siteId;

    @Column(name = "organisation_id")
    private String organisationId;
}
