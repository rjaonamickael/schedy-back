package com.schedy.service;

import com.schedy.config.JwtUtil;
import com.schedy.dto.request.AnnouncementDto;
import com.schedy.dto.request.FeatureFlagDto;
import com.schedy.dto.response.AnnouncementResponse;
import com.schedy.dto.response.FeatureFlagResponse;
import com.schedy.dto.response.ImpersonateResponse;
import com.schedy.dto.response.ImpersonationLogResponse;
import com.schedy.entity.*;
import com.schedy.repository.FeatureFlagRepository;
import com.schedy.repository.ImpersonationLogRepository;
import com.schedy.repository.PlatformAnnouncementRepository;
import com.schedy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Platform-level operations: announcements, feature flags, impersonation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private final PlatformAnnouncementRepository announcementRepository;
    private final FeatureFlagRepository          featureFlagRepository;
    private final UserRepository                 userRepository;
    private final ImpersonationLogRepository     impersonationLogRepository;
    private final JwtUtil                        jwtUtil;
    private final OrgAdminService                orgAdminService;

    // ── ANNOUNCEMENTS ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getAnnouncements() {
        return announcementRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(AnnouncementResponse::from).toList();
    }

    @Transactional
    public AnnouncementResponse createAnnouncement(AnnouncementDto dto) {
        PlatformAnnouncement announcement = PlatformAnnouncement.builder()
                .title(dto.title())
                .body(dto.body())
                .severity(parseSeverity(dto.severity()))
                .active(dto.active())
                .expiresAt(parseExpiresAt(dto.expiresAt()))
                .build();
        return AnnouncementResponse.from(announcementRepository.save(announcement));
    }

    @Transactional
    public AnnouncementResponse updateAnnouncement(String id, AnnouncementDto dto) {
        PlatformAnnouncement ann = announcementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable : " + id));
        ann.setTitle(dto.title());
        ann.setBody(dto.body());
        ann.setSeverity(parseSeverity(dto.severity()));
        ann.setActive(dto.active());
        ann.setExpiresAt(parseExpiresAt(dto.expiresAt()));
        return AnnouncementResponse.from(announcementRepository.save(ann));
    }

    @Transactional
    public void deleteAnnouncement(String id) {
        if (!announcementRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable : " + id);
        }
        announcementRepository.deleteById(id);
    }

    // ── FEATURE FLAGS ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> getFeatureFlags(String orgId) {
        orgAdminService.requireOrg(orgId);
        return featureFlagRepository.findByOrganisationId(orgId).stream()
                .map(FeatureFlagResponse::from).toList();
    }

    @Transactional
    public List<FeatureFlagResponse> updateFeatureFlags(String orgId, List<FeatureFlagDto> dtos) {
        orgAdminService.requireOrg(orgId);
        List<FeatureFlagResponse> result = new ArrayList<>();
        for (FeatureFlagDto dto : dtos) {
            FeatureFlag flag = featureFlagRepository
                    .findByOrganisationIdAndFeatureKey(orgId, dto.featureKey())
                    .orElseGet(() -> FeatureFlag.builder()
                            .organisationId(orgId)
                            .featureKey(dto.featureKey())
                            .build());
            flag.setEnabled(dto.enabled());
            result.add(FeatureFlagResponse.from(featureFlagRepository.save(flag)));
        }
        return result;
    }

    // ── IMPERSONATION ───────────────────────────────────────────────────────

    @Transactional
    public ImpersonateResponse generateImpersonationToken(
            String orgId, String superadminEmail, String reason, String ipAddress) {

        Organisation org = orgAdminService.requireOrg(orgId);

        User targetAdmin = userRepository.findFirstByOrganisationIdAndRole(orgId, User.UserRole.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun administrateur trouv\u00e9 pour l'organisation : " + orgId));

        String token = jwtUtil.generateImpersonationToken(
                targetAdmin.getEmail(), orgId, superadminEmail);

        ImpersonationLog logEntry = ImpersonationLog.builder()
                .superadminEmail(superadminEmail)
                .targetOrgId(orgId)
                .targetOrgName(org.getNom())
                .ipAddress(ipAddress)
                .reason(reason)
                .build();
        impersonationLogRepository.save(logEntry);

        log.warn("IMPERSONATION: {} impersonated org '{}' ({})", superadminEmail, org.getNom(), orgId);
        return new ImpersonateResponse(token, org.getNom(), org.getPays(), 30 * 60L);
    }

    @Transactional(readOnly = true)
    public Page<ImpersonationLogResponse> getImpersonationLog(int page, int size) {
        return impersonationLogRepository.findAllByOrderByStartedAtDesc(
                PageRequest.of(page, size)).map(ImpersonationLogResponse::from);
    }

    // ── HELPERS ─────────────────────────────────────────────────────────────

    private PlatformAnnouncement.Severity parseSeverity(String severity) {
        if (severity == null) return PlatformAnnouncement.Severity.INFO;
        try { return PlatformAnnouncement.Severity.valueOf(severity.toUpperCase()); }
        catch (IllegalArgumentException e) { return PlatformAnnouncement.Severity.INFO; }
    }

    private java.time.OffsetDateTime parseExpiresAt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return java.time.OffsetDateTime.parse(raw); }
        catch (java.time.format.DateTimeParseException e) {
            try {
                return java.time.LocalDate.parse(raw)
                        .atTime(23, 59, 59)
                        .atOffset(java.time.ZoneOffset.UTC);
            } catch (java.time.format.DateTimeParseException e2) { return null; }
        }
    }
}
