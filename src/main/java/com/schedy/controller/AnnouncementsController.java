package com.schedy.controller;

import com.schedy.config.TenantContext;
import com.schedy.dto.response.AnnouncementResponse;
import com.schedy.repository.PlatformAnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AnnouncementsController {

    private final PlatformAnnouncementRepository announcementRepository;
    private final TenantContext tenantContext;

    @GetMapping("/active")
    public ResponseEntity<List<AnnouncementResponse>> getActive() {
        String orgId = tenantContext.getOrganisationId();
        if (orgId != null) {
            return ResponseEntity.ok(
                announcementRepository.findActiveNonExpiredForOrg(OffsetDateTime.now(ZoneOffset.UTC), orgId)
                        .stream()
                        .map(AnnouncementResponse::from)
                        .toList()
            );
        }
        // SuperAdmin or unauthenticated-org context: return all global announcements
        return ResponseEntity.ok(
            announcementRepository.findActiveNonExpired(OffsetDateTime.now(ZoneOffset.UTC))
                    .stream()
                    .map(AnnouncementResponse::from)
                    .toList()
        );
    }
}
