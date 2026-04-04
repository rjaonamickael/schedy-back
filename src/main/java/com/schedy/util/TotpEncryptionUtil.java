package com.schedy.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM symmetric encryption for TOTP secrets stored in the database.
 *
 * <p>The encryption key is a Base64-encoded 32-byte value sourced from the
 * {@code TOTP_ENCRYPTION_KEY} environment variable (via {@code schedy.totp.encryption-key}).
 * A default dev-only key is provided — production deployments MUST override it.
 *
 * <p>Wire format: {@code Base64(IV[12] || Ciphertext+Tag[N+16])}
 * <ul>
 *   <li>IV:  12 random bytes (GCM standard nonce)</li>
 *   <li>Tag: 128-bit authentication tag (GCM default)</li>
 * </ul>
 */
@Slf4j
@Component
public class TotpEncryptionUtil {

    private static final String ALGORITHM     = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LENGTH = 12;  // bytes — 96-bit nonce recommended for GCM
    private static final int    GCM_TAG_BITS  = 128; // authentication tag length
    private static final String DEV_KEY_DEFAULT = "c2NoZWR5LWRldi10b3RwLWtleS0zMmJ5dGVzISEhISE=";

    private final SecretKey secretKey;
    private final String    rawBase64Key;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    public TotpEncryptionUtil(
            @Value("${schedy.totp.encryption-key:c2NoZWR5LWRldi10b3RwLWtleS0zMmJ5dGVzISEhISE=}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                "TOTP_ENCRYPTION_KEY must be exactly 32 bytes (256 bits) when Base64-decoded. " +
                "Got " + keyBytes.length + " bytes. " +
                "Generate with: openssl rand -base64 32");
        }
        this.secretKey    = new SecretKeySpec(keyBytes, "AES");
        this.rawBase64Key = base64Key;
        log.info("TotpEncryptionUtil initialised with AES-256-GCM");
    }

    @PostConstruct
    public void validateKeyForProfile() {
        boolean usingDevKey = DEV_KEY_DEFAULT.equals(rawBase64Key);
        if (!usingDevKey) {
            return;
        }
        if ("dev".equals(activeProfile)) {
            log.warn("**SECURITY WARNING** schedy.totp.encryption-key is using the compiled-in dev " +
                     "default. Set TOTP_ENCRYPTION_KEY environment variable before deploying to production.");
        } else {
            throw new IllegalStateException(
                "TOTP encryption key must be set in production. " +
                "Set TOTP_ENCRYPTION_KEY environment variable. " +
                "Generate with: openssl rand -base64 32");
        }
    }

    /**
     * Encrypts the plain-text TOTP secret and returns a Base64-encoded blob
     * containing the random IV prepended to the GCM ciphertext+tag.
     *
     * @param plainText the raw TOTP secret (e.g. a Base32 string)
     * @return Base64(IV || ciphertext+tag)
     */
    public String encrypt(String plainText) {
        try {
            byte[] iv = generateIv();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes());

            // Prepend the IV so we can re-derive it on decryption
            byte[] blob = new byte[iv.length + cipherText.length];
            System.arraycopy(iv,         0, blob, 0,         iv.length);
            System.arraycopy(cipherText, 0, blob, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(blob);
        } catch (Exception e) {
            throw new RuntimeException("TOTP secret encryption failed", e);
        }
    }

    /**
     * Decrypts a Base64-encoded blob previously produced by {@link #encrypt(String)}.
     *
     * @param base64Blob Base64(IV || ciphertext+tag)
     * @return the original plain-text TOTP secret
     */
    public String decrypt(String base64Blob) {
        try {
            byte[] blob = Base64.getDecoder().decode(base64Blob);
            byte[] iv         = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[blob.length - GCM_IV_LENGTH];
            System.arraycopy(blob, 0,             iv,         0, iv.length);
            System.arraycopy(blob, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText));
        } catch (Exception e) {
            throw new RuntimeException("TOTP secret decryption failed", e);
        }
    }

    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
