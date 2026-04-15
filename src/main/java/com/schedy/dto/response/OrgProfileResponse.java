package com.schedy.dto.response;

import com.schedy.entity.Organisation;

/**
 * Admin self-service projection of the organisation. Intentionally omits
 * superadmin-only fields (verificationStatus, status, notes, verifiedBy,
 * verifiedAt, verificationNote) and the Stripe customer id.
 */
public record OrgProfileResponse(
        String id,
        String nom,
        String domaine,
        String adresse,
        String telephone,
        String pays,
        String province,
        String businessNumber,
        String provincialId,
        String nif,
        String stat,
        String legalRepresentative,
        String contactEmail,
        String siret,
        String status,
        String verificationStatus
) {
    public static OrgProfileResponse from(Organisation o) {
        return new OrgProfileResponse(
                o.getId(),
                o.getNom(),
                o.getDomaine(),
                o.getAdresse(),
                o.getTelephone(),
                o.getPays(),
                o.getProvince(),
                o.getBusinessNumber(),
                o.getProvincialId(),
                o.getNif(),
                o.getStat(),
                o.getLegalRepresentative(),
                o.getContactEmail(),
                o.getSiret(),
                o.getStatus(),
                o.getVerificationStatus()
        );
    }
}
