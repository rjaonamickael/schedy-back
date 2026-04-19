package com.schedy.dto.response;

import com.schedy.entity.Organisation;

/**
 * Admin self-service projection of the organisation. Intentionally omits
 * superadmin-only fields (status, notes, verifiedBy, verifiedAt,
 * verificationNote) and the Stripe customer id.
 *
 * <p>V48 : ajout des champs brand (logoUrl, websiteUrl, linkedinUrl) pour
 * alimenter le nouvel onglet "Organisation" du profil admin et permettre
 * le snapshot au submit temoignage.
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
        String verificationStatus,
        // V48 — brand / social presence
        String logoUrl,
        String websiteUrl,
        String linkedinUrl,
        // V50 — restauration Facebook / Instagram / X entreprise
        String facebookUrl,
        String instagramUrl,
        String twitterUrl
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
                o.getVerificationStatus(),
                o.getLogoUrl(),
                o.getWebsiteUrl(),
                o.getLinkedinUrl(),
                o.getFacebookUrl(),
                o.getInstagramUrl(),
                o.getTwitterUrl()
        );
    }
}
