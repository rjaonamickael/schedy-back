package com.schedy.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B-07 — Unit tests for CryptoUtil.
 *
 * Covers the secure-token generation consolidated from the duplicated
 * implementations that previously existed in AuthService, EmployeService,
 * and SuperAdminService.
 *
 * The sha256 method is tested as well since both token flow and QR-code
 * hashing depend on it.
 */
@DisplayName("CryptoUtil unit tests (B-07 / B-16)")
class CryptoUtilTest {

    // ── generateSecureToken ───────────────────────────────────────────────────

    @Nested
    @DisplayName("generateSecureToken()")
    class GenerateSecureToken {

        @Test
        @DisplayName("returns a 64-character string (32 bytes as hex)")
        void returnsSixtyFourCharacters() {
            String token = CryptoUtil.generateSecureToken();
            assertThat(token).hasSize(64);
        }

        @Test
        @DisplayName("matches regex [0-9a-f]{64} (lowercase hex only)")
        void matchesLowercaseHexRegex() {
            String token = CryptoUtil.generateSecureToken();
            assertThat(token).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("two consecutive calls produce different tokens (random)")
        void consecutiveCallsProduceDifferentTokens() {
            String first  = CryptoUtil.generateSecureToken();
            String second = CryptoUtil.generateSecureToken();
            assertThat(first).isNotEqualTo(second);
        }

        @RepeatedTest(value = 20, name = "randomness check #{currentRepetition}/{totalRepetitions}")
        @DisplayName("20 tokens are all unique (collision extremely unlikely with 256-bit entropy)")
        void twentyTokensAreUnique() {
            // If this test ever fails the SecureRandom implementation is broken,
            // not the production probability estimate.
            Set<String> tokens = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                tokens.add(CryptoUtil.generateSecureToken());
            }
            assertThat(tokens).hasSize(20);
        }

        @Test
        @DisplayName("never returns null")
        void neverReturnsNull() {
            assertThat(CryptoUtil.generateSecureToken()).isNotNull();
        }
    }

    // ── sha256 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sha256(String)")
    class Sha256 {

        @Test
        @DisplayName("produces 64-character lowercase hex string")
        void produces64CharHex() {
            String hash = CryptoUtil.sha256("hello");
            assertThat(hash)
                    .hasSize(64)
                    .matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("known SHA-256 digest for 'hello'")
        void knownDigestForHello() {
            // SHA-256("hello") verified against standard test vectors
            assertThat(CryptoUtil.sha256("hello"))
                    .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        }

        @Test
        @DisplayName("same input always produces same digest (deterministic)")
        void isDeterministic() {
            String input = "schedy-token-test";
            assertThat(CryptoUtil.sha256(input)).isEqualTo(CryptoUtil.sha256(input));
        }

        @Test
        @DisplayName("different inputs produce different digests")
        void differentInputsDifferentDigests() {
            assertThat(CryptoUtil.sha256("abc")).isNotEqualTo(CryptoUtil.sha256("ABC"));
        }

        @Test
        @DisplayName("token round-trip: sha256(generateSecureToken()) is 64-char hex")
        void tokenHashIsValidHex() {
            String token = CryptoUtil.generateSecureToken();
            String hash  = CryptoUtil.sha256(token);
            assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        }
    }
}
