package com.schedy.repository;

import com.schedy.entity.DemandeConge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeCongeRepository extends JpaRepository<DemandeConge, String> {
    List<DemandeConge> findByEmployeId(String employeId);
    Page<DemandeConge> findByEmployeId(String employeId, Pageable pageable);
    List<DemandeConge> findByStatut(String statut);
    Page<DemandeConge> findByStatut(String statut, Pageable pageable);

    // Organisation-scoped queries
    Page<DemandeConge> findByOrganisationId(String organisationId, Pageable pageable);
    List<DemandeConge> findByOrganisationId(String organisationId);
    Optional<DemandeConge> findByIdAndOrganisationId(String id, String organisationId);
    List<DemandeConge> findByEmployeIdAndOrganisationId(String employeId, String organisationId);
    void deleteByEmployeIdAndOrganisationId(String employeId, String organisationId);
}
