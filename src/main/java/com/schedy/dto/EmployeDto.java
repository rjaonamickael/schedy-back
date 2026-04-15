package com.schedy.dto;

import com.schedy.entity.DisponibilitePlage;
import com.schedy.entity.Genre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 16 / Feature 2 : {@code role : String} has been replaced by
 * {@code roles : List<String>}. The list is ORDERED : index 0 is the role
 * principal (primary), index 1 is secondaire, etc. Display and scoring
 * reference this hierarchy explicitly.
 *
 * <p>V38 : adds {@code numeroEmploye} (alphanumeric HR number, unique per
 * organisation) and {@code genre} (enum HOMME / FEMME / AUTRE, used for
 * maternity-leave eligibility and reporting).
 */
public record EmployeDto(
    String id,
    @NotBlank @Size(max = 255) String nom,
    List<String> roles,
    @Size(max = 50) String telephone,
    @Size(max = 255) String email,
    LocalDate dateNaissance,
    LocalDate dateEmbauche,
    @Size(min = 4, max = 10) String pin,
    String organisationId,
    List<DisponibilitePlage> disponibilites,
    List<String> siteIds,
    /**
     * V38 : alphanumeric HR number (letters + digits only, no special chars).
     * Length 1-32 to cover matricules RH usuels. Nullable when the org does
     * not assign numbers to its employees.
     */
    @Size(max = 32)
    @Pattern(regexp = "^[A-Za-z0-9]*$", message = "Le numero d'employe doit contenir uniquement des lettres et chiffres")
    String numeroEmploye,
    /** V38 : HOMME / FEMME / AUTRE, nullable. */
    Genre genre
) {}
