package com.schedy.repository;

import com.schedy.entity.TypeConge;
import com.schedy.entity.TypeLimite;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TypeCongeRepository extends JpaRepository<TypeConge, String> {
    // Organisation-scoped queries
    Page<TypeConge> findByOrganisationId(String organisationId, Pageable pageable);
    List<TypeConge> findByOrganisationId(String organisationId);
    Optional<TypeConge> findByIdAndOrganisationId(String id, String organisationId);

    /** Used by the scheduler to credit accruals or reset annual envelopes. */
    List<TypeConge> findByTypeLimite(TypeLimite typeLimite);
    List<TypeConge> findByOrganisationIdAndTypeLimite(String organisationId, TypeLimite typeLimite);

    @Modifying @Transactional
    void deleteByOrganisationId(String organisationId);
}
