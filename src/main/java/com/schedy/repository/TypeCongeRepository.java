package com.schedy.repository;

import com.schedy.entity.TypeConge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TypeCongeRepository extends JpaRepository<TypeConge, String> {
    List<TypeConge> findByCategorie(String categorie);

    // Organisation-scoped queries
    Page<TypeConge> findByOrganisationId(String organisationId, Pageable pageable);
    List<TypeConge> findByOrganisationId(String organisationId);
    Optional<TypeConge> findByIdAndOrganisationId(String id, String organisationId);
}
