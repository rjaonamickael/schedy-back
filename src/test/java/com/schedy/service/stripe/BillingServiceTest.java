package com.schedy.service.stripe;

import com.schedy.config.StripeProperties;
import com.schedy.config.TenantContext;
import com.schedy.dto.response.BillingSummaryResponse;
import com.schedy.dto.response.CheckoutSessionResponse;
import com.schedy.dto.response.PortalSessionResponse;
import com.schedy.entity.Organisation;
import com.schedy.entity.PlanTemplate;
import com.schedy.entity.Subscription;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.PlanTemplateRepository;
import com.schedy.repository.SubscriptionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BillingService unit tests")
class BillingServiceTest {

    @Mock private OrganisationRepository organisationRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanTemplateRepository planTemplateRepository;
    @Mock private StripeService stripeService;
    @Mock private StripeProperties stripeProperties;
    @Mock private TenantContext tenantContext;

    @InjectMocks private BillingService billingService;

    private static final String ORG_ID        = "org-billing-001";
    private static final String CUST_ID       = "cus_test_001";
    private static final String PRICE_MONTHLY = "price_monthly_001";
    private static final String PRICE_ANNUAL  = "price_annual_001";

    @BeforeEach
    void setUp() {
        // Defensive: ensure no SecurityContext state leaks from a previously-run
        // test class in the same Surefire fork.
        SecurityContextHolder.clearContext();
        lenient().when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
        lenient().when(stripeProperties.isConfigured()).thenReturn(true);
        lenient().when(stripeProperties.successPath()).thenReturn("/billing/success");
        lenient().when(stripeProperties.cancelPath()).thenReturn("/billing/cancel");
        lenient().when(stripeProperties.portalReturnPath()).thenReturn("/billing");
        // Authenticate a fake admin so currentUserEmail() doesn't throw.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin@acme.test", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDownSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ──────────────────────────────────────────────────────────────
    // getCurrentBillingSummary()
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCurrentBillingSummary()")
    class GetCurrentBillingSummary {

        @Test
        @DisplayName("returns default TRIAL summary when no subscription exists for the org")
        void noSubscription_returnsDefaultTrialSummary() {
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());

            BillingSummaryResponse response = billingService.getCurrentBillingSummary();

            assertThat(response.status()).isEqualTo("TRIAL");
            assertThat(response.planTier()).isEqualTo("ESSENTIALS");
            assertThat(response.maxEmployees()).isEqualTo(15);
            assertThat(response.maxSites()).isEqualTo(1);
            assertThat(response.stripeConfigured()).isTrue();
        }

        @Test
        @DisplayName("returns ACTIVE status with willRenew=true when subscription is active and not cancelled")
        void activeSubscription_returnsCorrectSummary() {
            Subscription sub = Subscription.builder()
                    .organisationId(ORG_ID)
                    .planTier(Subscription.PlanTier.STARTER)
                    .status(Subscription.SubscriptionStatus.ACTIVE)
                    .maxEmployees(50)
                    .maxSites(5)
                    .cancelAtPeriodEnd(false)
                    .stripePriceId(PRICE_MONTHLY)
                    .latestInvoiceStatus("paid")
                    .build();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(sub));
            // No matching plan template — interval resolution returns null, which is fine.
            when(planTemplateRepository.findAll()).thenReturn(List.of());

            BillingSummaryResponse response = billingService.getCurrentBillingSummary();

            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.planTier()).isEqualTo("STARTER");
            assertThat(response.willRenew()).isTrue();
            assertThat(response.cancelAtPeriodEnd()).isFalse();
            assertThat(response.latestInvoiceStatus()).isEqualTo("paid");
        }

        @Test
        @DisplayName("isTrialActive=true when subscription is TRIAL and trialEndsAt is in the future")
        void trialSubscription_isTrialActiveTrue() {
            Subscription sub = Subscription.builder()
                    .organisationId(ORG_ID)
                    .planTier(Subscription.PlanTier.ESSENTIALS)
                    .status(Subscription.SubscriptionStatus.TRIAL)
                    .trialEndsAt(OffsetDateTime.now().plusDays(7))
                    .build();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(sub));
            // resolveBillingInterval() is a no-op when stripePriceId is null,
            // so findAll() is never called; lenient avoids UnnecessaryStubbingException.
            lenient().when(planTemplateRepository.findAll()).thenReturn(List.of());

            BillingSummaryResponse response = billingService.getCurrentBillingSummary();

            assertThat(response.isTrialActive()).isTrue();
        }

        @Test
        @DisplayName("isTrialActive=false when trial has already expired")
        void expiredTrial_isTrialActiveFalse() {
            Subscription sub = Subscription.builder()
                    .organisationId(ORG_ID)
                    .planTier(Subscription.PlanTier.ESSENTIALS)
                    .status(Subscription.SubscriptionStatus.TRIAL)
                    .trialEndsAt(OffsetDateTime.now().minusDays(1))
                    .build();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(sub));
            // stripePriceId is null → resolveBillingInterval() returns immediately; findAll() never called.
            lenient().when(planTemplateRepository.findAll()).thenReturn(List.of());

            BillingSummaryResponse response = billingService.getCurrentBillingSummary();

            assertThat(response.isTrialActive()).isFalse();
        }

        @Test
        @DisplayName("resolves MONTHLY interval from plan template when price id matches monthly price")
        void billingInterval_resolvedFromPlanTemplate() {
            Subscription sub = Subscription.builder()
                    .organisationId(ORG_ID)
                    .planTier(Subscription.PlanTier.ESSENTIALS)
                    .status(Subscription.SubscriptionStatus.ACTIVE)
                    .stripePriceId(PRICE_MONTHLY)
                    .build();
            PlanTemplate pt = PlanTemplate.builder()
                    .code("ESSENTIALS")
                    .stripeMonthlyPriceId(PRICE_MONTHLY)
                    .stripeAnnualPriceId(PRICE_ANNUAL)
                    .build();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(sub));
            when(planTemplateRepository.findAll()).thenReturn(List.of(pt));

            BillingSummaryResponse response = billingService.getCurrentBillingSummary();

            assertThat(response.billingInterval()).isEqualTo("MONTHLY");
        }

        @Test
        @DisplayName("resolves ANNUAL interval when price id matches annual price")
        void billingInterval_annualWhenPriceMatchesAnnual() {
            Subscription sub = Subscription.builder()
                    .organisationId(ORG_ID)
                    .planTier(Subscription.PlanTier.ESSENTIALS)
                    .status(Subscription.SubscriptionStatus.ACTIVE)
                    .stripePriceId(PRICE_ANNUAL)
                    .build();
            PlanTemplate pt = PlanTemplate.builder()
                    .code("ESSENTIALS")
                    .stripeMonthlyPriceId(PRICE_MONTHLY)
                    .stripeAnnualPriceId(PRICE_ANNUAL)
                    .build();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(sub));
            when(planTemplateRepository.findAll()).thenReturn(List.of(pt));

            BillingSummaryResponse response = billingService.getCurrentBillingSummary();

            assertThat(response.billingInterval()).isEqualTo("ANNUAL");
        }

        @Test
        @DisplayName("includes START_CHECKOUT in actions for a TRIAL subscription when Stripe is configured")
        void trialSubscription_actionsContainStartCheckout() {
            Subscription sub = Subscription.builder()
                    .organisationId(ORG_ID)
                    .planTier(Subscription.PlanTier.ESSENTIALS)
                    .status(Subscription.SubscriptionStatus.TRIAL)
                    .build();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(sub));
            // stripePriceId is null → resolveBillingInterval() short-circuits; findAll() never called.
            lenient().when(planTemplateRepository.findAll()).thenReturn(List.of());

            BillingSummaryResponse response = billingService.getCurrentBillingSummary();

            assertThat(response.availableActions()).contains("START_CHECKOUT");
        }

        @Test
        @DisplayName("includes OPEN_PORTAL in actions for an ACTIVE subscription with a Stripe sub id")
        void activeWithStripeSubId_actionsContainOpenPortal() {
            Subscription sub = Subscription.builder()
                    .organisationId(ORG_ID)
                    .planTier(Subscription.PlanTier.ESSENTIALS)
                    .status(Subscription.SubscriptionStatus.ACTIVE)
                    .stripeSubscriptionId("sub_live_001")
                    .build();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(sub));
            // stripePriceId is null → resolveBillingInterval() short-circuits; findAll() never called.
            lenient().when(planTemplateRepository.findAll()).thenReturn(List.of());

            BillingSummaryResponse response = billingService.getCurrentBillingSummary();

            assertThat(response.availableActions()).contains("OPEN_PORTAL");
            assertThat(response.availableActions()).doesNotContain("START_CHECKOUT");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // createCheckoutSession()
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createCheckoutSession()")
    class CreateCheckoutSession {

        @Test
        @DisplayName("throws BusinessRuleException when org already has an ACTIVE subscription with a Stripe id")
        void doubleCheckoutGuard_throwsBusinessRuleException() {
            // createCheckoutSession() calls organisationRepository.findById() BEFORE checking
            // the subscription guard, so the org must be resolvable to reach that guard.
            Organisation org = Organisation.builder().id(ORG_ID).nom("Acme").build();
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            Subscription active = Subscription.builder()
                    .organisationId(ORG_ID)
                    .status(Subscription.SubscriptionStatus.ACTIVE)
                    .stripeSubscriptionId("sub_existing_001")
                    .build();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(active));
            doNothing().when(stripeService).ensureConfigured();

            assertThatThrownBy(() -> billingService.createCheckoutSession("ESSENTIALS", "MONTHLY"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("abonnement actif");
        }

        @Test
        @DisplayName("creates a Stripe Customer when org has no customerId yet and persists it")
        void noCustomerId_createsAndPersistsStripeCustomer() throws StripeException {
            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom("Acme").stripeCustomerId(null).build();
            PlanTemplate plan = PlanTemplate.builder()
                    .code("ESSENTIALS")
                    .stripeMonthlyPriceId(PRICE_MONTHLY)
                    .stripeAnnualPriceId(PRICE_ANNUAL)
                    .build();
            Session stripeSession = mock(Session.class);
            when(stripeSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test");
            Customer customer = mock(Customer.class);
            when(customer.getId()).thenReturn(CUST_ID);

            doNothing().when(stripeService).ensureConfigured();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(planTemplateRepository.findByCode("ESSENTIALS")).thenReturn(Optional.of(plan));
            when(stripeService.createCustomer(ORG_ID, "Acme", "admin@acme.test")).thenReturn(customer);
            when(stripeService.createSubscriptionCheckoutSession(
                    eq(CUST_ID), eq(ORG_ID), eq(PRICE_MONTHLY), anyString(), anyString()))
                    .thenReturn(stripeSession);

            CheckoutSessionResponse response = billingService.createCheckoutSession("ESSENTIALS", "MONTHLY");

            // Customer must be persisted on the org.
            verify(organisationRepository).save(argThat(o -> CUST_ID.equals(o.getStripeCustomerId())));
            assertThat(response.checkoutUrl()).contains("checkout.stripe.com");
        }

        @Test
        @DisplayName("reuses existing customerId without calling createCustomer again")
        void existingCustomerId_doesNotCallCreateCustomer() throws StripeException {
            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom("Acme").stripeCustomerId(CUST_ID).build();
            PlanTemplate plan = PlanTemplate.builder()
                    .code("ESSENTIALS")
                    .stripeMonthlyPriceId(PRICE_MONTHLY)
                    .build();
            Session stripeSession = mock(Session.class);
            when(stripeSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_existing");

            doNothing().when(stripeService).ensureConfigured();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(planTemplateRepository.findByCode("ESSENTIALS")).thenReturn(Optional.of(plan));
            when(stripeService.createSubscriptionCheckoutSession(
                    eq(CUST_ID), eq(ORG_ID), eq(PRICE_MONTHLY), anyString(), anyString()))
                    .thenReturn(stripeSession);

            billingService.createCheckoutSession("ESSENTIALS", "MONTHLY");

            verify(stripeService, never()).createCustomer(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when org does not exist")
        void unknownOrg_throwsResourceNotFoundException() {
            doNothing().when(stripeService).ensureConfigured();
            // createCheckoutSession() calls findById() first; findByOrganisationId() is never
            // reached when the org is missing, so that stub would be unnecessary. Use lenient.
            lenient().when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> billingService.createCheckoutSession("ESSENTIALS", "MONTHLY"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws BusinessRuleException when plan code is unknown")
        void unknownPlan_throwsBusinessRuleException() {
            Organisation org = Organisation.builder().id(ORG_ID).nom("Acme").stripeCustomerId(CUST_ID).build();
            doNothing().when(stripeService).ensureConfigured();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(planTemplateRepository.findByCode("BOGUS")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> billingService.createCheckoutSession("BOGUS", "MONTHLY"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Plan inconnu");
        }

        @Test
        @DisplayName("throws SERVICE_UNAVAILABLE when plan price id is null (misconfigured plan)")
        void nullPriceId_throwsServiceUnavailable() {
            Organisation org = Organisation.builder().id(ORG_ID).nom("Acme").stripeCustomerId(CUST_ID).build();
            // Plan exists but has no Stripe price ids set yet.
            PlanTemplate plan = PlanTemplate.builder()
                    .code("ESSENTIALS")
                    .stripeMonthlyPriceId(null)
                    .stripeAnnualPriceId(null)
                    .build();
            doNothing().when(stripeService).ensureConfigured();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(planTemplateRepository.findByCode("ESSENTIALS")).thenReturn(Optional.of(plan));

            assertThatThrownBy(() -> billingService.createCheckoutSession("ESSENTIALS", "MONTHLY"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("503");
        }

        @Test
        @DisplayName("uses annual price id when billingInterval is ANNUAL")
        void annualInterval_usesAnnualPriceId() throws StripeException {
            Organisation org = Organisation.builder().id(ORG_ID).nom("Acme").stripeCustomerId(CUST_ID).build();
            PlanTemplate plan = PlanTemplate.builder()
                    .code("ESSENTIALS")
                    .stripeMonthlyPriceId(PRICE_MONTHLY)
                    .stripeAnnualPriceId(PRICE_ANNUAL)
                    .build();
            Session stripeSession = mock(Session.class);
            when(stripeSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_annual");

            doNothing().when(stripeService).ensureConfigured();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(planTemplateRepository.findByCode("ESSENTIALS")).thenReturn(Optional.of(plan));
            when(stripeService.createSubscriptionCheckoutSession(
                    eq(CUST_ID), eq(ORG_ID), eq(PRICE_ANNUAL), anyString(), anyString()))
                    .thenReturn(stripeSession);

            billingService.createCheckoutSession("ESSENTIALS", "ANNUAL");

            // Must have called createSubscriptionCheckoutSession with the ANNUAL price id.
            verify(stripeService).createSubscriptionCheckoutSession(
                    eq(CUST_ID), eq(ORG_ID), eq(PRICE_ANNUAL), anyString(), anyString());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // createPortalSession()
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPortalSession()")
    class CreatePortalSession {

        @Test
        @DisplayName("throws BusinessRuleException when org has no Stripe customer id")
        void noCustomerId_throwsBusinessRuleException() {
            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom("Acme").stripeCustomerId(null).build();
            doNothing().when(stripeService).ensureConfigured();
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            assertThatThrownBy(() -> billingService.createPortalSession())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Aucun client Stripe");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when org does not exist")
        void unknownOrg_throwsResourceNotFoundException() {
            doNothing().when(stripeService).ensureConfigured();
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> billingService.createPortalSession())
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("returns portal URL when Stripe customer exists and SDK call succeeds")
        void existingCustomer_returnsPortalUrl() throws StripeException {
            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom("Acme").stripeCustomerId(CUST_ID).build();
            com.stripe.model.billingportal.Session portalSession =
                    mock(com.stripe.model.billingportal.Session.class);
            when(portalSession.getUrl()).thenReturn("https://billing.stripe.com/session/bps_test");

            doNothing().when(stripeService).ensureConfigured();
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(stripeService.createPortalSession(eq(CUST_ID), anyString())).thenReturn(portalSession);

            PortalSessionResponse response = billingService.createPortalSession();

            assertThat(response.portalUrl()).contains("billing.stripe.com");
        }

        @Test
        @DisplayName("throws BAD_GATEWAY when Stripe SDK throws a StripeException")
        void stripeException_throwsBadGateway() throws StripeException {
            Organisation org = Organisation.builder()
                    .id(ORG_ID).nom("Acme").stripeCustomerId(CUST_ID).build();
            doNothing().when(stripeService).ensureConfigured();
            when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(stripeService.createPortalSession(anyString(), anyString()))
                    .thenThrow(mock(StripeException.class));

            assertThatThrownBy(() -> billingService.createPortalSession())
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("502");
        }
    }
}
