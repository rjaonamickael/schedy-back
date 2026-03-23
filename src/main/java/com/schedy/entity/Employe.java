package com.schedy.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "employe", indexes = {
    @Index(name = "idx_employe_org", columnList = "organisationId"),
    @Index(name = "idx_employe_pin_hash", columnList = "pinHash, organisationId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Employe {

    @Id
    private String id;

    @PrePersist
    void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @NotBlank
    @Column(nullable = false)
    private String nom;

    private String role;

    private String telephone;

    private String email;

    private LocalDate dateNaissance;

    private LocalDate dateEmbauche;

    @JsonIgnore
    private String pin;

    @JsonIgnore
    private String pinHash;

    private String organisationId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "employe_disponibilites", joinColumns = @JoinColumn(name = "employe_id"))
    @BatchSize(size = 50)
    @Builder.Default
    private List<DisponibilitePlage> disponibilites = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "employe_sites", joinColumns = @JoinColumn(name = "employe_id"))
    @Column(name = "site_id")
    @BatchSize(size = 50)
    @Builder.Default
    private List<String> siteIds = new ArrayList<>();
}
