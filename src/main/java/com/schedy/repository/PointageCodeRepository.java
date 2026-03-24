package com.schedy.repository;

import com.schedy.entity.PointageCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PointageCodeRepository extends JpaRepository<PointageCode, String> {
    Optional<PointageCode> findBySiteIdAndActifTrue(String siteId);
    Optional<PointageCode> findByCodeAndActifTrue(String code);
    Optional<PointageCode> findByPinAndActifTrue(String pin);
    Optional<PointageCode> findByPinHashAndActifTrue(String pinHash);
    List<PointageCode> findBySiteId(String siteId);
    boolean existsByCodeAndActifTrue(String code);
    boolean existsByPinAndActifTrue(String pin);
    void deleteByActifFalse();

    // Organisation-scoped queries
    Optional<PointageCode> findBySiteIdAndActifTrueAndOrganisationId(String siteId, String organisationId);
    Optional<PointageCode> findByCodeAndOrganisationId(String code, String organisationId);
    Optional<PointageCode> findByPinAndOrganisationId(String pin, String organisationId);
}
