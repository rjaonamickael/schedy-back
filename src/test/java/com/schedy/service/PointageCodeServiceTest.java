package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.PointageCodeDto;
import com.schedy.dto.response.KioskPointageCodeResponse;
import com.schedy.entity.PointageCode;
import com.schedy.entity.PointageCode.UniteRotation;
import com.schedy.repository.EmployeRepository;
import com.schedy.repository.PointageCodeRepository;
import com.schedy.util.CryptoUtil;
import com.schedy.util.TotpEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PointageCodeService unit tests")
class PointageCodeServiceTest {

    @Mock private PointageCodeRepository pointageCodeRepository;
    @Mock private EmployeRepository employeRepository;
    @Mock private TenantContext tenantContext;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TotpEncryptionUtil pinEncryptionUtil;

    @InjectMocks private PointageCodeService pointageCodeService;

    private static final String ORG_ID  = "org-123";
    private static final String SITE_ID = "site-789";

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
        lenient().when(pinEncryptionUtil.encrypt(anyString()))
                .thenAnswer(inv -> "ENC:" + inv.getArgument(0));
    }

    private PointageCode buildValidCode() {
        return PointageCode.builder()
                .id("code-1")
                .siteId(SITE_ID)
                .code("ABCD1234")
                .pin("ENC:123456")
                .pinHash(CryptoUtil.sha256("123456"))
                .rotationValeur(1)
                .rotationUnite(UniteRotation.JOURS)
                .validFrom(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
                .validTo(OffsetDateTime.now(ZoneOffset.UTC).plusHours(23))
                .actif(true)
                .organisationId(ORG_ID)
                .build();
    }

    // =========================================================================
    // B-01 — KioskPointageCodeResponse must not expose a 'pin' field
    // =========================================================================

    @Nested
    @DisplayName("B-01 — KioskPointageCodeResponse record shape")
    class KioskResponseShape {

        @Test
        @DisplayName("KioskPointageCodeResponse has exactly 7 components and no 'pin'")
        void kioskResponse_hasSevenComponentsAndNoPinField() {
            RecordComponent[] components = KioskPointageCodeResponse.class.getRecordComponents();
            assertThat(components).hasSize(7);
            Set<String> names = Arrays.stream(components)
                    .map(RecordComponent::getName)
                    .collect(Collectors.toSet());
            assertThat(names).doesNotContain("pin");
            assertThat(names).containsExactlyInAnyOrder(
                    "siteId", "code", "rotationValeur", "rotationUnite",
                    "validFrom", "validTo", "actif");
        }

        @Test
        @DisplayName("getActiveForSitePublic() returns DTO without pin")
        void getActiveForSitePublic_returnsKioskDto() {
            PointageCode code = buildValidCode();
            when(pointageCodeRepository.findFirstBySiteIdAndActifTrueOrderByValidFromDesc(SITE_ID))
                    .thenReturn(Optional.of(code));
            KioskPointageCodeResponse response = pointageCodeService.getActiveForSitePublic(SITE_ID);
            assertThat(response).isNotNull();
            assertThat(response.siteId()).isEqualTo(SITE_ID);
            assertThat(response.code()).isEqualTo("ABCD1234");
            assertThat(response.actif()).isTrue();
        }
    }

    // =========================================================================
    // B-02 — generateUniquePin uses pinHash, not plaintext
    // =========================================================================

    @Nested
    @DisplayName("B-02 — generateUniquePin uses SHA-256 hash check")
    class GenerateUniquePinSecurity {

        @Test
        @DisplayName("calls findExistingPinHashes (batch), never existsByPinAndActifTrue")
        void generateNewCode_callsPinHashCheck() {
            when(pointageCodeRepository
                    .findFirstBySiteIdAndActifTrueAndOrganisationIdOrderByValidFromDesc(SITE_ID, ORG_ID))
                    .thenReturn(Optional.empty());
            // Batch approach: no collisions — return empty list for both checks
            when(pointageCodeRepository.findExistingPinHashes(anyList())).thenReturn(List.of());
            when(pointageCodeRepository.findExistingCodes(anyList())).thenReturn(List.of());
            when(employeRepository.findBySiteIdsContainingAndOrganisationId(SITE_ID, ORG_ID))
                    .thenReturn(List.of());
            when(pointageCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pointageCodeService.getOrCreateForSite(SITE_ID, 1, UniteRotation.JOURS);

            verify(pointageCodeRepository, atLeastOnce()).findExistingPinHashes(anyList());
            verify(pointageCodeRepository, never()).existsByPinAndActifTrue(anyString());
        }

        @Test
        @DisplayName("hashes passed to findExistingPinHashes are valid 64-char SHA-256 hex")
        void generateNewCode_passesSha256Hex() {
            when(pointageCodeRepository
                    .findFirstBySiteIdAndActifTrueAndOrganisationIdOrderByValidFromDesc(SITE_ID, ORG_ID))
                    .thenReturn(Optional.empty());
            when(pointageCodeRepository.findExistingCodes(anyList())).thenReturn(List.of());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> hashListCaptor = ArgumentCaptor.forClass(List.class);
            when(pointageCodeRepository.findExistingPinHashes(hashListCaptor.capture())).thenReturn(List.of());
            when(employeRepository.findBySiteIdsContainingAndOrganisationId(SITE_ID, ORG_ID))
                    .thenReturn(List.of());
            when(pointageCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pointageCodeService.getOrCreateForSite(SITE_ID, 1, UniteRotation.JOURS);

            List<String> capturedHashes = hashListCaptor.getValue();
            assertThat(capturedHashes).isNotEmpty();
            capturedHashes.forEach(hash ->
                    assertThat(hash).hasSize(64).matches("[0-9a-f]{64}"));
        }

        @Test
        @DisplayName("PIN stored encrypted, not plaintext")
        void generateNewCode_storesPinEncrypted() {
            when(pointageCodeRepository
                    .findFirstBySiteIdAndActifTrueAndOrganisationIdOrderByValidFromDesc(SITE_ID, ORG_ID))
                    .thenReturn(Optional.empty());
            when(pointageCodeRepository.findExistingPinHashes(anyList())).thenReturn(List.of());
            when(pointageCodeRepository.findExistingCodes(anyList())).thenReturn(List.of());
            when(employeRepository.findBySiteIdsContainingAndOrganisationId(SITE_ID, ORG_ID))
                    .thenReturn(List.of());
            ArgumentCaptor<PointageCode> captor = ArgumentCaptor.forClass(PointageCode.class);
            when(pointageCodeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            pointageCodeService.getOrCreateForSite(SITE_ID, 1, UniteRotation.JOURS);

            verify(pinEncryptionUtil, atLeastOnce()).encrypt(anyString());
            assertThat(captor.getValue().getPin()).startsWith("ENC:");
        }
    }

    // =========================================================================
    // B-10 — validateKioskAdminCodeConfig @PostConstruct guard
    // =========================================================================

    @Nested
    @DisplayName("B-10 — validateKioskAdminCodeConfig")
    class ValidateKioskAdminCodeConfig {

        private PointageCodeService buildService(String adminCode, String profile) {
            PointageCodeService service = new PointageCodeService(
                    pointageCodeRepository, employeRepository, tenantContext,
                    passwordEncoder, pinEncryptionUtil);
            ReflectionTestUtils.setField(service, "kioskAdminCode", adminCode);
            ReflectionTestUtils.setField(service, "activeProfile", profile);
            return service;
        }

        @Test
        @DisplayName("throws for empty code in prod")
        void emptyCode_prod_throws() {
            assertThrows(IllegalStateException.class,
                    () -> buildService("", "prod").validateKioskAdminCodeConfig());
        }

        @Test
        @DisplayName("does NOT throw for empty code in dev (uses default)")
        void emptyCode_dev_usesDefault() {
            buildService("", "dev").validateKioskAdminCodeConfig();
        }

        @Test
        @DisplayName("does NOT throw for 4-digit code in prod")
        void validCode_prod_accepted() {
            buildService("2580", "prod").validateKioskAdminCodeConfig();
        }
    }

    // =========================================================================
    // Pre-existing tests — updated for B-02
    // =========================================================================

    @Test
    @DisplayName("getOrCreateForSite() returns existing valid code without creating a new one")
    void getOrCreate_returnsExisting() {
        PointageCode existing = buildValidCode();
        when(pointageCodeRepository.findFirstBySiteIdAndActifTrueAndOrganisationIdOrderByValidFromDesc(SITE_ID, ORG_ID))
                .thenReturn(Optional.of(existing));
        PointageCodeDto result = pointageCodeService.getOrCreateForSite(SITE_ID, 1, UniteRotation.JOURS);
        assertThat(result.code()).isEqualTo("ABCD1234");
        verify(pointageCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateRotation rejects out-of-range value for JOURS")
    void validateRotation_rejectsOutOfRange() {
        assertThrows(com.schedy.exception.BusinessRuleException.class,
                () -> pointageCodeService.getOrCreateForSite(SITE_ID, 31, UniteRotation.JOURS));
    }
}
