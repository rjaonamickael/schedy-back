package com.schedy.entity;

/**
 * Statuts de l'absence imprévue.
 *
 * Transitions :
 *   SIGNALEE  → VALIDEE | REFUSEE | ANNULEE
 *   VALIDEE   → EN_COURS | TRAITEE | ANNULEE
 *   EN_COURS  → TRAITEE | ANNULEE
 *   REFUSEE   → (terminal)
 *   TRAITEE   → (terminal)
 *   ANNULEE   → (terminal)
 *
 * Quand un MANAGER initie : créée directement en VALIDEE (auto-validée).
 * Quand un EMPLOYEE initie : créée en SIGNALEE (attente validation manager).
 */
public enum StatutAbsenceImprevue {
    SIGNALEE,
    VALIDEE,
    REFUSEE,
    EN_COURS,
    TRAITEE,
    ANNULEE
}
