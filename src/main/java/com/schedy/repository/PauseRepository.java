package com.schedy.repository;

import com.schedy.entity.Pause;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
public interface PauseRepository extends JpaRepository<Pause, String> {
    List<Pause> findByEmployeIdAndOrganisationIdAndDebutBetween(
            String employeId, String organisationId, OffsetDateTime from, OffsetDateTime to);
    List<Pause> findBySiteIdAndOrganisationIdAndDebutBetween(
            String siteId, String organisationId, OffsetDateTime from, OffsetDateTime to);
    List<Pause> findByOrganisationIdAndDebutBetween(
            String organisationId, OffsetDateTime from, OffsetDateTime to);
    Optional<Pause> findByIdAndOrganisationId(String id, String organisationId);
    @Modifying @Transactional
    void deleteByOrganisationId(String organisationId);
}
