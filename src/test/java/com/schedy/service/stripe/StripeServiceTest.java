package com.schedy.service.stripe;

import com.schedy.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * StripeService is a thin SDK wrapper with no business logic and no DB calls.
 * Tests are deliberately minimal: they verify the delegation contract
 * (correct params forwarded to the SDK) and the two pure-logic helpers
 * (isWebhookConfigured, ensureConfigured).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StripeService unit tests")
class StripeServiceTest {

    @Mock private StripeProperties properties;

    @InjectMocks private StripeService stripeService;

    // ──────────────────────────────────────────────────────────────
    // verifyAndConstructEvent()
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyAndConstructEvent()")
    class VerifyAndConstructEvent {

        @Test
        @DisplayName("delegates to Webhook.constructEvent with the payload, sigHeader, and webhookSecret from properties")
        void delegatesToWebhookConstructEvent() throws SignatureVerificationException {
            String payload   = "{\"id\":\"evt_test\",\"type\":\"checkout.session.completed\"}";
            String sigHeader = "t=1234567890,v1=abc123";
            String secret    = "whsec_test_secret";
            Event  fakeEvent = mock(Event.class);

            when(properties.webhookSecret()).thenReturn(secret);

            try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
                webhookMock
                    .when(() -> Webhook.constructEvent(payload, sigHeader, secret))
                    .thenReturn(fakeEvent);

                Event result = stripeService.verifyAndConstructEvent(payload, sigHeader);

                assertThat(result).isSameAs(fakeEvent);
                webhookMock.verify(() -> Webhook.constructEvent(payload, sigHeader, secret));
            }
        }

        @Test
        @DisplayName("propagates SignatureVerificationException when signature is invalid")
        void invalidSignature_propagatesException() throws SignatureVerificationException {
            String payload   = "{\"id\":\"evt_bad\"}";
            String sigHeader = "t=0,v1=invalid";
            String secret    = "whsec_test_secret";

            when(properties.webhookSecret()).thenReturn(secret);

            try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
                webhookMock
                    .when(() -> Webhook.constructEvent(payload, sigHeader, secret))
                    .thenThrow(new SignatureVerificationException("Bad sig", sigHeader));

                assertThatThrownBy(() -> stripeService.verifyAndConstructEvent(payload, sigHeader))
                        .isInstanceOf(SignatureVerificationException.class)
                        .hasMessageContaining("Bad sig");
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // isWebhookConfigured() — tests the StripeProperties record helper
    // directly to ensure the prefix check is correct.
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("StripeProperties.isWebhookConfigured()")
    class IsWebhookConfigured {

        @Test
        @DisplayName("returns true when webhookSecret starts with 'whsec_'")
        void validSecret_returnsTrue() {
            StripeProperties props = new StripeProperties(
                    "sk_test_key", "whsec_valid_secret",
                    "/success", "/cancel", "/portal");

            assertThat(props.isWebhookConfigured()).isTrue();
        }

        @Test
        @DisplayName("returns false when webhookSecret is null")
        void nullSecret_returnsFalse() {
            StripeProperties props = new StripeProperties(
                    "sk_test_key", null,
                    "/success", "/cancel", "/portal");

            assertThat(props.isWebhookConfigured()).isFalse();
        }

        @Test
        @DisplayName("returns false when webhookSecret is blank")
        void blankSecret_returnsFalse() {
            StripeProperties props = new StripeProperties(
                    "sk_test_key", "   ",
                    "/success", "/cancel", "/portal");

            assertThat(props.isWebhookConfigured()).isFalse();
        }

        @Test
        @DisplayName("returns false when webhookSecret does not start with 'whsec_'")
        void wrongPrefixSecret_returnsFalse() {
            StripeProperties props = new StripeProperties(
                    "sk_test_key", "sk_test_accidental_paste",
                    "/success", "/cancel", "/portal");

            assertThat(props.isWebhookConfigured()).isFalse();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // ensureConfigured()
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ensureConfigured()")
    class EnsureConfigured {

        @Test
        @DisplayName("does not throw when Stripe is properly configured")
        void configured_doesNotThrow() {
            when(properties.isConfigured()).thenReturn(true);

            // must not throw
            stripeService.ensureConfigured();
        }

        @Test
        @DisplayName("throws 503 SERVICE_UNAVAILABLE when Stripe is not configured")
        void notConfigured_throws503() {
            when(properties.isConfigured()).thenReturn(false);

            assertThatThrownBy(() -> stripeService.ensureConfigured())
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("503");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // StripeProperties.isConfigured() — pure-logic record method
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("StripeProperties.isConfigured()")
    class IsConfigured {

        @Test
        @DisplayName("returns true for a valid sk_test_ key")
        void skTestKey_returnsTrue() {
            StripeProperties props = new StripeProperties(
                    "sk_test_validkey", "whsec_x", "/s", "/c", "/p");
            assertThat(props.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("returns true for a valid sk_live_ key")
        void skLiveKey_returnsTrue() {
            StripeProperties props = new StripeProperties(
                    "sk_live_validkey", "whsec_x", "/s", "/c", "/p");
            assertThat(props.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("returns false when secretKey is null")
        void nullKey_returnsFalse() {
            StripeProperties props = new StripeProperties(
                    null, "whsec_x", "/s", "/c", "/p");
            assertThat(props.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("returns false when secretKey is blank")
        void blankKey_returnsFalse() {
            StripeProperties props = new StripeProperties(
                    "", "whsec_x", "/s", "/c", "/p");
            assertThat(props.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("returns false when secretKey has unrecognised prefix")
        void unknownPrefix_returnsFalse() {
            StripeProperties props = new StripeProperties(
                    "pk_test_publickey", "whsec_x", "/s", "/c", "/p");
            assertThat(props.isConfigured()).isFalse();
        }
    }
}
