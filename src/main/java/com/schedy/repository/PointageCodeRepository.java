package com.schedy.repository;

import com.schedy.entity.PointageCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PointageCodeRepository extends JpaRepository<PointageCode, String> {
    Optional<PointageCode> findFirstBySiteIdAndActifTrueOrderByValidFromDesc(String siteId);
    Optional<PointageCode> findFirstByCodeAndActifTrueOrderByValidFromDesc(String code);
    Optional<PointageCode> findFirstByPinHashAndActifTrueOrderByValidFromDesc(String pinHash);
    List<PointageCode> findBySiteId(String siteId);
    boolean existsByCodeAndActifTrue(String code);
    boolean existsByPinAndActifTrue(String pin);
    void deleteByActifFalse();
    void deleteByOrganisationId(String organisationId);

    // Organisation-scoped queries
    Optional<PointageCode> findFirstBySiteIdAndActifTrueAndOrganisationIdOrderByValidFromDesc(String siteId, String organisationId);
    Optional<PointageCode> findByCodeAndOrganisationId(String code, String organisationId);
    Optional<PointageCode> findByPinAndOrganisationId(String pin, String organisationId);

    // Scheduler: fetch all active codes whose validity window has passed
    List<PointageCode> findByActifTrueAndValidToBefore(OffsetDateTime threshold);
}
