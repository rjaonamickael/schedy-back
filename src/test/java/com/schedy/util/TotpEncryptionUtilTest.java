package com.schedy.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("TotpEncryptionUtil unit tests")
class TotpEncryptionUtilTest {

    private static final String DEV_KEY_DEFAULT =
            "c2NoZWR5LWRldi10b3RwLWtleS0zMmJ5dGVzISEhISE=";
    private static final String CUSTOM_KEY =
            "dGVzdC1wcm9kdWN0aW9uLWtleS0zMmJ5dGVzISEhISE=";

    @Nested
    @DisplayName("B-03 — validateKeyForProfile @PostConstruct guard")
    class ValidateKeyForProfile {

        private TotpEncryptionUtil buildUtil(String base64Key, String profile) {
            TotpEncryptionUtil util = new TotpEncryptionUtil(base64Key);
            ReflectionTestUtils.setField(util, "activeProfile", profile);
            return util;
        }

        @Test
        @DisplayName("throws for dev-default key in prod profile")
        void devDefaultKey_prodProfile_throws() {
            assertThrows(IllegalStateException.class,
                    () -> buildUtil(DEV_KEY_DEFAULT, "prod").validateKeyForProfile());
        }

        @Test
        @DisplayName("throws for dev-default key in staging profile")
        void devDefaultKey_stagingProfile_throws() {
            assertThrows(IllegalStateException.class,
                    () -> buildUtil(DEV_KEY_DEFAULT, "staging").validateKeyForProfile());
        }

        @Test
        @DisplayName("does NOT throw for dev-default key in dev profile")
        void devDefaultKey_devProfile_doesNotThrow() {
            assertDoesNotThrow(() -> buildUtil(DEV_KEY_DEFAULT, "dev").validateKeyForProfile());
        }

        @Test
        @DisplayName("does NOT throw for custom key in prod profile")
        void customKey_prodProfile_doesNotThrow() {
            assertDoesNotThrow(() -> buildUtil(CUSTOM_KEY, "prod").validateKeyForProfile());
        }

        @Test
        @DisplayName("constructor throws for invalid key length (16 bytes)")
        void constructor_throwsForShortKey() {
            String shortKey = java.util.Base64.getEncoder().encodeToString(new byte[16]);
            assertThrows(IllegalArgumentException.class, () -> new TotpEncryptionUtil(shortKey));
        }
    }

    @Nested
    @DisplayName("encrypt/decrypt round-trip")
    class EncryptDecryptRoundTrip {

        private final TotpEncryptionUtil util = new TotpEncryptionUtil(CUSTOM_KEY);

        @Test
        @DisplayName("decrypt(encrypt(plaintext)) returns original")
        void roundTrip_returnsOriginal() {
            String secret = "JBSWY3DPEHPK3PXP";
            assertThat(util.decrypt(util.encrypt(secret))).isEqualTo(secret);
        }

        @Test
        @DisplayName("encrypt produces different ciphertext each call (random IV)")
        void encrypt_producesDistinctCiphertexts() {
            String secret = "JBSWY3DPEHPK3PXP";
            assertThat(util.encrypt(secret)).isNotEqualTo(util.encrypt(secret));
        }
    }
}
