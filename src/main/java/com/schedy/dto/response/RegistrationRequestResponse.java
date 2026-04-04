package com.schedy.dto.response;

import com.schedy.entity.RegistrationRequest;

import java.time.OffsetDateTime;

/**
 * Full projection of a RegistrationRequest for the superadmin list/detail views.
 */
public record RegistrationRequestResponse(

    String id,
    String organisationName,
    String contactName,
    String contactEmail,
    String contactPhone,
    String pays,
    String province,
    String adresse,
    String businessNumber,
    String provincialId,
    String nif,
    String stat,
    String desiredPlan,
    Integer employeeCount,
    String message,
    String billingCycle,
    String status,
    String rejectionReason,
    OffsetDateTime reviewedAt,
    String reviewedBy,
    OffsetDateTime createdAt

) {
    /** Convenience factory from entity. */
    public static RegistrationRequestResponse from(RegistrationRequest entity) {
        return new RegistrationRequestResponse(
            entity.getId(),
            entity.getOrganisationName(),
            entity.getContactName(),
            entity.getContactEmail(),
            entity.getContactPhone(),
            entity.getPays(),
            entity.getProvince(),
            entity.getAdresse(),
            entity.getBusinessNumber(),
            entity.getProvincialId(),
            entity.getNif(),
            entity.getStat(),
            entity.getDesiredPlan(),
            entity.getEmployeeCount(),
            entity.getMessage(),
            entity.getBillingCycle(),
            entity.getStatus().name(),
            entity.getRejectionReason(),
            entity.getReviewedAt(),
            entity.getReviewedBy(),
            entity.getCreatedAt()
        );
    }
}
