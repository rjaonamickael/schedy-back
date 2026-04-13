package com.schedy.repository;

import com.schedy.entity.PointageCode;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    boolean existsByPinHashAndActifTrue(String pinHash);
    @Modifying @Transactional
    void deleteByActifFalse();
    @Modifying @Transactional
    void deleteByOrganisationId(String organisationId);
    // Organisation-scoped queries
    Optional<PointageCode> findFirstBySiteIdAndActifTrueAndOrganisationIdOrderByValidFromDesc(String siteId, String organisationId);
    Optional<PointageCode> findByCodeAndOrganisationId(String code, String organisationId);
    Optional<PointageCode> findByPinAndOrganisationId(String pin, String organisationId);
    // Scheduler: fetch all active codes whose validity window has passed
    List<PointageCode> findByActifTrueAndValidToBefore(OffsetDateTime threshold);
    /**
     * Batch uniqueness check for PIN hashes — replaces per-candidate existsByPinHash queries (B-15).
     * Returns only the hashes that are already in use by an active PointageCode.
     */
    @Query("SELECT pc.pinHash FROM PointageCode pc WHERE pc.pinHash IN :hashes AND pc.actif = true")
    List<String> findExistingPinHashes(@Param("hashes") List<String> hashes);
    /**
     * Batch uniqueness check for plain codes — replaces per-candidate existsByCode queries (B-15).
     * Returns only the codes that are already in use by an active PointageCode.
     */
    @Query("SELECT pc.code FROM PointageCode pc WHERE pc.code IN :codes AND pc.actif = true")
    List<String> findExistingCodes(@Param("codes") List<String> codes);
}
