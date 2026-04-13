package com.schedy.repository;

import com.schedy.entity.PlatformAnnouncement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
@Repository
public interface PlatformAnnouncementRepository extends JpaRepository<PlatformAnnouncement, String> {
    List<PlatformAnnouncement> findByActiveTrueOrderByCreatedAtDesc();
    @Query("SELECT a FROM PlatformAnnouncement a WHERE a.active = true AND (a.expiresAt IS NULL OR a.expiresAt > :now) ORDER BY a.createdAt DESC")
    List<PlatformAnnouncement> findActiveNonExpired(@Param("now") OffsetDateTime now);
    /**
     * Returns active, non-expired announcements visible to a specific organisation.
     * Includes global announcements (organisationId IS NULL) and org-scoped ones matching orgId.
     */
    @Query("SELECT a FROM PlatformAnnouncement a WHERE a.active = true AND (a.expiresAt IS NULL OR a.expiresAt > :now) AND (a.organisationId IS NULL OR a.organisationId = :orgId) ORDER BY a.createdAt DESC")
    List<PlatformAnnouncement> findActiveNonExpiredForOrg(@Param("now") OffsetDateTime now, @Param("orgId") String orgId);
    @Modifying @Transactional
    void deleteByOrganisationId(String organisationId);
}
