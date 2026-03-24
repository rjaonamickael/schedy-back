package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.PointageCodeDto;
import com.schedy.dto.response.KioskPointageCodeResponse;
import com.schedy.entity.PointageCode;
import com.schedy.entity.PointageCode.FrequenceRotation;
import com.schedy.exception.BusinessRuleException;
import com.schedy.repository.EmployeRepository;
import com.schedy.repository.PointageCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointageCodeService {

    private final PointageCodeRepository pointageCodeRepository;
    private final EmployeRepository employeRepository;
    private final TenantContext tenantContext;

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
        Optional<PointageCode> existing = pointageCodeRepository.findBySiteIdAndActifTrueAndOrganisationId(siteId, orgId);
        if (existing.isPresent() && existing.get().isValid()) {
            return toDto(existing.get());
        }
        return null;
    }

    @Transactional
    public PointageCodeDto getOrCreateForSite(String siteId, FrequenceRotation frequence) {
        String orgId = tenantContext.requireOrganisationId();
        Optional<PointageCode> existing = pointageCodeRepository.findBySiteIdAndActifTrueAndOrganisationId(siteId, orgId);
        if (existing.isPresent() && existing.get().isValid()) {
            return toDto(existing.get());
        }
        return toDto(generateNewCode(siteId, frequence, orgId));
    }

    @Transactional
    public PointageCodeDto regenerateNow(String siteId, FrequenceRotation frequence) {
        String orgId = tenantContext.requireOrganisationId();
        // If no frequency provided, use the existing one
        if (frequence == null) {
            Optional<PointageCode> existing = pointageCodeRepository.findBySiteIdAndActifTrueAndOrganisationId(siteId, orgId);
            frequence = existing.map(PointageCode::getFrequence).orElse(FrequenceRotation.QUOTIDIEN);
        }
        return toDto(generateNewCode(siteId, frequence, orgId));
    }

    @Transactional
    public PointageCodeDto updateFrequence(String siteId, FrequenceRotation frequence) {
        String orgId = tenantContext.requireOrganisationId();
        return toDto(generateNewCode(siteId, frequence, orgId));
    }

    // ---- Public methods (no TenantContext - kiosk/validation endpoints) ----

    /**
     * Public kiosk endpoint: returns the active code for a site if one exists and is valid.
     * Never creates or regenerates codes — that is strictly an authenticated operation.
     * Returns KioskPointageCodeResponse (no PIN) to prevent PIN exposure on public endpoint.
     */
    @Transactional(readOnly = true)
    public KioskPointageCodeResponse getActiveForSitePublic(String siteId) {
        Optional<PointageCode> existing = pointageCodeRepository.findBySiteIdAndActifTrue(siteId);
        if (existing.isPresent() && existing.get().isValid()) {
            return toKioskDto(existing.get());
        }
        return null;
    }

    /**
     * Internal method for DataInitializer -- creates a code without TenantContext.
     */
    @Transactional
    public PointageCodeDto createForSiteInternal(String siteId, FrequenceRotation frequence, String organisationId) {
        Optional<PointageCode> existing = pointageCodeRepository.findBySiteIdAndActifTrue(siteId);
        if (existing.isPresent() && existing.get().isValid()) {
            return toDto(existing.get());
        }
        return toDto(generateNewCode(siteId, frequence, organisationId));
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
        Optional<PointageCode> byCode = pointageCodeRepository.findByCodeAndActifTrue(codeOrPin);
        if (byCode.isPresent()) {
            PointageCode pc = byCode.get();
            if (pc.isValid()) {
                return new CodeValidationResult(pc.getSiteId(), pc.getOrganisationId());
            }
            throw new ResponseStatusException(HttpStatus.GONE, "Code expire");
        }

        // Try as PIN via SHA-256 hash lookup (B-M19)
        Optional<PointageCode> byPin = pointageCodeRepository.findByPinHashAndActifTrue(sha256(codeOrPin));
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

    // ---- Internal helpers ----

    private PointageCode generateNewCode(String siteId, FrequenceRotation frequence, String orgId) {
        // Deactivate existing code for this site
        pointageCodeRepository.findBySiteIdAndActifTrueAndOrganisationId(siteId, orgId)
                .ifPresent(old -> {
                    old.setActif(false);
                    pointageCodeRepository.save(old);
                    log.info("Deactivated old pointage code for site: {}", siteId);
                });

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime validTo = calculateValidTo(now, frequence);

        String code = generateUniqueCode();
        String pin = generateUniquePin();

        PointageCode pointageCode = PointageCode.builder()
                .siteId(siteId)
                .code(code)
                .pin(pin)
                .pinHash(sha256(pin))
                .frequence(frequence)
                .validFrom(now)
                .validTo(validTo)
                .actif(true)
                .organisationId(orgId)
                .build();

        pointageCode = pointageCodeRepository.save(pointageCode);
        log.info("Generated new pointage code for site: {} (valid until {})", siteId, validTo);
        return pointageCode;
    }

    private OffsetDateTime calculateValidTo(OffsetDateTime from, FrequenceRotation frequence) {
        return switch (frequence) {
            case QUOTIDIEN -> from.plusHours(24);
            case HEBDOMADAIRE -> from.plusDays(7);
            case BI_HEBDOMADAIRE -> from.plusDays(14);
            case MENSUEL -> from.plusDays(30);
        };
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
                pc.getFrequence().name(),
                pc.getValidFrom().toString(),
                pc.getValidTo().toString(),
                pc.isActif()
        );
    }

    private KioskPointageCodeResponse toKioskDto(PointageCode pc) {
        return new KioskPointageCodeResponse(
                pc.getSiteId(),
                pc.getCode(),
                pc.getFrequence().name(),
                pc.getValidFrom().toString(),
                pc.getValidTo().toString(),
                pc.isActif()
        );
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
