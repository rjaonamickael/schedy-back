package com.schedy.service;

import com.schedy.entity.TotpRecoveryCode;
import com.schedy.entity.User;
import com.schedy.repository.TotpRecoveryCodeRepository;
import com.schedy.repository.UserRepository;
import com.schedy.util.CryptoUtil;
import com.schedy.util.TotpEncryptionUtil;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Handles all server-side logic for TOTP-based Two-Factor Authentication.
 *
 * <h3>Login flow (Option A — 202 + pendingToken)</h3>
 * <ol>
 *   <li>Client POSTs credentials to {@code /api/v1/auth/login}</li>
 *   <li>If 2FA is enabled, server returns {@code 202} with {@code requires2fa=true}
 *       and a short-lived {@code pendingToken} (5-minute JWT, type=2fa_pending, no roles).</li>
 *   <li>Client POSTs TOTP code + pendingToken to {@code /api/v1/auth/2fa/verify}.</li>
 *   <li>Server validates token type, verifies TOTP, issues real access+refresh tokens.</li>
 * </ol>
 *
 * <h3>Rate limiting</h3>
 * In-memory {@link ConcurrentHashMap} — same approach as the login lockout in AuthService.
 * Limitation: single-instance only; replace with Redis before horizontal scaling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TotpService {

    private static final int    RECOVERY_CODE_COUNT  = 8;
    private static final int    RECOVERY_CODE_LENGTH = 10; // characters (alphanumeric, uppercase)
    private static final int    MAX_2FA_ATTEMPTS      = 5;
    private static final long   LOCKOUT_DURATION_MS   = 15 * 60 * 1000L;

    private final UserRepository                userRepository;
    private final TotpRecoveryCodeRepository    recoveryCodeRepository;
    private final TotpEncryptionUtil            encryptionUtil;

    /** Per-email 2FA attempt tracker — Caffeine cache with auto-eviction. */
    private final Cache<String, TwoFaAttemptTracker> twoFaAttempts = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    private record TwoFaAttemptTracker(int attempts, Instant firstAttempt, Instant lockedUntil) {
        boolean isLocked() {
            return lockedUntil != null && Instant.now().isBefore(lockedUntil);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────

    /**
     * Generates a new TOTP secret for the current user and returns the setup payload.
     * The secret is encrypted and saved to the user record immediately so that
     * {@link #confirmSetup(String)} can read it back. 2FA is NOT yet enabled at this stage.
     *
     * @return {@link SetupResponse} with the secret, otpauth URI, and QR-code PNG as data URI
     */
    @Transactional
    public SetupResponse setup() {
        User user = currentUser();
        if (user.isTotpEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La double authentification est déjà activée. Désactivez-la d'abord pour reconfigurer.");
        }

        // Generate a new 32-char Base32 secret
        String secret = new DefaultSecretGenerator().generate();

        // Persist it encrypted (not yet enabled — confirmSetup() will flip the flag)
        user.setTotpSecretEncrypted(encryptionUtil.encrypt(secret));
        userRepository.save(user);

        // Build the otpauth:// URI and render as a QR-code PNG
        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer("Schedy")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        String qrBase64;
        try {
            ZxingPngQrGenerator qrGenerator = new ZxingPngQrGenerator();
            byte[] imageData = qrGenerator.generate(qrData);
            qrBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageData);
        } catch (Exception e) {
            log.error("QR code generation failed for {}: {}", user.getEmail(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible de générer le QR code.");
        }

        log.info("TOTP setup initiated for user: {}", user.getEmail());
        return new SetupResponse(secret, qrData.getUri(), qrBase64);
    }

    /**
     * Verifies the first TOTP code entered by the user, enables 2FA, and returns
     * the one-time-visible recovery codes (plain text — never stored in plain text).
     *
     * @param code 6-digit TOTP code from the authenticator app
     * @return list of {@value #RECOVERY_CODE_COUNT} plain-text recovery codes
     */
    @Transactional
    public List<String> confirmSetup(String code) {
        User user = currentUser();

        if (user.getTotpSecretEncrypted() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Aucun secret TOTP en attente. Lancez d'abord /2fa/setup.");
        }
        if (user.isTotpEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La double authentification est déjà activée.");
        }

        String secret = encryptionUtil.decrypt(user.getTotpSecretEncrypted());
        if (!verifyTotpCode(secret, code)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Code TOTP invalide. Vérifiez l'heure de votre appareil et réessayez.");
        }

        // Enable 2FA and invalidate existing refresh token (force re-auth with 2FA)
        user.setTotpEnabled(true);
        user.setTotpLastUsedOtp(code); // mark first code as used (replay prevention)
        user.setRefreshToken(null);
        userRepository.save(user);

        // Generate and store recovery codes
        List<String> plainCodes = generateRecoveryCodes(user.getId());

        log.info("TOTP 2FA enabled for user: {}", user.getEmail());
        return plainCodes;
    }

    /**
     * Verifies the TOTP code and then disables 2FA, clearing all 2FA state.
     *
     * @param code 6-digit TOTP code (or a recovery code to disable via recovery)
     */
    @Transactional
    public void disable(String code) {
        User user = currentUser();

        if (!user.isTotpEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La double authentification n'est pas activée.");
        }

        String secret = encryptionUtil.decrypt(user.getTotpSecretEncrypted());
        if (!verifyTotpCode(secret, code)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Code TOTP invalide.");
        }

        clearTotpState(user);
        log.info("TOTP 2FA disabled for user: {}", user.getEmail());
    }

    // ─────────────────────────────────────────────────────────────────
    // Verification (login flow)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Verifies a TOTP code during the login second step.
     * Enforces per-account rate limiting and replay prevention via
     * {@code totp_last_used_otp}.
     *
     * @param email the user's email (extracted from the pendingToken)
     * @param code  6-digit TOTP code
     * @return true if the code is valid and not replayed
     */
    @Transactional
    public boolean verify(String email, String code) {
        checkRateLimit(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Utilisateur introuvable"));

        if (!user.isTotpEnabled() || user.getTotpSecretEncrypted() == null) {
            return false;
        }

        // Replay prevention: reject the same OTP if it was the last accepted one
        if (code.equals(user.getTotpLastUsedOtp())) {
            log.warn("TOTP replay attempt for user: {}", email);
            return false;
        }

        String secret = encryptionUtil.decrypt(user.getTotpSecretEncrypted());
        boolean valid = verifyTotpCode(secret, code);

        if (valid) {
            user.setTotpLastUsedOtp(code);
            userRepository.save(user);
            clearAttempts(email);
            log.info("TOTP verification succeeded for: {}", email);
        } else {
            recordFailedAttempt(email);
            log.warn("TOTP verification failed for: {}", email);
        }

        return valid;
    }

    /**
     * Verifies a recovery code during the login second step.
     * The code is marked as used on success.
     *
     * @param email the user's email (extracted from the pendingToken)
     * @param code  plain-text recovery code (e.g. "ABCD-EFGHIJ")
     * @return true if the recovery code is valid and unused
     */
    @Transactional
    public boolean verifyRecoveryCode(String email, String code) {
        checkRateLimit(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Utilisateur introuvable"));

        String normalised = normaliseRecoveryCode(code);
        String hash = CryptoUtil.sha256(normalised);

        Optional<TotpRecoveryCode> match =
                recoveryCodeRepository.findByUserIdAndCodeHashAndUsedFalse(user.getId(), hash);

        if (match.isEmpty()) {
            recordFailedAttempt(email);
            log.warn("Recovery code verification failed for: {}", email);
            return false;
        }

        TotpRecoveryCode rc = match.get();
        rc.setUsed(true);
        rc.setUsedAt(Instant.now());
        recoveryCodeRepository.save(rc);
        clearAttempts(email);

        long remaining = recoveryCodeRepository.countByUserIdAndUsedFalse(user.getId());
        if (remaining <= 2) {
            log.warn("User {} has only {} recovery code(s) remaining after use", email, remaining);
        }

        log.info("Recovery code used for: {} — {} code(s) remaining", email, remaining);
        return true;
    }

    // ─────────────────────────────────────────────────────────────────
    // Status
    // ─────────────────────────────────────────────────────────────────

    /**
     * Returns the recovery code status for the currently authenticated user.
     */
    @Transactional(readOnly = true)
    public RecoveryStatusResponse getRecoveryStatus() {
        User user = currentUser();
        long remaining = recoveryCodeRepository.countByUserIdAndUsedFalse(user.getId());
        return new RecoveryStatusResponse(RECOVERY_CODE_COUNT, (int) remaining);
    }

    /**
     * Returns whether 2FA is enabled for the currently authenticated user,
     * along with how many recovery codes they still have available.
     */
    @Transactional(readOnly = true)
    public TwoFaStatusResponse getStatus() {
        User user = currentUser();
        int remaining = user.isTotpEnabled()
                ? (int) recoveryCodeRepository.countByUserIdAndUsedFalse(user.getId())
                : 0;
        return new TwoFaStatusResponse(user.isTotpEnabled(), remaining);
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    private boolean verifyTotpCode(String secret, String code) {
        CodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(),
                new SystemTimeProvider());
        ((DefaultCodeVerifier) verifier).setAllowedTimePeriodDiscrepancy(1); // ±30s window
        return verifier.isValidCode(secret, code);
    }

    /**
     * Generates {@value #RECOVERY_CODE_COUNT} recovery codes, stores their hashes,
     * and returns the plain-text codes for one-time display to the user.
     */
    private List<String> generateRecoveryCodes(Long userId) {
        // Remove any existing (e.g. from a previous setup attempt)
        recoveryCodeRepository.deleteByUserId(userId);

        SecureRandom rng = new SecureRandom();
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I/O/1/0 — reduces transcription errors
        List<String> plainCodes = new ArrayList<>(RECOVERY_CODE_COUNT);

        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            // Generate e.g. "ABCDE-FGHIJ" (5+dash+5 chars)
            StringBuilder sb = new StringBuilder(RECOVERY_CODE_LENGTH + 1);
            for (int j = 0; j < RECOVERY_CODE_LENGTH; j++) {
                if (j == 5) sb.append('-');
                sb.append(alphabet.charAt(rng.nextInt(alphabet.length())));
            }
            String plain = sb.toString(); // "XXXXX-XXXXX"
            plainCodes.add(plain);

            TotpRecoveryCode rc = TotpRecoveryCode.builder()
                    .userId(userId)
                    .codeHash(CryptoUtil.sha256(plain))
                    .used(false)
                    .build();
            recoveryCodeRepository.save(rc);
        }

        return plainCodes;
    }

    private void clearTotpState(User user) {
        recoveryCodeRepository.deleteByUserId(user.getId());
        user.setTotpEnabled(false);
        user.setTotpSecretEncrypted(null);
        user.setTotpLastUsedOtp(null);
        user.setRefreshToken(null); // force re-auth after 2FA state change
        userRepository.save(user);
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Utilisateur introuvable"));
    }

    /** Strips formatting (spaces, dashes) and uppercases the recovery code before hashing. */
    private String normaliseRecoveryCode(String code) {
        return code.replaceAll("[\\s-]", "").toUpperCase();
    }

    // ── Rate limiting ──

    /** Throws 429 if the account is currently locked due to too many 2FA failures. */
    private void checkRateLimit(String email) {
        TwoFaAttemptTracker tracker = twoFaAttempts.getIfPresent(email);
        if (tracker != null && tracker.isLocked()) {
            log.warn("2FA rate limit exceeded for: {}", email);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Trop de tentatives. Veuillez réessayer dans quelques minutes.");
        }
    }

    private void recordFailedAttempt(String email) {
        twoFaAttempts.asMap().compute(email, (key, existing) -> {
            Instant now = Instant.now();
            if (existing == null ||
                    now.toEpochMilli() - existing.firstAttempt().toEpochMilli() > LOCKOUT_DURATION_MS) {
                return new TwoFaAttemptTracker(1, now, null);
            }
            int newCount = existing.attempts() + 1;
            if (newCount >= MAX_2FA_ATTEMPTS) {
                log.warn("2FA account locked after {} attempts: {}", newCount, email);
                return new TwoFaAttemptTracker(newCount, existing.firstAttempt(),
                        now.plusMillis(LOCKOUT_DURATION_MS));
            }
            return new TwoFaAttemptTracker(newCount, existing.firstAttempt(), null);
        });
    }

    private void clearAttempts(String email) {
        twoFaAttempts.invalidate(email);
    }

    // ─────────────────────────────────────────────────────────────────
    // Response types (public records — serialised by Jackson)
    // ─────────────────────────────────────────────────────────────────

    public record SetupResponse(
            String secret,
            String otpauthUri,
            String qrCodeBase64
    ) {}

    public record RecoveryStatusResponse(
            int total,
            int remaining
    ) {}

    public record TwoFaStatusResponse(
            boolean enabled,
            int recoveryCodesRemaining
    ) {}
}
