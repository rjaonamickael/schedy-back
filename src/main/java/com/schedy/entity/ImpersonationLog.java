package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "impersonation_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ImpersonationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "superadmin_email", nullable = false, length = 255)
    private String superadminEmail;

    @Column(name = "target_org_id", nullable = false, length = 255)
    private String targetOrgId;

    @Column(name = "target_org_name", nullable = false, length = 255)
    private String targetOrgName;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String reason;
}
