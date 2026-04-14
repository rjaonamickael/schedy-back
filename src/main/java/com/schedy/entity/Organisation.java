package com.schedy.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "organisation")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Organisation {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

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

    // Added by V25 migration — organisation identification data
    @Column(name = "province", length = 10)
    private String province;

    @Column(name = "business_number", length = 50)
    private String businessNumber;

    @Column(name = "provincial_id", length = 50)
    private String provincialId;

    @Column(name = "nif", length = 50)
    private String nif;

    @Column(name = "stat", length = 50)
    private String stat;

    // Added by V26 migration — superadmin verification workflow
    @Column(name = "verification_status", length = 20, nullable = false)
    @Builder.Default
    private String verificationStatus = "UNVERIFIED";

    @Column(name = "verified_by", length = 255)
    private String verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "verification_note", columnDefinition = "TEXT")
    private String verificationNote;

    // Added by V7 migration — platform-level metadata
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "status", length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Date de renouvellement annuel des envelopes de conges (format MM-DD).
     * Default '01-01' = 1er janvier. Utilisee par RenouvellementCongesScheduler
     * pour reset les banques de type ENVELOPPE_ANNUELLE.
     */
    @Column(name = "date_renouvellement_conges", length = 5, nullable = false)
    @Builder.Default
    private String dateRenouvellementConges = "01-01";
}
