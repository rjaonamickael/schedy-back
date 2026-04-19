package com.schedy.service.stripe;

import com.schedy.entity.Organisation;
import com.schedy.entity.StripeEvent;
import com.schedy.entity.Subscription;
import com.schedy.entity.User;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.StripeEventRepository;
import com.schedy.repository.SubscriptionRepository;
import com.schedy.repository.UserRepository;
import com.schedy.service.EmailService;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StripeWebhookService unit tests")
class StripeWebhookServiceTest {

    @Mock private StripeEventRepository stripeEventRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private StripeService stripeService;
    // S18-BE-05 — customer notification dependencies
    @Mock private UserRepository userRepository;
    @Mock private EmailService   emailService;

    @InjectMocks private StripeWebhookService stripeWebhookService;

    private static final String ORG_ID    = "org-abc";
    private static final String CUST_ID   = "cus_test123";
    private static final String SUB_ID    = "sub_test456";
    private static final String EVENT_ID  = "evt_test001";

    // ──────────────────────────────────────────────────────────────
    // Helper: build a mocked Event with controlled deserialization.
    // The Stripe SDK's Event.getDataObjectDeserializer() returns a
    // non-final class, so Mockito can mock the full chain without any
    // reflection tricks.
    // ──────────────────────────────────────────────────────────────

    private Event buildEvent(String type, StripeObject dataObject) {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getType()).thenReturn(type);
        // lenient: unknown/no-op event types never call deserialize(), so these
        // stubs must not trigger UnnecessaryStubbingException in strict mode.
        lenient().when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        lenient().when(deserializer.getObject()).thenReturn(Optional.ofNullable(dataObject));
        return event;
    }

    /** Returns a fresh Subscription in ACTIVE state with a Stripe sub id. */
    private Subscription activeSubscription() {
        return Subscription.builder()
                .id("sub-local-1")
                .organisationId(ORG_ID)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .stripeSubscriptionId(SUB_ID)
                .planTier(Subscription.PlanTier.ESSENTIALS)
                .build();
    }

    @BeforeEach
    void defaultSaveFlush() {
        // saveAndFlush completes normally by default; individual tests
        // override this when they need to simulate a duplicate.
        lenient().when(stripeEventRepository.saveAndFlush(any(StripeEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(stripeEventRepository.save(any(StripeEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ──────────────────────────────────────────────────────────────
    // Idempotency
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency — duplicate events")
    class Idempotency {

        @Test
        @DisplayName("returns true immediately when a duplicate event PK collision is detected")
        void duplicateEvent_returnsTrue_withoutProcessing() {
            Event event = mock(Event.class);
            when(event.getId()).thenReturn(EVENT_ID);
            when(event.getType()).thenReturn("checkout.session.completed");
            when(stripeEventRepository.saveAndFlush(any(StripeEvent.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            boolean result = stripeWebhookService.handle(event);

            assertThat(result).isTrue();
            // The handler branches must never be reached on a duplicate.
            verifyNoInteractions(organisationRepository, subscriptionRepository);
        }

        @Test
        @DisplayName("processedAt is set on the record after a successful handler run")
        void successfulHandle_setsProcessedAt() {
            // Use an unknown event type so no handler side-effects fire.
            Event event = buildEvent("some.unknown.type", null);

            stripeWebhookService.handle(event);

            ArgumentCaptor<StripeEvent> captor = ArgumentCaptor.forClass(StripeEvent.class);
            // save() is called once at the end with processedAt populated.
            verify(stripeEventRepository).save(captor.capture());
            assertThat(captor.getValue().getProcessedAt()).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // checkout.session.completed
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkout.session.completed")
    class CheckoutSessionCompleted {

        @Test
        @DisplayName("creates subscription with ACTIVE status when session contains a subscription id")
        void sessionCompleted_createsActiveSubscription() {
            Session session = mock(Session.class);
            // getId() is only logged when orgId == null; it is never called on the
            // happy path where clientReferenceId resolves the org directly.
            lenient().when(session.getId()).thenReturn("cs_test_001");
            when(session.getClientReferenceId()).thenReturn(ORG_ID);
            // getCustomer() only called when org.stripeCustomerId == null; not reached here.
            lenient().when(session.getCustomer()).thenReturn(CUST_ID);
            when(session.getSubscription()).thenReturn(SUB_ID);

            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom("Acme").stripeCustomerId(CUST_ID).build();
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
            when(subscriptionRepository.save(any(Subscription.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Event event = buildEvent("checkout.session.completed", session);

            boolean result = stripeWebhookService.handle(event);

            assertThat(result).isTrue();
            ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(subCaptor.capture());
            Subscription saved = subCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
            assertThat(saved.getStripeSubscriptionId()).isEqualTo(SUB_ID);
        }

        @Test
        @DisplayName("resolves org from metadata when clientReferenceId is null")
        void sessionCompleted_resolvesOrgFromMetadata() {
            Session session = mock(Session.class);
            // getId() only reached when orgId == null after both resolution paths fail.
            lenient().when(session.getId()).thenReturn("cs_test_002");
            when(session.getClientReferenceId()).thenReturn(null);
            when(session.getMetadata()).thenReturn(java.util.Map.of("organisationId", ORG_ID));
            // getCustomer() only called when org.stripeCustomerId == null; not reached here.
            lenient().when(session.getCustomer()).thenReturn(CUST_ID);
            when(session.getSubscription()).thenReturn(SUB_ID);

            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom("Acme").stripeCustomerId(CUST_ID).build();
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
            when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            stripeWebhookService.handle(buildEvent("checkout.session.completed", session));

            verify(subscriptionRepository).save(any(Subscription.class));
        }

        @Test
        @DisplayName("updates existing subscription rather than creating a new one")
        void sessionCompleted_updatesExistingSubscription() {
            Session session = mock(Session.class);
            // getId() only logged when orgId == null; not reached on this happy path.
            lenient().when(session.getId()).thenReturn("cs_test_003");
            when(session.getClientReferenceId()).thenReturn(ORG_ID);
            // getCustomer() only called when org.stripeCustomerId == null; not reached here.
            lenient().when(session.getCustomer()).thenReturn(CUST_ID);
            when(session.getSubscription()).thenReturn(SUB_ID);

            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom("Acme").stripeCustomerId(CUST_ID).build();
            Subscription existing = Subscription.builder()
                    .id("sub-local-existing")
                    .organisationId(ORG_ID)
                    .status(Subscription.SubscriptionStatus.TRIAL)
                    .build();
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(existing));
            when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            stripeWebhookService.handle(buildEvent("checkout.session.completed", session));

            // Must not create a second subscription — the same existing object is saved.
            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo("sub-local-existing");
            assertThat(captor.getValue().getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // customer.subscription.deleted
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("customer.subscription.deleted")
    class SubscriptionDeleted {

        @Test
        @DisplayName("resets subscription to CANCELLED with ESSENTIALS defaults when deleted")
        void subscriptionDeleted_resetsToEssentialsCancelled() {
            com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
            // getId() is not called in handleSubscriptionDeleted(); only used in other handlers.
            lenient().when(stripeSub.getId()).thenReturn(SUB_ID);
            when(stripeSub.getMetadata()).thenReturn(java.util.Map.of("organisationId", ORG_ID));
            // getCustomer() is the fallback when metadata has no organisationId; not reached here.
            lenient().when(stripeSub.getCustomer()).thenReturn(CUST_ID);

            Subscription local = activeSubscription();
            local.setMaxEmployees(100);
            local.setMaxSites(10);
            local.setPlanTier(Subscription.PlanTier.PRO);

            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(local));
            when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            stripeWebhookService.handle(buildEvent("customer.subscription.deleted", stripeSub));

            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(captor.capture());
            Subscription saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(Subscription.SubscriptionStatus.CANCELLED);
            assertThat(saved.getMaxEmployees()).isEqualTo(15);
            assertThat(saved.getMaxSites()).isEqualTo(1);
            assertThat(saved.getPlanTier()).isEqualTo(Subscription.PlanTier.ESSENTIALS);
            assertThat(saved.getStripeSubscriptionId()).isNull();
            assertThat(saved.isCancelAtPeriodEnd()).isFalse();
        }

        @Test
        @DisplayName("does nothing when no local subscription exists for the org")
        void subscriptionDeleted_noLocalSub_noWrite() {
            com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
            // getId() is not called in handleSubscriptionDeleted(); lenient to avoid strict-mode failure.
            lenient().when(stripeSub.getId()).thenReturn(SUB_ID);
            when(stripeSub.getMetadata()).thenReturn(java.util.Map.of("organisationId", ORG_ID));

            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());

            stripeWebhookService.handle(buildEvent("customer.subscription.deleted", stripeSub));

            verify(subscriptionRepository, never()).save(any());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // invoice.paid
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("invoice.paid")
    class InvoicePaid {

        @Test
        @DisplayName("sets latestInvoiceStatus to 'paid' and status to ACTIVE")
        void invoicePaid_setsStatusPaid() {
            Invoice invoice = mock(Invoice.class);
            when(invoice.getCustomer()).thenReturn(CUST_ID);
            when(invoice.getPeriodEnd()).thenReturn(null);

            Subscription local = activeSubscription();
            when(organisationRepository.findByStripeCustomerId(CUST_ID))
                    .thenReturn(Optional.of(Organisation.builder().id(ORG_ID).nom("Acme").build()));
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(local));
            when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            stripeWebhookService.handle(buildEvent("invoice.paid", invoice));

            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(captor.capture());
            assertThat(captor.getValue().getLatestInvoiceStatus()).isEqualTo("paid");
            assertThat(captor.getValue().getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("does nothing when customer cannot be resolved to a local org")
        void invoicePaid_unknownCustomer_noWrite() {
            Invoice invoice = mock(Invoice.class);
            when(invoice.getCustomer()).thenReturn("cus_unknown");
            when(organisationRepository.findByStripeCustomerId("cus_unknown"))
                    .thenReturn(Optional.empty());

            stripeWebhookService.handle(buildEvent("invoice.paid", invoice));

            verify(subscriptionRepository, never()).save(any());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // invoice.payment_failed
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("invoice.payment_failed")
    class InvoicePaymentFailed {

        @Test
        @DisplayName("sets latestInvoiceStatus to 'payment_failed'")
        void invoicePaymentFailed_setsStatusPaymentFailed() {
            Invoice invoice = mock(Invoice.class);
            when(invoice.getCustomer()).thenReturn(CUST_ID);
            when(invoice.getId()).thenReturn("in_test_001");

            Subscription local = activeSubscription();
            when(organisationRepository.findByStripeCustomerId(CUST_ID))
                    .thenReturn(Optional.of(Organisation.builder().id(ORG_ID).nom("Acme").build()));
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(local));
            when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            stripeWebhookService.handle(buildEvent("invoice.payment_failed", invoice));

            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(captor.capture());
            assertThat(captor.getValue().getLatestInvoiceStatus()).isEqualTo("payment_failed");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Exception propagation
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Exception handling")
    class ExceptionHandling {

        @Test
        @DisplayName("re-throws runtime exception from handler so caller can return HTTP 500")
        void handlerException_isRethrown() {
            // Wire up a checkout session that forces a RuntimeException
            // inside the handler by making findById throw.
            Session session = mock(Session.class);
            // getId() is only reached when orgId == null; the exception here fires
            // during findById(), so this stub is defensive / never invoked.
            lenient().when(session.getId()).thenReturn("cs_test_exc");
            when(session.getClientReferenceId()).thenReturn(ORG_ID);
            when(organisationRepository.findById(ORG_ID))
                    .thenThrow(new RuntimeException("db outage"));

            Event event = buildEvent("checkout.session.completed", session);

            assertThatThrownBy(() -> stripeWebhookService.handle(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("db outage");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Unknown event type
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unknown / unhandled event types")
    class UnknownEventType {

        @Test
        @DisplayName("returns true and sets processedAt for completely unknown event types")
        void unknownType_isProcessedWithoutError() {
            Event event = buildEvent("some.totally.unknown.event", null);

            boolean result = stripeWebhookService.handle(event);

            assertThat(result).isTrue();
            // No repository interaction beyond the dedup insert + final save.
            verifyNoInteractions(organisationRepository, subscriptionRepository);
            // The final save() must be called to stamp processedAt.
            ArgumentCaptor<StripeEvent> captor = ArgumentCaptor.forClass(StripeEvent.class);
            verify(stripeEventRepository).save(captor.capture());
            assertThat(captor.getValue().getProcessedAt()).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // S18-BE-05 — customer email notifications
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S18-BE-05 — email notifications for trial / payment events")
    class EmailNotifications {

        private User adminUser() {
            // Use a real User via @Builder rather than mock(User.class). Mockito
            // cannot reliably intercept Lombok-generated getters on JPA entities
            // in strict stubbing mode (final or package-scoped in some JDKs),
            // which produced UnfinishedStubbingException in earlier runs.
            return User.builder().email("admin@acme.com").build();
        }

        @Test
        @DisplayName("trial_will_end — sends email to org admin when present")
        void trialWillEnd_sendsEmailToAdmin() {
            com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
            when(stripeSub.getMetadata()).thenReturn(java.util.Map.of("organisationId", ORG_ID));
            lenient().when(stripeSub.getCustomer()).thenReturn(CUST_ID);
            lenient().when(stripeSub.getId()).thenReturn(SUB_ID);
            long trialEpoch = java.time.Instant.parse("2026-05-01T00:00:00Z").getEpochSecond();
            when(stripeSub.getTrialEnd()).thenReturn(trialEpoch);

            when(organisationRepository.findById(ORG_ID)).thenReturn(
                    Optional.of(Organisation.builder().id(ORG_ID).nom("Acme Inc").build()));
            when(userRepository.findFirstByOrganisationIdAndRole(ORG_ID, User.UserRole.ADMIN))
                    .thenReturn(Optional.of(adminUser()));

            stripeWebhookService.handle(buildEvent("customer.subscription.trial_will_end", stripeSub));

            verify(emailService).sendStripeTrialWillEndEmail(
                    "admin@acme.com", "Acme Inc", java.time.Instant.ofEpochSecond(trialEpoch));
        }

        @Test
        @DisplayName("trial_will_end — does NOT send email when no ADMIN user in org")
        void trialWillEnd_noAdmin_noEmail() {
            com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
            when(stripeSub.getMetadata()).thenReturn(java.util.Map.of("organisationId", ORG_ID));
            lenient().when(stripeSub.getCustomer()).thenReturn(CUST_ID);
            lenient().when(stripeSub.getId()).thenReturn(SUB_ID);
            when(stripeSub.getTrialEnd()).thenReturn(null);

            when(organisationRepository.findById(ORG_ID)).thenReturn(
                    Optional.of(Organisation.builder().id(ORG_ID).nom("Acme Inc").build()));
            when(userRepository.findFirstByOrganisationIdAndRole(ORG_ID, User.UserRole.ADMIN))
                    .thenReturn(Optional.empty());

            stripeWebhookService.handle(buildEvent("customer.subscription.trial_will_end", stripeSub));

            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("payment_failed — sends email to org admin after updating subscription status")
        void paymentFailed_sendsEmailToAdmin() {
            Invoice invoice = mock(Invoice.class);
            when(invoice.getCustomer()).thenReturn(CUST_ID);
            when(invoice.getId()).thenReturn("in_failed_001");
            lenient().when(invoice.getHostedInvoiceUrl()).thenReturn("https://invoice.stripe.com/x");

            Organisation org = Organisation.builder().id(ORG_ID).nom("Acme Inc").build();
            when(organisationRepository.findByStripeCustomerId(CUST_ID)).thenReturn(Optional.of(org));
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(subscriptionRepository.findByOrganisationId(ORG_ID))
                    .thenReturn(Optional.of(activeSubscription()));
            when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.findFirstByOrganisationIdAndRole(ORG_ID, User.UserRole.ADMIN))
                    .thenReturn(Optional.of(adminUser()));

            stripeWebhookService.handle(buildEvent("invoice.payment_failed", invoice));

            verify(emailService).sendStripePaymentFailedEmail(
                    "admin@acme.com", "Acme Inc", "https://invoice.stripe.com/x");
        }

        @Test
        @DisplayName("payment_action_required — sends email to org admin")
        void paymentActionRequired_sendsEmailToAdmin() {
            Invoice invoice = mock(Invoice.class);
            when(invoice.getCustomer()).thenReturn(CUST_ID);
            when(invoice.getId()).thenReturn("in_3ds_001");
            lenient().when(invoice.getHostedInvoiceUrl()).thenReturn(null);

            Organisation org = Organisation.builder().id(ORG_ID).nom("Acme Inc").build();
            when(organisationRepository.findByStripeCustomerId(CUST_ID)).thenReturn(Optional.of(org));
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(userRepository.findFirstByOrganisationIdAndRole(ORG_ID, User.UserRole.ADMIN))
                    .thenReturn(Optional.of(adminUser()));

            stripeWebhookService.handle(buildEvent("invoice.payment_action_required", invoice));

            verify(emailService).sendStripePaymentActionRequiredEmail(
                    "admin@acme.com", "Acme Inc", null);
        }

        @Test
        @DisplayName("payment_action_required — skipped silently when no org resolvable")
        void paymentActionRequired_noOrg_noEmail() {
            Invoice invoice = mock(Invoice.class);
            when(invoice.getCustomer()).thenReturn(CUST_ID);
            when(invoice.getId()).thenReturn("in_3ds_orphan");
            when(organisationRepository.findByStripeCustomerId(CUST_ID)).thenReturn(Optional.empty());

            stripeWebhookService.handle(buildEvent("invoice.payment_action_required", invoice));

            verifyNoInteractions(emailService);
            verifyNoInteractions(userRepository);
        }
    }
}
