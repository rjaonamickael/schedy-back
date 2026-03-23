package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.PointageCodeDto;
import com.schedy.entity.PointageCode;
import com.schedy.entity.PointageCode.FrequenceRotation;
import com.schedy.entity.Site;
import com.schedy.repository.PointageCodeRepository;
import com.schedy.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointageCodeService {

    private final PointageCodeRepository pointageCodeRepository;
    private final SiteRepository siteRepository;
    private final TenantContext tenantContext;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    // ---- Authenticated methods (use TenantContext) ----

    @Transactional
    public PointageCodeDto getActiveForSite(String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        Optional<PointageCode> existing = pointageCodeRepository.findBySiteIdAndActifTrueAndOrganisationId(siteId, orgId);
        if (existing.isPresent() && existing.get().isValid()) {
            return toDto(existing.get());
        }
        // Auto-create a code if none exists
        log.info("Auto-creating pointage code for site: {} (org: {})", siteId, orgId);
        return toDto(generateNewCode(siteId, FrequenceRotation.QUOTIDIEN, orgId));
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

    @Transactional
    public PointageCodeDto getActiveForSitePublic(String siteId) {
        Optional<PointageCode> existing = pointageCodeRepository.findBySiteIdAndActifTrue(siteId);
        if (existing.isPresent()) {
            PointageCode pc = existing.get();
            if (pc.isValid()) {
                return toDto(pc);
            }
            // Code expired -- deactivate it and auto-regenerate with the same frequency
            String orgId = pc.getOrganisationId();
            FrequenceRotation freq = pc.getFrequence();
            pc.setActif(false);
            pointageCodeRepository.save(pc);
            log.info("Auto-deactivated expired pointage code for site: {}", siteId);
            return toDto(generateNewCode(siteId, freq, orgId));
        }
        // No code ever existed -- auto-create one by resolving the organisation from the site
        Optional<Site> site = siteRepository.findById(siteId);
        if (site.isPresent() && site.get().getOrganisationId() != null) {
            log.info("Auto-creating first pointage code for site: {}", siteId);
            return toDto(generateNewCode(siteId, FrequenceRotation.QUOTIDIEN, site.get().getOrganisationId()));
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

    @Transactional(readOnly = true)
    public String validateCode(String codeOrPin) {
        // Try as code first
        Optional<PointageCode> byCode = pointageCodeRepository.findByCodeAndActifTrue(codeOrPin);
        if (byCode.isPresent()) {
            PointageCode pc = byCode.get();
            if (pc.isValid()) {
                return pc.getSiteId();
            }
            throw new ResponseStatusException(HttpStatus.GONE, "Code expire");
        }

        // Try as PIN
        Optional<PointageCode> byPin = pointageCodeRepository.findByPinAndActifTrue(codeOrPin);
        if (byPin.isPresent()) {
            PointageCode pc = byPin.get();
            if (pc.isValid()) {
                return pc.getSiteId();
            }
            throw new ResponseStatusException(HttpStatus.GONE, "Code expire");
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Code ou PIN invalide");
    }

    /**
     * Resolves the organisationId for a given site via the PointageCode.
     * Used by the public kiosk validation flow to derive the org from the site.
     */
    @Transactional(readOnly = true)
    public String resolveOrganisationIdFromCode(String codeOrPin) {
        Optional<PointageCode> byCode = pointageCodeRepository.findByCodeAndActifTrue(codeOrPin);
        if (byCode.isPresent() && byCode.get().isValid()) {
            return byCode.get().getOrganisationId();
        }
        Optional<PointageCode> byPin = pointageCodeRepository.findByPinAndActifTrue(codeOrPin);
        if (byPin.isPresent() && byPin.get().isValid()) {
            return byPin.get().getOrganisationId();
        }
        return null;
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

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime validTo = calculateValidTo(now, frequence);

        String code = generateUniqueCode();
        String pin = generateUniquePin();

        PointageCode pointageCode = PointageCode.builder()
                .siteId(siteId)
                .code(code)
                .pin(pin)
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

    private LocalDateTime calculateValidTo(LocalDateTime from, FrequenceRotation frequence) {
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
}
