package com.schedy.controller;

import com.schedy.dto.response.PlanTemplateResponse;
import com.schedy.entity.PlanTemplate;
import com.schedy.repository.PlanTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Unauthenticated read-only access to active plan templates.
 * Mapped under /api/v1/public/** which is permitAll in SecurityConfig.
 *
 * Consumed by the public landing page and the registration flow to
 * display prices (monthly / annual) that are managed exclusively from
 * the superadmin console. No hardcoded price values on the frontend.
 */
@RestController
@RequestMapping("/api/v1/public/plan-templates")
@RequiredArgsConstructor
public class PublicPlanController {

    private final PlanTemplateRepository planTemplateRepository;

    /**
     * GET /api/v1/public/plan-templates
     * Returns every active plan template ordered by sortOrder, including
     * the pre-loaded features map (EAGER — safe to serialize after the
     * transactional boundary).
     */
    @GetMapping
    public ResponseEntity<List<PlanTemplateResponse>> getActivePlans() {
        List<PlanTemplateResponse> plans = planTemplateRepository
                .findAllByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(PlanTemplateResponse::from)
                .toList();
        return ResponseEntity.ok(plans);
    }
}
