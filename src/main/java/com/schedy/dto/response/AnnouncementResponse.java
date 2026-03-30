package com.schedy.dto.response;

import com.schedy.entity.PlatformAnnouncement;
import com.schedy.entity.PlatformAnnouncement.Severity;

import java.time.OffsetDateTime;

public record AnnouncementResponse(
        String id,
        String title,
        String body,
        Severity severity,
        boolean active,
        String organisationId,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {
    /**
     * Maps a PlatformAnnouncement entity to its response DTO.
     * organisationId is included here because this DTO is consumed by both regular
     * users (where it will be null for global announcements) and superadmin endpoints
     * (where it identifies org-scoped announcements).
     */
    public static AnnouncementResponse from(PlatformAnnouncement entity) {
        return new AnnouncementResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getSeverity(),
                entity.isActive(),
                entity.getOrganisationId(),
                entity.getCreatedAt(),
                entity.getExpiresAt()
        );
    }
}
