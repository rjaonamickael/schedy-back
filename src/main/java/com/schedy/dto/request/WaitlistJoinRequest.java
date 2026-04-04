package com.schedy.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO for the public PRO waitlist sign-up endpoint.
 * Submitted anonymously — no authentication required.
 */
public record WaitlistJoinRequest(

    @NotBlank(message = "L'adresse email est obligatoire.")
    @Email(message = "L'adresse email n'est pas valide.")
    @Size(max = 255, message = "L'adresse email ne peut pas depasser 255 caracteres.")
    String email,

    /**
     * UI language at the time of sign-up.
     * Expected values: "fr", "en". Not validated strictly — stored as-is.
     */
    @Size(max = 5, message = "Le code langue ne peut pas depasser 5 caracteres.")
    String language,

    /**
     * CTA origin that triggered the sign-up.
     * Examples: "planb_cta", "pricing_pro", "landing_hero".
     */
    @Size(max = 50, message = "La source ne peut pas depasser 50 caracteres.")
    String source

) {}
