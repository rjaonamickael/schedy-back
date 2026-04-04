package com.schedy.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO for a public registration request.
 * Submitted anonymously by a prospective organisation admin.
 */
public record RegistrationRequestDto(

    @NotBlank(message = "Le nom de l'organisation est obligatoire.")
    String organisationName,

    @NotBlank(message = "Le nom du contact est obligatoire.")
    String contactName,

    @NotBlank(message = "L'adresse email est obligatoire.")
    @Email(message = "L'adresse email n'est pas valide.")
    String contactEmail,

    /** Optional phone number — no format enforced at API level */
    String contactPhone,

    @NotBlank(message = "Le pays est obligatoire.")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Le pays doit être un code ISO alpha-3 en majuscules (ex. CAN, MDG).")
    String pays,

    /** Province/state — mandatory when pays = CAN */
    String province,

    String adresse,

    /** Canada: CRA Business Number (BN) */
    String businessNumber,

    /** Canada: provincial business identifier (NEQ / BIN / etc.) */
    String provincialId,

    /** Madagascar: Numéro d'Identification Fiscale */
    String nif,

    /** Madagascar: Numéro STAT */
    String stat,

    @NotBlank(message = "Le plan souhaité est obligatoire.")
    @Pattern(regexp = "^(FREE|STARTER|PRO|CUSTOM)$", message = "Le plan doit être l'une des valeurs suivantes : FREE, STARTER, PRO, CUSTOM.")
    String desiredPlan,

    /** Estimated number of employees — informational only */
    Integer employeeCount,

    /** Free-text message from the applicant */
    @Size(max = 2000, message = "Le message ne peut pas dépasser 2000 caractères.")
    String message,

    /**
     * Billing preference chosen at sign-up.
     * Accepted values: "ANNUAL", "MONTHLY".
     * Nullable — existing integrations that omit this field remain valid.
     */
    @Pattern(regexp = "^(ANNUAL|MONTHLY)$",
             message = "Le cycle de facturation doit être 'ANNUAL' ou 'MONTHLY'.")
    String billingCycle

) {}
