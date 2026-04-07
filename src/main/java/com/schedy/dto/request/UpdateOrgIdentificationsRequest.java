package com.schedy.dto.request;

public record UpdateOrgIdentificationsRequest(
    String province,
    String businessNumber,
    String provincialId,
    String nif,
    String stat
) {}
