package com.schedy.repository;

import com.schedy.entity.PlanTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanTemplateRepository extends JpaRepository<PlanTemplate, String> {

    /** Returns all active and inactive templates ordered for display. */
    List<PlanTemplate> findAllByOrderBySortOrderAsc();

    Optional<PlanTemplate> findByCode(String code);

    boolean existsByCode(String code);

    /** Used to check whether another template already uses this code (excluding itself on update). */
    boolean existsByCodeAndIdNot(String code, String id);
}
