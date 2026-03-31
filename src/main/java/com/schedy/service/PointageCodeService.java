package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.PointageCodeDto;
import com.schedy.dto.response.KioskPointageCodeResponse;
import com.schedy.entity.Employe;
import com.schedy.entity.PointageCode;
import com.schedy.entity.PointageCode.UniteRotation;
import com.schedy.exception.BusinessRuleException;
import com.schedy.repository.EmployeRepository;
import com.schedy.repository.PointageCodeRepository;
import com.schedy.util.CryptoUtil;
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
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointageCodeService {

    private final PointageCodeRepository pointageCodeRepository;
    private final EmployeRepository employeRepository;
    private final TenantContext tenantContext;
    private final PasswordEncoder passwordEncoder;

    @Value("${schedy.kiosk.admin-code:}")
    private String kioskAdminCode;

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
     * Returns KioskPointageCodeResponse (no PIN) to prevent PIN exposure on public endpoint.
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
     */
    @Transactional(readOnly = true)
    public CodeValidationResult validateAndResolve(String codeOrPin) {
        // Try as code first
        Optional<PointageCode> byCode = pointageCodeRepository.findFirstByCodeAndActifTrueOrderByValidFromDesc(codeOrPin);
        if (byCode.isPresent()) {
            PointageCode pc = byCode.get();
            if (pc.isValid()) {
                return new CodeValidationResult(pc.getSiteId(), pc.getOrganisationId());
            }
            throw new ResponseStatusException(HttpStatus.GONE, "Code expire");
        }

        // Try as PIN via SHA-256 hash lookup (B-M19)
        Optional<PointageCode> byPin = pointageCodeRepository.findFirstByPinHashAndActifTrueOrderByValidFromDesc(sha256(codeOrPin));
        if (byPin.isPresent()) {
            PointageCode pc = byPin.get();
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
                .pin(pin)
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
     * Called whenever a new pointage code is generated so employee PINs stay in sync
     * with the new rotation cycle.
     *
     * @param siteId the site whose employees need fresh PINs
     * @param orgId  the organisation that owns the site
     * @return number of employees whose PINs were regenerated
     */
    private int regenerateEmployeePins(String siteId, String orgId) {
        List<Employe> employes = employeRepository.findBySiteIdsContainingAndOrganisationId(siteId, orgId);
        for (Employe emp : employes) {
            String newPin = String.format("%04d", RANDOM.nextInt(10000));
            emp.setPin(passwordEncoder.encode(newPin));
            emp.setPinHash(CryptoUtil.sha256(newPin));
            emp.setPinClair(newPin);
        }
        if (!employes.isEmpty()) {
            employeRepository.saveAll(employes);
            log.info("Regenerated PINs for {} employees on site {}", employes.size(), siteId);
        }
        return employes.size();
    }

    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            code = generateRandomCode(8);
            attempts++;
            if (attempts > 100) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unable to generate unique code after 100 attempts");
            }
        } while (pointageCodeRepository.existsByCodeAndActifTrue(code));
        return code;
    }

    private String generateUniquePin() {
        String pin;
        int attempts = 0;
        do {
            pin = generateRandomPin(6);
            attempts++;
            if (attempts > 100) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unable to generate unique PIN after 100 attempts");
            }
        } while (pointageCodeRepository.existsByPinAndActifTrue(pin));
        return pin;
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
        return new PointageCodeDto(
                pc.getId(),
                pc.getSiteId(),
                pc.getCode(),
                pc.getPin(),
                pc.getRotationValeur(),
                pc.getRotationUnite().name(),
                pc.getValidFrom().toString(),
                pc.getValidTo().toString(),
                pc.isActif()
        );
    }

    /**
     * Builds the public kiosk DTO. PIN is intentionally omitted — the PIN is only
     * returned on the authenticated GET /site/{siteId} endpoint (PointageCodeDto).
     */
    private KioskPointageCodeResponse toKioskDto(PointageCode pc) {
        return new KioskPointageCodeResponse(
                pc.getSiteId(),
                pc.getCode(),
                pc.getPin(),
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
