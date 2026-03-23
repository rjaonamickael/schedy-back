package com.schedy.config;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.security.access.AccessDeniedException;

@Component
@RequestScope
public class TenantContext {

    private String organisationId;

    public String getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
    }

    public String requireOrganisationId() {
        if (organisationId == null || organisationId.isBlank()) {
            throw new AccessDeniedException("Organisation context required");
        }
        return organisationId;
    }
}
