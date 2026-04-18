package com.schedy.dto.response;

import com.schedy.entity.ImpersonationLog;

import java.time.OffsetDateTime;

public record ImpersonationLogResponse(
        String id,
        String superadminEmail,
        String targetOrgId,
        String targetOrgName,
        String reason,
        String ipAddress,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt
) {
    public static ImpersonationLogResponse from(ImpersonationLog entity) {
        return new ImpersonationLogResponse(
                entity.getId(),
                entity.getSuperadminEmail(),
                entity.getTargetOrgId(),
                entity.getTargetOrgName(),
                entity.getReason(),
                entity.getIpAddress(),
                entity.getStartedAt(),
                entity.getEndedAt()
        );
    }
}
