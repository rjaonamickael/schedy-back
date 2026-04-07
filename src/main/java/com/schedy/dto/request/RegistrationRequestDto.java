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
    @Size(max = 255, message = "Le nom de l'organisation ne peut pas depasser 255 caracteres.")
    String organisationName,

    @NotBlank(message = "Le nom du contact est obligatoire.")
    @Size(max = 255, message = "Le nom du contact ne peut pas depasser 255 caracteres.")
    String contactName,

    @NotBlank(message = "L'adresse email est obligatoire.")
    @Email(message = "L'adresse email n'est pas valide.")
    @Size(max = 255, message = "L'adresse email ne peut pas depasser 255 caracteres.")
    String contactEmail,

    /** Optional phone number — no format enforced at API level */
    @Size(max = 255, message = "Le numero de telephone ne peut pas depasser 255 caracteres.")
    String contactPhone,

    @NotBlank(message = "Le pays est obligatoire.")
    @Size(max = 50, message = "Le code pays ne peut pas depasser 50 caracteres.")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Le pays doit être un code ISO alpha-3 en majuscules (ex. CAN, MDG).")
    String pays,

    /** Province/state — mandatory when pays = CAN */
    @Size(max = 50, message = "La province ne peut pas depasser 50 caracteres.")
    String province,

    @Size(max = 255, message = "L'adresse ne peut pas depasser 255 caracteres.")
    String adresse,

    /** Canada: CRA Business Number (BN) */
    @Size(max = 255, message = "Le numero d'entreprise ne peut pas depasser 255 caracteres.")
    String businessNumber,

    /** Canada: provincial business identifier (NEQ / BIN / etc.) */
    @Size(max = 255, message = "L'identifiant provincial ne peut pas depasser 255 caracteres.")
    String provincialId,

    /** Madagascar: Numéro d'Identification Fiscale */
    @Size(max = 255, message = "Le NIF ne peut pas depasser 255 caracteres.")
    String nif,

    /** Madagascar: Numéro STAT */
    @Size(max = 255, message = "Le STAT ne peut pas depasser 255 caracteres.")
    String stat,

    @NotBlank(message = "Le plan souhaité est obligatoire.")
    @Size(max = 50, message = "Le plan ne peut pas depasser 50 caracteres.")
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
    @Size(max = 50, message = "Le cycle de facturation ne peut pas depasser 50 caracteres.")
    @Pattern(regexp = "^(ANNUAL|MONTHLY)$",
             message = "Le cycle de facturation doit être 'ANNUAL' ou 'MONTHLY'.")
    String billingCycle,

    /** Whether the applicant checked the legal certification checkbox */
    Boolean certificationAccepted

) {}
