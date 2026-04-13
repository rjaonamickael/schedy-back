package com.schedy.service;

import com.schedy.config.JwtUtil;
import com.schedy.dto.request.AnnouncementDto;
import com.schedy.dto.request.FeatureFlagDto;
import com.schedy.dto.response.AnnouncementResponse;
import com.schedy.dto.response.FeatureFlagResponse;
import com.schedy.dto.response.ImpersonateResponse;
import com.schedy.dto.response.ImpersonationLogResponse;
import com.schedy.entity.*;
import com.schedy.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformAdminService unit tests")
class PlatformAdminServiceTest {

    @Mock private PlatformAnnouncementRepository announcementRepository;
    @Mock private FeatureFlagRepository          featureFlagRepository;
    @Mock private UserRepository                 userRepository;
    @Mock private ImpersonationLogRepository     impersonationLogRepository;
    @Mock private JwtUtil                        jwtUtil;
    @Mock private OrgAdminService                orgAdminService;

    @InjectMocks private PlatformAdminService platformAdminService;

    private static final String ORG_ID         = "org-plat-1";
    private static final String SUPERADMIN_EMAIL = "superadmin@schedy.io";

    private Organisation stubOrg() {
        Organisation org = Organisation.builder()
                .id(ORG_ID).nom("PlatCo").status("ACTIVE").pays("CAN").build();
        lenient().doReturn(org).when(orgAdminService).requireOrg(ORG_ID);
        return org;
    }

    // ── getAnnouncements ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAnnouncements()")
    class GetAnnouncements {

        @Test
        @DisplayName("returns announcements sorted desc by createdAt")
        void returnsAnnouncementsInDescOrder() {
            PlatformAnnouncement a1 = PlatformAnnouncement.builder()
                    .id("ann-1").title("Info").body("Body A")
                    .severity(PlatformAnnouncement.Severity.INFO).active(true).build();
            PlatformAnnouncement a2 = PlatformAnnouncement.builder()
                    .id("ann-2").title("Warning").body("Body B")
                    .severity(PlatformAnnouncement.Severity.WARNING).active(true).build();
            when(announcementRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                    .thenReturn(List.of(a2, a1));

            List<AnnouncementResponse> result = platformAdminService.getAnnouncements();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo("ann-2");
            assertThat(result.get(1).id()).isEqualTo("ann-1");
        }

        @Test
        @DisplayName("returns empty list when no announcements exist")
        void returnsEmptyWhenNone() {
            when(announcementRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                    .thenReturn(List.of());

            List<AnnouncementResponse> result = platformAdminService.getAnnouncements();

            assertThat(result).isEmpty();
        }
    }

    // ── createAnnouncement ────────────────────────────────────────────────────

    @Nested
    @DisplayName("createAnnouncement()")
    class CreateAnnouncement {

        @Test
        @DisplayName("saves announcement with provided title, body and severity")
        void savesAnnouncementWithCorrectFields() {
            ArgumentCaptor<PlatformAnnouncement> cap =
                    ArgumentCaptor.forClass(PlatformAnnouncement.class);
            when(announcementRepository.save(cap.capture())).thenAnswer(inv -> {
                PlatformAnnouncement a = inv.getArgument(0);
                return PlatformAnnouncement.builder()
                        .id("new-1").title(a.getTitle()).body(a.getBody())
                        .severity(a.getSeverity()).active(a.isActive()).build();
            });

            AnnouncementDto dto = new AnnouncementDto("Maintenance", "Server down at 02:00", "WARNING", true, null);

            AnnouncementResponse result = platformAdminService.createAnnouncement(dto);

            assertThat(result.id()).isEqualTo("new-1");
            assertThat(result.title()).isEqualTo("Maintenance");
            assertThat(result.severity()).isEqualTo(PlatformAnnouncement.Severity.WARNING);

            PlatformAnnouncement saved = cap.getValue();
            assertThat(saved.getBody()).isEqualTo("Server down at 02:00");
            assertThat(saved.isActive()).isTrue();
        }

        @Test
        @DisplayName("defaults severity to INFO when null or unrecognised string")
        void defaultsSeverityToInfo() {
            ArgumentCaptor<PlatformAnnouncement> cap =
                    ArgumentCaptor.forClass(PlatformAnnouncement.class);
            when(announcementRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            platformAdminService.createAnnouncement(
                    new AnnouncementDto("T", "B", null, true, null));

            assertThat(cap.getValue().getSeverity()).isEqualTo(PlatformAnnouncement.Severity.INFO);
        }

        @Test
        @DisplayName("defaults severity to INFO for unrecognised severity string")
        void defaultsSeverityForUnrecognisedString() {
            ArgumentCaptor<PlatformAnnouncement> cap =
                    ArgumentCaptor.forClass(PlatformAnnouncement.class);
            when(announcementRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            platformAdminService.createAnnouncement(
                    new AnnouncementDto("T", "B", "UNKNOWN_LEVEL", true, null));

            assertThat(cap.getValue().getSeverity()).isEqualTo(PlatformAnnouncement.Severity.INFO);
        }

        @Test
        @DisplayName("parses ISO offset datetime string in expiresAt")
        void parsesIsoOffsetDateTimeInExpiresAt() {
            ArgumentCaptor<PlatformAnnouncement> cap =
                    ArgumentCaptor.forClass(PlatformAnnouncement.class);
            when(announcementRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            String expiresAt = OffsetDateTime.now().plusDays(7).toString();
            platformAdminService.createAnnouncement(
                    new AnnouncementDto("T", "B", "INFO", true, expiresAt));

            assertThat(cap.getValue().getExpiresAt()).isNotNull();
        }

        @Test
        @DisplayName("parses local date string (yyyy-MM-dd) in expiresAt as end of day UTC")
        void parsesLocalDateInExpiresAt() {
            ArgumentCaptor<PlatformAnnouncement> cap =
                    ArgumentCaptor.forClass(PlatformAnnouncement.class);
            when(announcementRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            platformAdminService.createAnnouncement(
                    new AnnouncementDto("T", "B", "INFO", true, "2030-12-31"));

            OffsetDateTime saved = cap.getValue().getExpiresAt();
            assertThat(saved).isNotNull();
            assertThat(saved.getHour()).isEqualTo(23);
            assertThat(saved.getMinute()).isEqualTo(59);
        }

        @Test
        @DisplayName("sets expiresAt to null when expiresAt string is blank")
        void setsExpiresAtNullWhenBlank() {
            ArgumentCaptor<PlatformAnnouncement> cap =
                    ArgumentCaptor.forClass(PlatformAnnouncement.class);
            when(announcementRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            platformAdminService.createAnnouncement(
                    new AnnouncementDto("T", "B", "INFO", true, "  "));

            assertThat(cap.getValue().getExpiresAt()).isNull();
        }
    }

    // ── updateAnnouncement ────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateAnnouncement()")
    class UpdateAnnouncement {

        @Test
        @DisplayName("updates title, body and severity on existing announcement")
        void updatesExistingAnnouncement() {
            PlatformAnnouncement existing = PlatformAnnouncement.builder()
                    .id("ann-u1").title("Old").body("Old body")
                    .severity(PlatformAnnouncement.Severity.INFO).active(true).build();
            when(announcementRepository.findById("ann-u1")).thenReturn(Optional.of(existing));
            when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AnnouncementResponse result = platformAdminService.updateAnnouncement("ann-u1",
                    new AnnouncementDto("New title", "New body", "CRITICAL", false, null));

            assertThat(result.title()).isEqualTo("New title");
            assertThat(result.severity()).isEqualTo(PlatformAnnouncement.Severity.CRITICAL);
            assertThat(result.active()).isFalse();
        }

        @Test
        @DisplayName("throws 404 when announcement does not exist")
        void throws404WhenNotFound() {
            when(announcementRepository.findById("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> platformAdminService.updateAnnouncement("ghost",
                    new AnnouncementDto("T", "B", "INFO", true, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── deleteAnnouncement ────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteAnnouncement()")
    class DeleteAnnouncement {

        @Test
        @DisplayName("delegates to deleteById when announcement exists")
        void deletesById() {
            when(announcementRepository.existsById("ann-d1")).thenReturn(true);

            platformAdminService.deleteAnnouncement("ann-d1");

            verify(announcementRepository).deleteById("ann-d1");
        }

        @Test
        @DisplayName("throws 404 when announcement does not exist")
        void throws404WhenNotFound() {
            when(announcementRepository.existsById("ghost")).thenReturn(false);

            assertThatThrownBy(() -> platformAdminService.deleteAnnouncement("ghost"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);

            verify(announcementRepository, never()).deleteById(any());
        }
    }

    // ── getFeatureFlags ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFeatureFlags()")
    class GetFeatureFlags {

        @Test
        @DisplayName("validates org via orgAdminService then returns flag list")
        void validatesOrgAndReturnsFlags() {
            stubOrg();
            FeatureFlag ff = FeatureFlag.builder()
                    .id("ff-1").organisationId(ORG_ID).featureKey("PLANNING_EXPORT").enabled(true).build();
            when(featureFlagRepository.findByOrganisationId(ORG_ID)).thenReturn(List.of(ff));

            List<FeatureFlagResponse> result = platformAdminService.getFeatureFlags(ORG_ID);

            verify(orgAdminService).requireOrg(ORG_ID);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).featureKey()).isEqualTo("PLANNING_EXPORT");
            assertThat(result.get(0).enabled()).isTrue();
        }

        @Test
        @DisplayName("returns empty list when org has no feature flags configured")
        void returnsEmptyListWhenNone() {
            stubOrg();
            when(featureFlagRepository.findByOrganisationId(ORG_ID)).thenReturn(List.of());

            List<FeatureFlagResponse> result = platformAdminService.getFeatureFlags(ORG_ID);

            assertThat(result).isEmpty();
        }
    }

    // ── updateFeatureFlags ────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateFeatureFlags()")
    class UpdateFeatureFlags {

        @Test
        @DisplayName("creates new FeatureFlag when none exists for the key")
        void createsNewFlag() {
            stubOrg();
            when(featureFlagRepository.findByOrganisationIdAndFeatureKey(ORG_ID, "NEW_FEATURE"))
                    .thenReturn(Optional.empty());
            ArgumentCaptor<FeatureFlag> cap = ArgumentCaptor.forClass(FeatureFlag.class);
            when(featureFlagRepository.save(cap.capture())).thenAnswer(inv -> {
                FeatureFlag f = inv.getArgument(0);
                return FeatureFlag.builder()
                        .id("ff-new").organisationId(f.getOrganisationId())
                        .featureKey(f.getFeatureKey()).enabled(f.isEnabled()).build();
            });

            List<FeatureFlagResponse> result = platformAdminService.updateFeatureFlags(ORG_ID,
                    List.of(new FeatureFlagDto("NEW_FEATURE", true)));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).featureKey()).isEqualTo("NEW_FEATURE");
            assertThat(result.get(0).enabled()).isTrue();

            FeatureFlag saved = cap.getValue();
            assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
            assertThat(saved.getFeatureKey()).isEqualTo("NEW_FEATURE");
        }

        @Test
        @DisplayName("updates existing FeatureFlag when it already exists for the key")
        void updatesExistingFlag() {
            stubOrg();
            FeatureFlag existing = FeatureFlag.builder()
                    .id("ff-ex").organisationId(ORG_ID).featureKey("REPORTS").enabled(false).build();
            when(featureFlagRepository.findByOrganisationIdAndFeatureKey(ORG_ID, "REPORTS"))
                    .thenReturn(Optional.of(existing));
            when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(inv -> inv.getArgument(0));

            List<FeatureFlagResponse> result = platformAdminService.updateFeatureFlags(ORG_ID,
                    List.of(new FeatureFlagDto("REPORTS", true)));

            assertThat(result.get(0).id()).isEqualTo("ff-ex");
            assertThat(result.get(0).enabled()).isTrue();
        }

        @Test
        @DisplayName("processes multiple flags in a single call")
        void processesMultipleFlags() {
            stubOrg();
            when(featureFlagRepository.findByOrganisationIdAndFeatureKey(eq(ORG_ID), any()))
                    .thenReturn(Optional.empty());
            when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(inv -> {
                FeatureFlag f = inv.getArgument(0);
                return FeatureFlag.builder()
                        .id("ff-gen").organisationId(f.getOrganisationId())
                        .featureKey(f.getFeatureKey()).enabled(f.isEnabled()).build();
            });

            List<FeatureFlagDto> dtos = List.of(
                    new FeatureFlagDto("FEATURE_A", true),
                    new FeatureFlagDto("FEATURE_B", false));

            List<FeatureFlagResponse> result = platformAdminService.updateFeatureFlags(ORG_ID, dtos);

            assertThat(result).hasSize(2);
            verify(featureFlagRepository, times(2)).save(any(FeatureFlag.class));
        }

        @Test
        @DisplayName("validates org before processing flags")
        void validatesOrgFirst() {
            stubOrg();
            when(featureFlagRepository.findByOrganisationIdAndFeatureKey(any(), any()))
                    .thenReturn(Optional.empty());
            when(featureFlagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            platformAdminService.updateFeatureFlags(ORG_ID, List.of(new FeatureFlagDto("F", true)));

            verify(orgAdminService).requireOrg(ORG_ID);
        }
    }

    // ── generateImpersonationToken ────────────────────────────────────────────

    @Nested
    @DisplayName("generateImpersonationToken()")
    class GenerateImpersonationToken {

        @Test
        @DisplayName("returns ImpersonateResponse with token, org name, pays and 30-min expiry")
        void returnsImpersonateResponse() {
            Organisation org = stubOrg();
            User admin = User.builder()
                    .id(1L).email("admin@platco.com")
                    .organisationId(ORG_ID).role(User.UserRole.ADMIN).password("h").build();
            when(userRepository.findFirstByOrganisationIdAndRole(ORG_ID, User.UserRole.ADMIN))
                    .thenReturn(Optional.of(admin));
            when(jwtUtil.generateImpersonationToken("admin@platco.com", ORG_ID, SUPERADMIN_EMAIL))
                    .thenReturn("impersonation.jwt.token");
            when(impersonationLogRepository.save(any(ImpersonationLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ImpersonateResponse result = platformAdminService.generateImpersonationToken(
                    ORG_ID, SUPERADMIN_EMAIL, "Support ticket #42", "10.0.0.1");

            assertThat(result.accessToken()).isEqualTo("impersonation.jwt.token");
            assertThat(result.organisationName()).isEqualTo("PlatCo");
            assertThat(result.pays()).isEqualTo("CAN");
            assertThat(result.expiresIn()).isEqualTo(30 * 60L);
        }

        @Test
        @DisplayName("saves ImpersonationLog with superadmin email, reason and IP")
        void savesImpersonationLog() {
            stubOrg();
            User admin = User.builder()
                    .id(2L).email("a@b.com").organisationId(ORG_ID)
                    .role(User.UserRole.ADMIN).password("h").build();
            when(userRepository.findFirstByOrganisationIdAndRole(ORG_ID, User.UserRole.ADMIN))
                    .thenReturn(Optional.of(admin));
            when(jwtUtil.generateImpersonationToken(any(), any(), any())).thenReturn("tok");
            ArgumentCaptor<ImpersonationLog> logCap = ArgumentCaptor.forClass(ImpersonationLog.class);
            when(impersonationLogRepository.save(logCap.capture())).thenAnswer(inv -> inv.getArgument(0));

            platformAdminService.generateImpersonationToken(
                    ORG_ID, SUPERADMIN_EMAIL, "Reason", "192.168.1.5");

            ImpersonationLog log = logCap.getValue();
            assertThat(log.getSuperadminEmail()).isEqualTo(SUPERADMIN_EMAIL);
            assertThat(log.getReason()).isEqualTo("Reason");
            assertThat(log.getIpAddress()).isEqualTo("192.168.1.5");
            assertThat(log.getTargetOrgId()).isEqualTo(ORG_ID);
            assertThat(log.getTargetOrgName()).isEqualTo("PlatCo");
        }

        @Test
        @DisplayName("delegates JWT generation to jwtUtil with correct arguments")
        void delegatesJwtGeneration() {
            stubOrg();
            User admin = User.builder()
                    .id(3L).email("admin@org.com").organisationId(ORG_ID)
                    .role(User.UserRole.ADMIN).password("h").build();
            when(userRepository.findFirstByOrganisationIdAndRole(ORG_ID, User.UserRole.ADMIN))
                    .thenReturn(Optional.of(admin));
            when(jwtUtil.generateImpersonationToken(any(), any(), any())).thenReturn("tok");
            when(impersonationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            platformAdminService.generateImpersonationToken(
                    ORG_ID, SUPERADMIN_EMAIL, "reason", "1.2.3.4");

            verify(jwtUtil).generateImpersonationToken("admin@org.com", ORG_ID, SUPERADMIN_EMAIL);
        }

        @Test
        @DisplayName("throws 404 when no admin user exists for the organisation")
        void throws404WhenNoAdmin() {
            stubOrg();
            when(userRepository.findFirstByOrganisationIdAndRole(ORG_ID, User.UserRole.ADMIN))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> platformAdminService.generateImpersonationToken(
                    ORG_ID, SUPERADMIN_EMAIL, "reason", "1.2.3.4"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);

            verify(jwtUtil, never()).generateImpersonationToken(any(), any(), any());
            verify(impersonationLogRepository, never()).save(any());
        }
    }

    // ── getImpersonationLog ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getImpersonationLog()")
    class GetImpersonationLog {

        @Test
        @DisplayName("returns paginated impersonation log in descending startedAt order")
        void returnsPaginatedLog() {
            ImpersonationLog entry = ImpersonationLog.builder()
                    .id("log-1").superadminEmail(SUPERADMIN_EMAIL)
                    .targetOrgId(ORG_ID).targetOrgName("PlatCo")
                    .reason("Test").ipAddress("1.1.1.1").build();
            Page<ImpersonationLog> page = new PageImpl<>(
                    List.of(entry), PageRequest.of(0, 10), 1);
            when(impersonationLogRepository.findAllByOrderByStartedAtDesc(any()))
                    .thenReturn(page);

            Page<ImpersonationLogResponse> result = platformAdminService.getImpersonationLog(0, 10);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).superadminEmail()).isEqualTo(SUPERADMIN_EMAIL);
        }

        @Test
        @DisplayName("passes correct page and size to repository")
        void passesCorrectPagination() {
            when(impersonationLogRepository.findAllByOrderByStartedAtDesc(any()))
                    .thenReturn(Page.empty());

            platformAdminService.getImpersonationLog(2, 25);

            ArgumentCaptor<PageRequest> cap = ArgumentCaptor.forClass(PageRequest.class);
            verify(impersonationLogRepository).findAllByOrderByStartedAtDesc(cap.capture());
            PageRequest captured = cap.getValue();
            assertThat(captured.getPageNumber()).isEqualTo(2);
            assertThat(captured.getPageSize()).isEqualTo(25);
        }

        @Test
        @DisplayName("returns empty page when no log entries exist")
        void returnsEmptyPageWhenNone() {
            when(impersonationLogRepository.findAllByOrderByStartedAtDesc(any()))
                    .thenReturn(Page.empty());

            Page<ImpersonationLogResponse> result = platformAdminService.getImpersonationLog(0, 20);

            assertThat(result).isEmpty();
        }
    }
}
