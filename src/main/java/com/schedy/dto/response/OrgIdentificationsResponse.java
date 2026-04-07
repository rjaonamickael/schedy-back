package com.schedy.dto.response;

import java.time.OffsetDateTime;

public record OrgIdentificationsResponse(
    String id,
    String nom,
    String pays,
    String province,
    String businessNumber,
    String provincialId,
    String nif,
    String stat,
    String verificationStatus,
    String verifiedBy,
    OffsetDateTime verifiedAt,
    String verificationNote
) {}
