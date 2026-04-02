package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.PointageCodeDto;
import com.schedy.entity.PointageCode;
import com.schedy.entity.PointageCode.UniteRotation;
import com.schedy.repository.EmployeRepository;
import com.schedy.repository.PointageCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PointageCodeService unit tests")
class PointageCodeServiceTest {

    @Mock private PointageCodeRepository pointageCodeRepository;
    @Mock private EmployeRepository employeRepository;
    @Mock private TenantContext tenantContext;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private PointageCodeService pointageCodeService;

    private static final String ORG_ID  = "org-123";
    private static final String SITE_ID = "site-789";

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
    }

    private PointageCode buildValidCode() {
        return PointageCode.builder()
                .id("code-1")
                .siteId(SITE_ID)
                .code("ABCD1234")
                .pin("123456")
                .rotationValeur(1)
                .rotationUnite(UniteRotation.JOURS)
                .validFrom(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
                .validTo(OffsetDateTime.now(ZoneOffset.UTC).plusHours(23))
                .actif(true)
                .organisationId(ORG_ID)
                .build();
    }

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
    @DisplayName("getOrCreateForSite() generates new code when none exists")
    void getOrCreate_generatesNew() {
        when(pointageCodeRepository.findFirstBySiteIdAndActifTrueAndOrganisationIdOrderByValidFromDesc(SITE_ID, ORG_ID))
                .thenReturn(Optional.empty());
        when(pointageCodeRepository.existsByCodeAndActifTrue(anyString())).thenReturn(false);
        when(pointageCodeRepository.existsByPinAndActifTrue(anyString())).thenReturn(false);
        when(employeRepository.findBySiteIdsContainingAndOrganisationId(SITE_ID, ORG_ID))
                .thenReturn(List.of());
        ArgumentCaptor<PointageCode> captor = ArgumentCaptor.forClass(PointageCode.class);
        when(pointageCodeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        PointageCodeDto result = pointageCodeService.getOrCreateForSite(SITE_ID, 1, UniteRotation.JOURS);

        assertThat(result.siteId()).isEqualTo(SITE_ID);
        assertThat(result.rotationValeur()).isEqualTo(1);
        assertThat(result.rotationUnite()).isEqualTo("JOURS");
        assertThat(captor.getValue().getCode()).hasSize(8);
        assertThat(captor.getValue().getPin()).hasSize(6);
    }

    @Test
    @DisplayName("validateRotation rejects out-of-range value for JOURS")
    void validateRotation_rejectsOutOfRange() {
        // validateRotation throws before any repository call — no stubs needed
        // JOURS max is 30; passing 31 should throw
        org.junit.jupiter.api.Assertions.assertThrows(
                com.schedy.exception.BusinessRuleException.class,
                () -> pointageCodeService.getOrCreateForSite(SITE_ID, 31, UniteRotation.JOURS));
    }
}
