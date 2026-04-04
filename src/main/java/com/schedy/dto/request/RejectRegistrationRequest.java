package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for the superadmin reject endpoint.
 */
public record RejectRegistrationRequest(

    @NotBlank(message = "Le motif de refus est obligatoire.")
    @Size(max = 2000, message = "Le motif de refus ne peut pas dépasser 2000 caractères.")
    String reason

) {}
