package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ApplyPromoRequest(
    @NotBlank String promoCode
) {}
