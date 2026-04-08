package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "registration_request", indexes = {
    @Index(name = "idx_reg_request_status",     columnList = "status"),
    @Index(name = "idx_reg_request_created_at", columnList = "created_at"),
    @Index(name = "idx_reg_request_email",      columnList = "contact_email")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    private Long version;

    @Column(name = "organisation_name", nullable = false, length = 255)
    private String organisationName;

    @Column(name = "contact_name", nullable = false, length = 255)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    /** ISO alpha-3 country code: "CAN" or "MDG" */
    @Column(name = "pays", length = 3)
    private String pays;

    /** Province/state code — required when pays = CAN (e.g., "QC", "ON", "BC") */
    @Column(name = "province", length = 10)
    private String province;

    @Column(name = "adresse", length = 500)
    private String adresse;

    /** Canada: CRA Business Number (BN) */
    @Column(name = "business_number", length = 50)
    private String businessNumber;

    /** Canada: provincial business identifier (NEQ for QC, BIN for ON, etc.) */
    @Column(name = "provincial_id", length = 50)
    private String provincialId;

    /** Madagascar: Numéro d'Identification Fiscale */
    @Column(name = "nif", length = 50)
    private String nif;

    /** Madagascar: Numéro STAT (Statistique) */
    @Column(name = "stat", length = 50)
    private String stat;

    /** Desired subscription plan: "ESSENTIALS", "PRO", or "CUSTOM" */
    @Column(name = "desired_plan", length = 20)
    private String desiredPlan;

    /** Billing preference selected at sign-up: "ANNUAL" or "MONTHLY". Nullable. */
    @Column(name = "billing_cycle", length = 20)
    private String billingCycle;

    @Column(name = "certification_accepted", nullable = false)
    @Builder.Default
    private Boolean certificationAccepted = false;

    @Column(name = "certification_accepted_at")
    private OffsetDateTime certificationAcceptedAt;

    /** Estimated number of employees in the organisation */
    @Column(name = "employee_count")
    private Integer employeeCount;

    /** Free-text message from the applicant */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    /** Email of the superadmin who processed this request */
    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum RequestStatus {
        PENDING, APPROVED, REJECTED
    }
}
