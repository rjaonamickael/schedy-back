package com.schedy.repository;

import com.schedy.entity.Role;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findByNom(String nom);
    List<Role> findAllByOrderByImportanceAsc();
    // Organisation-scoped queries
    Page<Role> findByOrganisationId(String organisationId, Pageable pageable);
    List<Role> findByOrganisationId(String organisationId);
    Optional<Role> findByIdAndOrganisationId(String id, String organisationId);
    List<Role> findByOrganisationIdOrderByImportanceAsc(String organisationId);
    Optional<Role> findByNomAndOrganisationId(String nom, String organisationId);
    @Modifying @Transactional
    void deleteByOrganisationId(String organisationId);
}
