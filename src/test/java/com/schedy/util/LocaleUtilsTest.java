package com.schedy.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B-06 — Unit tests for LocaleUtils.isFrenchSpeaking(String).
 *
 * Verifies the single source of truth that replaced the diverging country-list
 * copies previously scattered across AuthService, EmployeService,
 * SuperAdminService, and RegistrationRequestService.
 */
@DisplayName("LocaleUtils unit tests (B-06)")
class LocaleUtilsTest {

    // ── French-speaking countries: must return true ───────────────────────────

    @Nested
    @DisplayName("isFrenchSpeaking() — French-speaking countries → true")
    class FrenchSpeakingTrue {

        @Test
        @DisplayName("France (FR) → true")
        void france() {
            assertThat(LocaleUtils.isFrenchSpeaking("FR")).isTrue();
        }

        @Test
        @DisplayName("Madagascar (MG, alpha-2) → true")
        void madagascarAlpha2() {
            assertThat(LocaleUtils.isFrenchSpeaking("MG")).isTrue();
        }

        @Test
        @DisplayName("Madagascar (MDG, alpha-3 legacy) → true")
        void madagascarAlpha3Legacy() {
            assertThat(LocaleUtils.isFrenchSpeaking("MDG")).isTrue();
        }

        @Test
        @DisplayName("Senegal (SN) → true")
        void senegal() {
            assertThat(LocaleUtils.isFrenchSpeaking("SN")).isTrue();
        }

        @ParameterizedTest(name = "isFrenchSpeaking(\"{0}\") → true")
        @ValueSource(strings = {"BE", "CH", "LU", "MC", "HT", "CI", "BF", "GN",
                                "ML", "NE", "TG", "BJ", "GA", "CM", "CG", "CD",
                                "TD", "DJ", "KM", "SC", "RE", "GP", "MQ", "GF",
                                "YT", "NC", "PF", "WF", "PM", "BL", "MF"})
        @DisplayName("all OIF member codes → true")
        void oifMembers(String code) {
            assertThat(LocaleUtils.isFrenchSpeaking(code)).isTrue();
        }
    }

    // ── Non-French-speaking: must return false ────────────────────────────────

    @Nested
    @DisplayName("isFrenchSpeaking() — non-French-speaking → false")
    class FrenchSpeakingFalse {

        @Test
        @DisplayName("Canada ISO alpha-3 (CAN) → false (bilingual, defaults to EN)")
        void canadaAlpha3() {
            assertThat(LocaleUtils.isFrenchSpeaking("CAN")).isFalse();
        }

        @Test
        @DisplayName("Canada ISO alpha-2 (CA) → false (bilingual, defaults to EN)")
        void canadaAlpha2() {
            assertThat(LocaleUtils.isFrenchSpeaking("CA")).isFalse();
        }

        @Test
        @DisplayName("United States (US) → false")
        void unitedStates() {
            assertThat(LocaleUtils.isFrenchSpeaking("US")).isFalse();
        }

        @ParameterizedTest(name = "isFrenchSpeaking(\"{0}\") → false")
        @ValueSource(strings = {"DE", "GB", "JP", "BR", "AU", "ZA", "IN", "CN"})
        @DisplayName("other non-French-speaking codes → false")
        void otherCodes(String code) {
            assertThat(LocaleUtils.isFrenchSpeaking(code)).isFalse();
        }
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isFrenchSpeaking() — edge cases")
    class EdgeCases {

        @ParameterizedTest(name = "isFrenchSpeaking({0}) → false")
        @NullAndEmptySource
        @DisplayName("null and empty string → false (no NPE)")
        void nullAndEmpty(String code) {
            assertThat(LocaleUtils.isFrenchSpeaking(code)).isFalse();
        }

        @Test
        @DisplayName("lowercase 'fr' → true (case-insensitive)")
        void caseInsensitiveLowerFr() {
            assertThat(LocaleUtils.isFrenchSpeaking("fr")).isTrue();
        }

        @Test
        @DisplayName("mixed-case 'Fr' → true (case-insensitive)")
        void caseInsensitiveMixedFr() {
            assertThat(LocaleUtils.isFrenchSpeaking("Fr")).isTrue();
        }

        @Test
        @DisplayName("lowercase 'mg' → true (case-insensitive)")
        void caseInsensitiveLowerMg() {
            assertThat(LocaleUtils.isFrenchSpeaking("mg")).isTrue();
        }

        @Test
        @DisplayName("lowercase 'can' → false (Canada, case-insensitive guard)")
        void caseInsensitiveLowerCan() {
            assertThat(LocaleUtils.isFrenchSpeaking("can")).isFalse();
        }

        @Test
        @DisplayName("'  FR  ' with surrounding whitespace → true (trims whitespace)")
        void trimsWhitespace() {
            assertThat(LocaleUtils.isFrenchSpeaking("  FR  ")).isTrue();
        }
    }
}
