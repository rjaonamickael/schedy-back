package com.schedy.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * B-16: Shared cryptographic utilities to avoid duplicating SHA-256 logic across
 * AuthService and PointageCodeService.
 *
 * <p>B-07: Also consolidates the secure-token generation that was previously
 * duplicated across AuthService, EmployeService, and SuperAdminService.
 */
public final class CryptoUtil {

    private CryptoUtil() {
        // Utility class — no instances
    }

    /**
     * Computes the SHA-256 hex digest of the given input string (UTF-8 encoded).
     *
     * @param input the string to hash
     * @return lowercase hex string of the 32-byte SHA-256 digest
     * @throws RuntimeException if SHA-256 is not available (should never happen on standard JVMs)
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Generates a cryptographically secure random token as a 64-character lowercase hex string
     * (32 random bytes encoded as hex).
     *
     * <p>Use this for invitation tokens, password-reset tokens, and any other single-use
     * secrets. Always store the {@link #sha256(String)} hash — never the raw token.
     *
     * @return 64-character lowercase hex string
     */
    public static String generateSecureToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
