package com.schedy.service;

import com.schedy.entity.User;
import com.schedy.repository.TotpRecoveryCodeRepository;
import com.schedy.repository.UserRepository;
import com.schedy.repository.UserSessionRepository;
import com.schedy.util.TotpEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TotpService unit tests (C-01)")
class TotpServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSessionRepository sessionRepository;
    @Mock private TotpRecoveryCodeRepository recoveryCodeRepository;
    @Mock private TotpEncryptionUtil encryptionUtil;

    @InjectMocks private TotpService totpService;

    private static final String EMAIL = "alice@example.com";
    private static final Long USER_ID = 42L;

    @BeforeEach
    void setUpSecurityContext() {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(EMAIL);
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Nested
    @DisplayName("TwoFaStatusResponse record")
    class TwoFaStatusResponseRecord {

        @Test
        @DisplayName("has 'enabled' and 'recoveryCodesRemaining' fields")
        void record_exposesBothFields() {
            TotpService.TwoFaStatusResponse response = new TotpService.TwoFaStatusResponse(true, 5);
            assertThat(response.enabled()).isTrue();
            assertThat(response.recoveryCodesRemaining()).isEqualTo(5);
        }

        @Test
        @DisplayName("disabled with zero codes is valid")
        void record_disabledWithZeroCodes() {
            TotpService.TwoFaStatusResponse response = new TotpService.TwoFaStatusResponse(false, 0);
            assertThat(response.enabled()).isFalse();
            assertThat(response.recoveryCodesRemaining()).isZero();
        }
    }

    @Nested
    @DisplayName("getStatus()")
    class GetStatus {

        @Test
        @DisplayName("returns correct remaining count when 2FA enabled and codes exist")
        void getStatus_2faEnabled_returnsCorrectCount() {
            User user = User.builder().id(USER_ID).email(EMAIL).totpEnabled(true).build();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(recoveryCodeRepository.countByUserIdAndUsedFalse(USER_ID)).thenReturn(6L);

            TotpService.TwoFaStatusResponse result = totpService.getStatus();

            assertThat(result.enabled()).isTrue();
            assertThat(result.recoveryCodesRemaining()).isEqualTo(6);
            verify(recoveryCodeRepository).countByUserIdAndUsedFalse(USER_ID);
        }

        @Test
        @DisplayName("returns 0 when 2FA disabled — no repo query")
        void getStatus_2faDisabled_returnsZero() {
            User user = User.builder().id(USER_ID).email(EMAIL).totpEnabled(false).build();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            TotpService.TwoFaStatusResponse result = totpService.getStatus();

            assertThat(result.enabled()).isFalse();
            assertThat(result.recoveryCodesRemaining()).isZero();
            verify(recoveryCodeRepository, never()).countByUserIdAndUsedFalse(any());
        }

        @Test
        @DisplayName("returns 0 when all codes consumed")
        void getStatus_2faEnabled_allCodesUsed() {
            User user = User.builder().id(USER_ID).email(EMAIL).totpEnabled(true).build();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(recoveryCodeRepository.countByUserIdAndUsedFalse(USER_ID)).thenReturn(0L);

            TotpService.TwoFaStatusResponse result = totpService.getStatus();

            assertThat(result.enabled()).isTrue();
            assertThat(result.recoveryCodesRemaining()).isZero();
        }
    }
}
