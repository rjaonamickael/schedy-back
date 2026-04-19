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

    // Added by V45 migration — admin self-service profile fields
    @Column(name = "legal_representative", length = 255)
    private String legalRepresentative;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "siret", length = 20)
    private String siret;

    // Added by V45 migration — Stripe customer reference (lazy-created)
    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    // Added by V48 migration — brand assets centralises. Source de verite reutilisee
    // par le snapshot Testimonial, et plus tard par signatures email/factures/kiosque.
    // Le logo passe par l'endpoint multipart /api/v1/organisation/me/logo (SVG
    // sanitise + stocke sur R2). website_url et linkedin_url sont editables via
    // PATCH /api/v1/organisation/me.
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    // Added by V51 migration — restauration des reseaux sociaux entreprise
    // (Facebook/Instagram/X) supprimes en V48. Demande utilisateur : ces
    // canaux restent utilises en B2B PME QC/MG.
    @Column(name = "facebook_url", length = 500)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 500)
    private String instagramUrl;

    @Column(name = "twitter_url", length = 500)
    private String twitterUrl;

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
