package com.schedy.repository;

import com.schedy.entity.Parametres;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public interface ParametresRepository extends JpaRepository<Parametres, Long> {
    Optional<Parametres> findBySiteId(String siteId);
    // Organisation-scoped queries
    Optional<Parametres> findBySiteIdAndOrganisationId(String siteId, String organisationId);
    Optional<Parametres> findBySiteIdIsNullAndOrganisationId(String organisationId);
    @Modifying @Transactional
    void deleteByOrganisationId(String organisationId);
}
