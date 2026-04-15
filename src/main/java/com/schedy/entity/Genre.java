package com.schedy.entity;

/**
 * Employee gender — 3 explicit values with no pre-supposed semantic ordering.
 *
 * <p>Primary use : deciding eligibility for maternity leave accruals
 * ({@code FEMME}) and reporting per-gender demographics. Kept explicit
 * rather than boolean so {@code AUTRE} is first-class and never loses
 * information on round-trips.
 *
 * <p>Persistence : stored as {@code VARCHAR} via {@code @Enumerated(EnumType.STRING)}
 * to stay stable across enum reorderings. Nullable : legacy rows and
 * employees who decline to answer both surface as {@code null}.
 */
public enum Genre {
    HOMME,
    FEMME,
    AUTRE
}
