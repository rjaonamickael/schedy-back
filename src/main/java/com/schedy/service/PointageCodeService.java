package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.PointageCodeDto;
import com.schedy.dto.response.KioskPointageCodeResponse;
import com.schedy.entity.Employe;
import com.schedy.entity.PinAuditLog;
import com.schedy.entity.PointageCode;
import com.schedy.entity.PointageCode.UniteRotation;
import com.schedy.exception.BusinessRuleException;
import com.schedy.repository.EmployeRepository;
import com.schedy.repository.PointageCodeRepository;
import com.schedy.util.CryptoUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointageCodeService {

    private final PointageCodeRepository pointageCodeRepository;
    private final EmployeRepository employeRepository;
    private final TenantContext tenantContext;
    private final PasswordEncoder passwordEncoder;
    private final com.schedy.util.TotpEncryptionUtil pinEncryptionUtil;
    private final PinAuditLogger pinAuditLogger;

    @Value("${schedy.kiosk.admin-code:}")
    private String kioskAdminCode;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @PostConstruct
    public void validateKioskAdminCodeConfig() {
        boolean isBlank = kioskAdminCode == null || kioskAdminCode.isBlank();
        if (isBlank) {
            if ("dev".equals(activeProfile)) {
                kioskAdminCode = "2580";
                log.warn("**SECURITY WARNING** schedy.kiosk.admin-code is not configured. " +
                         "Using dev default. Set KIOSK_ADMIN_CODE environment variable " +
                         "before deploying to production.");
            } else {
                throw new IllegalStateException(
                    "Kiosk admin code must be configured. " +
                    "Set KIOSK_ADMIN_CODE environment variable.");
            }
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    // ---- Authenticated methods (use TenantContext) ----

    /**
     * Returns the active pointage code for a site, or null if none exists.
     * Does NOT auto-create — GET endpoints must not have side effects (B-H21).
     * Use getOrCreateForSite() or regenerateNow() to create codes explicitly.
     */
    @Transactional(readOnly = true)
    public PointageCodeDto getActiveForSite(String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        Optional<PointageCode> existing = pointageCodeRepository.findFirstBySiteIdAndActifTrueAndOrganisationIdOrderByValidFromDesc(siteId, orgId);
        if (existing.isPresent() && existing.get().isValid()) {
            return toDto(existing.get());
        }
        return null;
    }

    @Transactional
    public PointageCodeDto getOrCreateForSite(String siteId, int rotationValeur, UniteRotation rotationUnite) {
        validateRotation(rotationValeur, rotationUnite);
        String orgId = tenantContext.requireOrganisationId();
        Optional<PointageCode> existing = pointageCodeRepository.findFirstBySiteIdAndActifTrueAndOrganisationIdOrderByValidFromDesc(siteId, orgId);
        if (existing.isPresent() && existing.get().isValid()) {
            PointageCode code = existing.get();
            // Update rotation if changed
            if (code.getRotationValeur() != rotationValeur || code.getRotationUnite() != rotationUnite) {
                code.setRotationValeur(rotationValeur);
                code.setRotationUnite(rotationUnite);
                // Recalculate validTo based on new frequency from validFrom
                code.setValidTo(calculateValidTo(code.getValidFrom(), rotationValeur, rotationUnite));
                pointageCodeRepository.save(code);
                log.info("Updated rotation for site {} to {} {}", siteId, rotationValeur, rotationUnite);
            }
            return toDto(code);
        }
        return toDto(generateNewCode(siteId, rotationValeur, rotationUnite, orgId));
    }

    @Transactional
    public PointageCodeDto regenerateNow(String siteId, Integer rotationValeur, UniteRotation rotationUnite) {
        String orgId = tenantContext.requireOrganisationId();
        // If no rotation params provided, carry over from the existing code
        if (rotationValeur == null || rotationUnite == null) {
            Optional<PointageCode> existing = pointageCodeRepository.findFirstBySiteIdAndActifTrueAndOrganisationIdOrderByValidFromDesc(siteId, orgId);
            int valeur = existing.map(PointageCode::getRotationValeur).orElse(1);
            UniteRotation unite = existing.map(PointageCode::getRotationUnite).orElse(UniteRotation.JOURS);
            return toDto(generateNewCode(siteId, valeur, unite, orgId));
        }
        return toDto(generateNewCode(siteId, rotationValeur, rotationUnite, orgId));
    }

    @Transactional
    public PointageCodeDto updateRotation(String siteId, int rotationValeur, UniteRotation rotationUnite) {
        String orgId = tenantContext.requireOrganisationId();
        return toDto(generateNewCode(siteId, rotationValeur, rotationUnite, orgId));
    }

    // ---- Public methods (no TenantContext - kiosk/validation endpoints) ----

    /**
     * Public kiosk endpoint: returns the active code for a site if one exists and is valid.
     * Never creates or regenerates codes — that is strictly an authenticated operation.
     * The PIN is included in the response so the kiosk screen can display it.
     */
    @Transactional(readOnly = true)
    public KioskPointageCodeResponse getActiveForSitePublic(String siteId) {
        Optional<PointageCode> existing = pointageCodeRepository.findFirstBySiteIdAndActifTrueOrderByValidFromDesc(siteId);
        if (existing.isPresent() && existing.get().isValid()) {
            return toKioskDto(existing.get());
        }
        return null;
    }

    /**
     * Internal method for system use (scheduler, setup) — creates a code without TenantContext.
     */
    @Transactional
    public PointageCodeDto createForSiteInternal(String siteId, int rotationValeur, UniteRotation rotationUnite, String organisationId) {
        Optional<PointageCode> existing = pointageCodeRepository.findFirstBySiteIdAndActifTrueOrderByValidFromDesc(siteId);
        if (existing.isPresent() && existing.get().isValid()) {
            return toDto(existing.get());
        }
        return toDto(generateNewCode(siteId, rotationValeur, rotationUnite, organisationId));
    }

    /**
     * Result of code validation: siteId + organisationId in a single DB read (B-M18).
     */
    public record CodeValidationResult(String siteId, String organisationId) {}

    /**
     * Single atomic method that validates the code/PIN and returns both siteId and organisationId.
     * Eliminates the TOCTOU between resolveOrganisationIdFromCode and validateCode (B-M18).
     * Reduces 4 DB round-trips to at most 2.
     *
     * <p>MED-07: Org-scoping note — this endpoint is intentionally unauthenticated (kiosk context),
     * so TenantContext is unavailable and we cannot filter by orgId at query time. Org isolation is
     * enforced at the caller level: the returned {@code organisationId} is extracted from the
     * PointageCode row itself and subsequently verified against the employee via
     * {@link #verifyEmployeBelongsToOrganisation}. As a defence-in-depth guard we reject any
     * code whose stored {@code organisationId} is null, which should never occur for valid rows.
     */
    @Transactional(readOnly = true)
    public CodeValidationResult validateAndResolve(String codeOrPin) {
        // Try as code first
        Optional<PointageCode> byCode = pointageCodeRepository.findFirstByCodeAndActifTrueOrderByValidFromDesc(codeOrPin);
        if (byCode.isPresent()) {
            PointageCode pc = byCode.get();
            // MED-07: reject orphan rows that have no organisation attached
            if (pc.getOrganisationId() == null) {
                log.error("PointageCode {} has no organisationId — rejecting (data integrity issue)", pc.getId());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Code invalide");
            }
            if (pc.isValid()) {
                return new CodeValidationResult(pc.getSiteId(), pc.getOrganisationId());
            }
            throw new ResponseStatusException(HttpStatus.GONE, "Code expire");
        }

        // Try as PIN via SHA-256 hash lookup (B-M19)
        Optional<PointageCode> byPin = pointageCodeRepository.findFirstByPinHashAndActifTrueOrderByValidFromDesc(sha256(codeOrPin));
        if (byPin.isPresent()) {
            PointageCode pc = byPin.get();
            // MED-07: reject orphan rows that have no organisation attached
            if (pc.getOrganisationId() == null) {
                log.error("PointageCode {} has no organisationId — rejecting (data integrity issue)", pc.getId());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Code invalide");
            }
            if (pc.isValid()) {
                return new CodeValidationResult(pc.getSiteId(), pc.getOrganisationId());
            }
            throw new ResponseStatusException(HttpStatus.GONE, "Code expire");
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Code ou PIN invalide");
    }

    /**
     * Verifies that the given employeId belongs to the given organisation (B-H17).
     * Prevents an attacker with a valid code from clocking in for employees of other orgs.
     */
    @Transactional(readOnly = true)
    public void verifyEmployeBelongsToOrganisation(String employeId, String organisationId) {
        if (organisationId == null) {
            throw new BusinessRuleException("Organisation non resolvable depuis le code");
        }
        employeRepository.findByIdAndOrganisationId(employeId, organisationId)
                .orElseThrow(() -> new BusinessRuleException("Employe non trouve dans cette organisation"));
    }

    // ---- Kiosk PIN clock-in ----

    /**
     * Find an employee by raw PIN who belongs to the given site.
     * Used by the public kiosk PIN endpoint (no TenantContext).
     * Returns null if no matching employee found.
     */
    @Transactional(readOnly = true)
    public Employe findEmployeByPinAndSite(String rawPin, String siteId) {
        String hash = CryptoUtil.sha256(rawPin);
        var candidates = employeRepository.findByPinHash(hash);
        for (var emp : candidates) {
            // Verify BCrypt matches (pinHash is SHA-256 for fast lookup, pin is BCrypt for security)
            if (emp.getPin() != null && org.springframework.security.crypto.bcrypt.BCrypt.checkpw(rawPin, emp.getPin())) {
                // Check this employee belongs to the site
                if (emp.getSiteIds() != null && emp.getSiteIds().contains(siteId)) {
                    return emp;
                }
            }
        }
        return null;
    }

    // ---- Kiosk admin code validation ----

    /**
     * Validates the kiosk admin code submitted from the kiosk UI.
     * The admin code is configured via the schedy.kiosk.admin-code property.
     * Returns false if the property is not configured (empty).
     */
    public boolean validateKioskAdminCode(String code) {
        if (kioskAdminCode == null || kioskAdminCode.isBlank()) {
            log.warn("Kiosk admin code validation attempted but schedy.kiosk.admin-code is not configured");
            return false;
        }
        // B-06: Use constant-time comparison to prevent timing attacks on the admin code.
        return java.security.MessageDigest.isEqual(
                kioskAdminCode.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ---- Internal helpers ----

    private PointageCode generateNewCode(String siteId, int rotationValeur, UniteRotation rotationUnite, String orgId) {
        validateRotation(rotationValeur, rotationUnite);

        // Deactivate existing code for this site
        pointageCodeRepository.findFirstBySiteIdAndActifTrueAndOrganisationIdOrderByValidFromDesc(siteId, orgId)
                .ifPresent(old -> {
                    old.setActif(false);
                    pointageCodeRepository.save(old);
                    log.info("Deactivated old pointage code for site: {}", siteId);
                });

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime validTo = calculateValidTo(now, rotationValeur, rotationUnite);

        String code = generateUniqueCode();
        String pin = generateUniquePin();

        PointageCode pointageCode = PointageCode.builder()
                .siteId(siteId)
                .code(code)
                .pin(pinEncryptionUtil.encrypt(pin))
                .pinHash(sha256(pin))
                .rotationValeur(rotationValeur)
                .rotationUnite(rotationUnite)
                .validFrom(now)
                .validTo(validTo)
                .actif(true)
                .organisationId(orgId)
                .build();

        pointageCode = pointageCodeRepository.save(pointageCode);
        log.info("Generated new pointage code for site: {} (valid until {}, rotation: {} {})",
                siteId, validTo, rotationValeur, rotationUnite);

        // Cascade: regenerate employee PINs for this site
        regenerateEmployeePins(siteId, orgId);

        return pointageCode;
    }

    private OffsetDateTime calculateValidTo(OffsetDateTime from, int valeur, UniteRotation unite) {
        return switch (unite) {
            case MINUTES  -> from.plusMinutes(valeur);
            case HEURES   -> from.plusHours(valeur);
            case JOURS    -> from.plusDays(valeur);
            case SEMAINES -> from.plusWeeks(valeur);
            case MOIS     -> from.plusMonths(valeur);
        };
    }

    private void validateRotation(int valeur, UniteRotation unite) {
        int max = switch (unite) {
            case MINUTES  -> 59;
            case HEURES   -> 23;
            case JOURS    -> 30;
            case SEMAINES -> 4;
            case MOIS     -> 12;
        };
        if (valeur < 1 || valeur > max) {
            throw new BusinessRuleException(
                    "Valeur " + valeur + " invalide pour " + unite + " (1-" + max + ")");
        }
    }

    /**
     * Regenerates BCrypt + SHA-256 + plain PINs for all employees assigned to a site.
     * Called whenever a new pointage code is generated so employee PINs stay in
     * sync with the rotation cycle the admin configured.
     *
     * <p>V36: every regenerated PIN is recorded in {@code pin_audit_log} with
     * {@link PinAuditLog.Action#REGENERATE_CASCADE} /
     * {@link PinAuditLog.Source#AUTO_ROTATION}. The {@code adminUserId} is
     * left null because cascade rotations are system-triggered side effects of
     * {@code generateNewCode}; manual triggers via {@code regenerateNow} remain
     * visible in the controller-level Spring Security access logs.
     *
     * <p>Known limitation (tracked separately): this method does NOT enforce
     * PIN uniqueness within the site. Two employees on the same site could
     * receive the same 4-digit PIN, in which case the kiosk lookup resolves
     * to whichever row Postgres returns first. The new admin-triggered
     * {@code EmployeService#regenerateIndividualPin} flow does enforce
     * uniqueness; cascade hardening will follow in a dedicated cleanup ticket.
     *
     * @param siteId the site whose employees need fresh PINs
     * @param orgId  the organisation that owns the site
     * @return number of employees whose PINs were regenerated
     */
    private int regenerateEmployeePins(String siteId, String orgId) {
        List<Employe> employes = employeRepository.findBySiteIdsContainingAndOrganisationId(siteId, orgId);
        if (employes.isEmpty()) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Employe emp : employes) {
            String oldPinHash = emp.getPinHash();
            String newPin = String.format("%04d", RANDOM.nextInt(10000));
            String newPinHash = CryptoUtil.sha256(newPin);

            emp.setPin(passwordEncoder.encode(newPin));
            emp.setPinHash(newPinHash);
            // Fix SEC-02: encrypt PIN with AES-256-GCM instead of storing plaintext
            emp.setPinClair(pinEncryptionUtil.encrypt(newPin));
            emp.setPinClairEncrypted(true);
            // V36: track when the PIN was last written and bump the version
            emp.setPinGeneratedAt(now);
            emp.setPinVersion((emp.getPinVersion() == null ? 1 : emp.getPinVersion()) + 1);

            // V36: audit log — system-triggered cascade, no admin attribution
            pinAuditLogger.write(PinAuditLog.builder()
                    .employeId(emp.getId())
                    .adminUserId(null)
                    .action(PinAuditLog.Action.REGENERATE_CASCADE)
                    .source(PinAuditLog.Source.AUTO_ROTATION)
                    .oldPinHash(oldPinHash)
                    .newPinHash(newPinHash)
                    .organisationId(orgId)
                    .build());
        }
        employeRepository.saveAll(employes);
        log.info("Regenerated PINs for {} employees on site {} (cascade)", employes.size(), siteId);
        return employes.size();
    }

    /**
     * Generates a unique 8-character alphanumeric code using a batch approach (B-15).
     * Produces 5 candidates per round, checks all hashes in a single IN query, and picks
     * the first candidate not already in use. Max 3 rounds (15 candidates) before failing.
     */
    private String generateUniqueCode() {
        final int BATCH_SIZE = 5;
        final int MAX_ROUNDS = 3;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<String> candidates = new ArrayList<>(BATCH_SIZE);
            for (int i = 0; i < BATCH_SIZE; i++) {
                candidates.add(generateRandomCode(8));
            }
            Set<String> existing = Set.copyOf(pointageCodeRepository.findExistingCodes(candidates));
            for (String candidate : candidates) {
                if (!existing.contains(candidate)) {
                    return candidate;
                }
            }
            log.warn("All {} code candidates in round {} collided; retrying", BATCH_SIZE, round + 1);
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to generate unique code after " + (MAX_ROUNDS * BATCH_SIZE) + " attempts");
    }

    /**
     * Generates a unique 6-digit PIN using a batch approach (B-15).
     * Produces 5 candidates per round, checks all SHA-256 hashes in a single IN query, and picks
     * the first candidate whose hash is not already in use. Max 3 rounds (15 candidates) before failing.
     */
    private String generateUniquePin() {
        final int BATCH_SIZE = 5;
        final int MAX_ROUNDS = 3;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<String> candidates = new ArrayList<>(BATCH_SIZE);
            for (int i = 0; i < BATCH_SIZE; i++) {
                candidates.add(generateRandomPin(6));
            }
            List<String> candidateHashes = candidates.stream()
                    .map(CryptoUtil::sha256)
                    .toList();
            Set<String> existingHashes = Set.copyOf(pointageCodeRepository.findExistingPinHashes(candidateHashes));
            for (String candidate : candidates) {
                if (!existingHashes.contains(CryptoUtil.sha256(candidate))) {
                    return candidate;
                }
            }
            log.warn("All {} PIN candidates in round {} collided; retrying", BATCH_SIZE, round + 1);
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to generate unique PIN after " + (MAX_ROUNDS * BATCH_SIZE) + " attempts");
    }

    private String generateRandomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    private String generateRandomPin(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private PointageCodeDto toDto(PointageCode pc) {
        String decryptedPin;
        try {
            decryptedPin = pinEncryptionUtil.decrypt(pc.getPin());
        } catch (Exception e) {
            log.error("Failed to decrypt PIN for code {}: {}", pc.getId(), e.getMessage());
            decryptedPin = null;
        }
        return new PointageCodeDto(
                pc.getId(),
                pc.getSiteId(),
                pc.getCode(),
                decryptedPin,
                pc.getRotationValeur(),
                pc.getRotationUnite().name(),
                pc.getValidFrom().toString(),
                pc.getValidTo().toString(),
                pc.isActif()
        );
    }

    /**
     * Builds the public kiosk DTO. The PIN is decrypted before being sent to
     * the kiosk so employees can read the numeric code on screen. The stored
     * {@code pin} column is AES-256-GCM encrypted at rest — forgetting the
     * decrypt step here leaks the ciphertext (base64 blob) to the UI instead
     * of the 6-digit code. Mirrors the decryption step in {@link #toDto}.
     */
    private KioskPointageCodeResponse toKioskDto(PointageCode pc) {
        String decryptedPin;
        try {
            decryptedPin = pinEncryptionUtil.decrypt(pc.getPin());
        } catch (Exception e) {
            log.error("Failed to decrypt PIN for kiosk code {}: {}", pc.getId(), e.getMessage());
            decryptedPin = null;
        }
        return new KioskPointageCodeResponse(
                pc.getSiteId(),
                pc.getCode(),
                decryptedPin,
                pc.getRotationValeur(),
                pc.getRotationUnite().name(),
                pc.getValidFrom().toString(),
                pc.getValidTo().toString(),
                pc.isActif()
        );
    }

    // B-16: Delegate to shared CryptoUtil to avoid code duplication with AuthService
    private String sha256(String input) {
        return CryptoUtil.sha256(input);
    }
}
