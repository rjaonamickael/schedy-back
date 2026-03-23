package com.schedy.repository;

import com.schedy.entity.BanqueConge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BanqueCongeRepository extends JpaRepository<BanqueConge, String> {
    List<BanqueConge> findByEmployeId(String employeId);
    List<BanqueConge> findByTypeCongeId(String typeCongeId);
    Optional<BanqueConge> findByEmployeIdAndTypeCongeId(String employeId, String typeCongeId);

    // Organisation-scoped queries
    Page<BanqueConge> findByOrganisationId(String organisationId, Pageable pageable);
    List<BanqueConge> findByOrganisationId(String organisationId);
    Optional<BanqueConge> findByIdAndOrganisationId(String id, String organisationId);
    List<BanqueConge> findByEmployeIdAndOrganisationId(String employeId, String organisationId);
    Optional<BanqueConge> findByEmployeIdAndTypeCongeIdAndOrganisationId(String employeId, String typeCongeId, String organisationId);
    void deleteByEmployeIdAndOrganisationId(String employeId, String organisationId);
    void deleteByTypeCongeIdAndOrganisationId(String typeCongeId, String organisationId);
}
