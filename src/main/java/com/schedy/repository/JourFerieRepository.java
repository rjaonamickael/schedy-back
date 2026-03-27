package com.schedy.repository;

import com.schedy.entity.JourFerie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JourFerieRepository extends JpaRepository<JourFerie, String> {
    List<JourFerie> findByRecurrent(boolean recurrent);
    List<JourFerie> findByDateBetween(LocalDate start, LocalDate end);

    // Organisation-scoped queries
    Page<JourFerie> findByOrganisationId(String organisationId, Pageable pageable);
    List<JourFerie> findByOrganisationId(String organisationId);
    Optional<JourFerie> findByIdAndOrganisationId(String id, String organisationId);
    void deleteByOrganisationId(String organisationId);
}
