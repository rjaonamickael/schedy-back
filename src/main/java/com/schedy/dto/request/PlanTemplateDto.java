package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request body for creating or updating a plan template.
 *
 * @param code          Unique identifier string (e.g. FREE, PRO, CUSTOM).
 * @param displayName   Human-readable plan name shown in the UI.
 * @param description   Optional marketing description (max 500 chars).
 * @param maxEmployees  Hard cap on employees; -1 means unlimited.
 * @param maxSites      Hard cap on sites; -1 means unlimited.
 * @param priceMonthly  Per-user monthly price in USD; null for free plans.
 * @param priceAnnual   Per-user annual price in USD; null for free plans.
 * @param trialDays     Number of trial days granted on signup (0 = no trial).
 * @param active        Whether this plan is visible and selectable.
 * @param sortOrder     Display order in plan listings (ascending).
 * @param features      Map of feature keys (e.g. PLANNING) to enabled boolean.
 */
public record PlanTemplateDto(

    @NotBlank
    String code,

    @NotBlank
    @Size(max = 100)
    String displayName,

    @Size(max = 500)
    String description,

    int maxEmployees,

    int maxSites,

    BigDecimal priceMonthly,

    BigDecimal priceAnnual,

    int trialDays,

    boolean active,

    int sortOrder,

    Map<String, Boolean> features

) {}
