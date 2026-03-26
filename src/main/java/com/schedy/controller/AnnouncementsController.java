package com.schedy.controller;

import com.schedy.entity.PlatformAnnouncement;
import com.schedy.repository.PlatformAnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AnnouncementsController {

    private final PlatformAnnouncementRepository announcementRepository;

    @GetMapping("/active")
    public ResponseEntity<List<PlatformAnnouncement>> getActive() {
        return ResponseEntity.ok(
            announcementRepository.findActiveNonExpired(OffsetDateTime.now(ZoneOffset.UTC))
        );
    }
}
