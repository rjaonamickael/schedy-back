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

    /**
     * Sprint 16 / Feature 2 : multi-role with explicit hierarchy.
     *
     * <p>{@code roles[0]} is the <b>role principal</b> (primary) used for :
     * <ul>
     *   <li>display in lists, planning grids and avatars</li>
     *   <li>full scoring weight in {@code ReplacementService}</li>
     *   <li>default role when assigning a creneau that doesn't specify one</li>
     * </ul>
     *
     * <p>{@code roles[1..n]} are <b>roles secondaires</b> with decaying score
     * weight (see {@code ReplacementService.scoreRoleMatchByPosition}). The
     * hierarchy is explicit : a "plongeur" listed as secondary scores less
     * than one listed as primary, even if both employees are eligible.
     *
     * <p>EAGER + @BatchSize follows the project rule on @ElementCollection
     * (see CLAUDE.md : never LAZY on collections, serialisation happens
     * after the session closes).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "employe_roles", joinColumns = @JoinColumn(name = "employe_id"))
    @OrderColumn(name = "role_ordre")
    @Column(name = "role")
    @BatchSize(size = 50)
    @Builder.Default
    private List<String> roles = new ArrayList<>();

    /**
     * Sprint 16 : returns the primary (index 0) role, or {@code null} if the
     * employee has no role at all. Used for display in lists, planning grids,
     * and any legacy call site that expects a single role string.
     */
    @Transient
    @JsonIgnore
    public String getPrimaryRole() {
        return roles == null || roles.isEmpty() ? null : roles.get(0);
    }

    /**
     * Sprint 16 : returns the 0-based position of the given role in the
     * hierarchy, or {@code -1} if the employee does not hold that role at all.
     *
     * <p>Used by {@code ReplacementService} to weight the role-match score :
     * index 0 (primary) gets full weight, index 1 (secondary) gets a decay
     * penalty, index 2+ decays further. The hierarchy is explicit so a
     * "cuisinier" listed as primary scores higher than one listed as secondary.</p>
     */
    @Transient
    @JsonIgnore
    public int getRolePosition(String role) {
        if (role == null || roles == null) return -1;
        return roles.indexOf(role);
    }

    /**
     * Sprint 16 : convenience check for "does this employee hold the given role
     * at all, regardless of position in the hierarchy ?".
     */
    @Transient
    @JsonIgnore
    public boolean hasRole(String role) {
        return role != null && roles != null && roles.contains(role);
    }

    private String telephone;

    private String email;

    private LocalDate dateNaissance;

    private LocalDate dateEmbauche;

    /**
     * V38 : Alphanumeric employee number issued by the organisation.
     * Free-form string (letters + digits only, no special chars) constrained
     * at the DTO / service level via {@code @Pattern}. Uniqueness is enforced
     * per-organisation through {@code idx_employe_num_org_unique} so two
     * organisations can reuse the same number without conflict. Nullable :
     * legacy rows pre-V38 and employees without a HR number stay {@code null}.
     */
    @Column(name = "numero_employe")
    private String numeroEmploye;

    /**
     * V38 : Employee gender for maternity-leave eligibility and reporting.
     * See {@link Genre} — nullable on purpose (legacy rows + employees who
     * decline to answer both surface as {@code null}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "genre")
    private Genre genre;

    @JsonIgnore
    private String pin;         // BCrypt hash for verification

    @JsonIgnore
    private String pinHash;     // SHA-256 hash for O(1) lookup

    @JsonIgnore
    private String pinClair;    // AES-256-GCM encrypted PIN — decrypted on demand via /employes/{id}/pin

    @JsonIgnore
    @Column(name = "pin_clair_encrypted", nullable = false)
    @Builder.Default
    private boolean pinClairEncrypted = false; // TRUE = post-V10 encrypted blob; FALSE = pre-migration plaintext

    /**
     * V36: UTC timestamp of the most recent PIN write (creation or regeneration).
     * Printed on the kiosk card so admins can match a physical card against the
     * live PIN. NULL for legacy rows pre-V36; backfilled on next regeneration.
     */
    @Column(name = "pin_generated_at")
    private java.time.OffsetDateTime pinGeneratedAt;

    /**
     * V36: monotonic counter incremented on every PIN regeneration. Printed on
     * the kiosk card alongside pinGeneratedAt so admins can quickly tell when a
     * physical card is stale and needs reprinting.
     */
    @Column(name = "pin_version", nullable = false)
    @Builder.Default
    private Integer pinVersion = 1;

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
