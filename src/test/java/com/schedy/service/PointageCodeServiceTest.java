package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.PointageCodeDto;
import com.schedy.entity.PointageCode;
import com.schedy.entity.PointageCode.FrequenceRotation;
import com.schedy.entity.Site;
import com.schedy.repository.PointageCodeRepository;
import com.schedy.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PointageCodeService unit tests")
class PointageCodeServiceTest {

    @Mock private PointageCodeRepository pointageCodeRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private TenantContext tenantContext;

    @InjectMocks private PointageCodeService pointageCodeService;

    private static final String ORG_ID = "org-123";
    private static final String SITE_ID = "site-789";

    @BeforeEach
    void setUp() {
        when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
    }

    private PointageCode buildValidCode() {
        return PointageCode.builder().id("code-1").siteId(SITE_ID).code("ABCD1234").pin("123456")
                .frequence(FrequenceRotation.QUOTIDIEN)
                .validFrom(LocalDateTime.now().minusHours(1))
                .validTo(LocalDateTime.now().plusHours(23))
                .actif(true).organisationId(ORG_ID).build();
    }

    @Test
    @DisplayName("getOrCreateForSite() returns existing valid code")
    void getOrCreate_returnsExisting() {
        PointageCode existing = buildValidCode();
        when(pointageCodeRepository.findBySiteIdAndActifTrueAndOrganisationId(SITE_ID, ORG_ID))
                .thenReturn(Optional.of(existing));

        PointageCodeDto result = pointageCodeService.getOrCreateForSite(SITE_ID, FrequenceRotation.QUOTIDIEN);

        assertThat(result.code()).isEqualTo("ABCD1234");
        verify(pointageCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("getOrCreateForSite() generates new code when none exists")
    void getOrCreate_generatesNew() {
        when(pointageCodeRepository.findBySiteIdAndActifTrueAndOrganisationId(SITE_ID, ORG_ID))
                .thenReturn(Optional.empty());
        when(pointageCodeRepository.existsByCodeAndActifTrue(anyString())).thenReturn(false);
        when(pointageCodeRepository.existsByPinAndActifTrue(anyString())).thenReturn(false);
        ArgumentCaptor<PointageCode> captor = ArgumentCaptor.forClass(PointageCode.class);
        when(pointageCodeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        PointageCodeDto result = pointageCodeService.getOrCreateForSite(SITE_ID, FrequenceRotation.QUOTIDIEN);

        assertThat(result.siteId()).isEqualTo(SITE_ID);
        assertThat(captor.getValue().getCode()).hasSize(8);
        assertThat(captor.getValue().getPin()).hasSize(6);
    }
}
