package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateCheckoutSessionRequest(
        @NotBlank String planTemplateCode,
        @NotBlank @Pattern(regexp = "^(MONTHLY|ANNUAL)$",
                message = "billingInterval must be MONTHLY or ANNUAL")
        String billingInterval
) {}
