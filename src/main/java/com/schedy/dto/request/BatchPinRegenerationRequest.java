package com.schedy.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * V36: body for the batch PIN regeneration endpoint. Capped at 200 employees
 * per call to keep the transaction bounded and avoid PIN regeneration storms
 * on organisations with hundreds of employees.
 */
public record BatchPinRegenerationRequest(
        @NotEmpty(message = "employeIds must not be empty")
        @Size(max = 200, message = "Max 200 employes per batch")
        List<String> employeIds,
        String motif
) {}
