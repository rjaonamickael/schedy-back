package com.schedy.dto.response;

import java.util.Map;

public record SuperAdminDashboardResponse(
    long totalOrganisations,
    long activeOrganisations,
    long suspendedOrganisations,
    long totalUsers,
    long totalEmployees,
    Map<String, Long> orgsByPlan,
    Map<String, Long> orgsByStatus
) {}
