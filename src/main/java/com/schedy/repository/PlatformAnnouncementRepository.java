package com.schedy.repository;

import com.schedy.entity.PlatformAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformAnnouncementRepository extends JpaRepository<PlatformAnnouncement, String> {
    List<PlatformAnnouncement> findByActiveTrueOrderByCreatedAtDesc();
}
