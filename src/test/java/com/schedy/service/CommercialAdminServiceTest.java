package com.schedy.service;

import com.schedy.dto.request.PlanTemplateDto;
import com.schedy.dto.request.PromoCodeDto;
import com.schedy.dto.request.SubscriptionDto;
import com.schedy.dto.response.PlanTemplateResponse;
import com.schedy.dto.response.PromoCodeResponse;
import com.schedy.dto.response.SubscriptionResponse;
import com.schedy.entity.Organisation;
import com.schedy.entity.PlanTemplate;
import com.schedy.entity.PromoCode;
import com.schedy.entity.Subscription;
import com.schedy.repository.PlanTemplateRepository;
import com.schedy.repository.PromoCodeRepository;
import com.schedy.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommercialAdminService unit tests")
class CommercialAdminServiceTest {

    @Mock private SubscriptionRepository  subscriptionRepository;
    @Mock private PromoCodeRepository     promoCodeRepository;
    @Mock private PlanTemplateRepository  planTemplateRepository;
    @Mock private OrgAdminService         orgAdminService;

    @InjectMocks private CommercialAdminService commercialAdminService;

    private static final String ORG_ID = "org-comm-1";

    private Organisation stubOrg() {
        Organisation org = Organisation.builder()
                .id(ORG_ID).nom("CommCo").status("ACTIVE").pays("CAN").build();
        lenient().doReturn(org).when(orgAdminService).requireOrg(ORG_ID);
        return org;
    }

    private Subscription stubSubscription() {
        Subscription sub = Subscription.builder()
                .id("sub-c1").organisationId(ORG_ID)
                .planTier(Subscription.PlanTier.ESSENTIALS)
                .status(Subscription.SubscriptionStatus.TRIAL)
                .maxEmployees(15).maxSites(1).build();
        lenient().when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(sub));
        lenient().when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        return sub;
    }

    // ── getSubscription ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSubscription()")
    class GetSubscription {

        @Test
        @DisplayName("delegates org validation to orgAdminService then returns mapped response")
        void delegatesOrgValidation() {
            stubOrg();
            stubSubscription();

            SubscriptionResponse result = commercialAdminService.getSubscription(ORG_ID);

            verify(orgAdminService).requireOrg(ORG_ID);
            assertThat(result.organisationId()).isEqualTo(ORG_ID);
            assertThat(result.planTier()).isEqualTo(Subscription.PlanTier.ESSENTIALS);
        }

        @Test
        @DisplayName("throws 404 when subscription does not exist for org")
        void throws404WhenNoSubscription() {
            stubOrg();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commercialAdminService.getSubscription(ORG_ID))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── updateSubscription ───────────────────────────────────────────────────

    @Nested
    @DisplayName("updateSubscription()")
    class UpdateSubscription {

        @Test
        @DisplayName("updates planTier when provided in DTO")
        void updatesPlanTier() {
            stubOrg();
            stubSubscription();
            SubscriptionDto dto = new SubscriptionDto("PRO", 0, 0, null, null);

            ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
            when(subscriptionRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            commercialAdminService.updateSubscription(ORG_ID, dto);

            assertThat(cap.getValue().getPlanTier()).isEqualTo(Subscription.PlanTier.PRO);
        }

        @Test
        @DisplayName("preserves existing planTier when DTO planTier is null")
        void preservesPlanTierWhenNull() {
            stubOrg();
            stubSubscription();
            SubscriptionDto dto = new SubscriptionDto(null, 0, 0, null, null);

            ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
            when(subscriptionRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            commercialAdminService.updateSubscription(ORG_ID, dto);

            assertThat(cap.getValue().getPlanTier()).isEqualTo(Subscription.PlanTier.ESSENTIALS);
        }

        @Test
        @DisplayName("updates maxEmployees when positive value provided")
        void updatesMaxEmployeesWhenPositive() {
            stubOrg();
            stubSubscription();
            SubscriptionDto dto = new SubscriptionDto(null, 50, 0, null, null);

            ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
            when(subscriptionRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            commercialAdminService.updateSubscription(ORG_ID, dto);

            assertThat(cap.getValue().getMaxEmployees()).isEqualTo(50);
        }

        @Test
        @DisplayName("does not update maxEmployees when value is zero or negative")
        void doesNotUpdateMaxEmployeesWhenZero() {
            stubOrg();
            stubSubscription();
            SubscriptionDto dto = new SubscriptionDto(null, 0, 0, null, null);

            ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
            when(subscriptionRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            commercialAdminService.updateSubscription(ORG_ID, dto);

            assertThat(cap.getValue().getMaxEmployees()).isEqualTo(15);
        }

        @Test
        @DisplayName("sets trialEndsAt when provided in DTO")
        void setsTrialEndsAt() {
            stubOrg();
            stubSubscription();
            OffsetDateTime trial = OffsetDateTime.now().plusDays(14);
            SubscriptionDto dto = new SubscriptionDto(null, 0, 0, null, trial);

            ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
            when(subscriptionRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            commercialAdminService.updateSubscription(ORG_ID, dto);

            assertThat(cap.getValue().getTrialEndsAt()).isEqualTo(trial);
        }

        @Test
        @DisplayName("preserves existing trialEndsAt when DTO trialEndsAt is null")
        void preservesTrialEndsAtWhenNull() {
            stubOrg();
            OffsetDateTime existing = OffsetDateTime.now().plusDays(30);
            Subscription sub = Subscription.builder()
                    .id("sub-c1").organisationId(ORG_ID)
                    .planTier(Subscription.PlanTier.ESSENTIALS)
                    .maxEmployees(15).maxSites(1)
                    .trialEndsAt(existing).build();
            when(subscriptionRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(sub));

            ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
            when(subscriptionRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            commercialAdminService.updateSubscription(ORG_ID, new SubscriptionDto(null, 0, 0, null, null));

            assertThat(cap.getValue().getTrialEndsAt()).isEqualTo(existing);
        }
    }

    // ── applyPromoCode ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("applyPromoCode()")
    class ApplyPromoCode {

        @Test
        @DisplayName("sets promoCodeId on subscription and increments promo currentUses")
        void setsPromoCodeAndIncrementsUses() {
            stubOrg();
            Subscription sub = stubSubscription();
            PromoCode promo = PromoCode.builder()
                    .id("promo-1").code("BETA50").active(true)
                    .validFrom(OffsetDateTime.now().minusDays(1))
                    .currentUses(5).build();
            when(promoCodeRepository.findByCode("BETA50")).thenReturn(Optional.of(promo));
            when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Subscription> subCap = ArgumentCaptor.forClass(Subscription.class);
            when(subscriptionRepository.save(subCap.capture())).thenAnswer(inv -> inv.getArgument(0));

            commercialAdminService.applyPromoCode(ORG_ID, "BETA50");

            assertThat(subCap.getValue().getPromoCodeId()).isEqualTo("promo-1");
            ArgumentCaptor<PromoCode> promoCap = ArgumentCaptor.forClass(PromoCode.class);
            verify(promoCodeRepository).save(promoCap.capture());
            assertThat(promoCap.getValue().getCurrentUses()).isEqualTo(6);
        }

        @Test
        @DisplayName("overrides planTier when promo has planOverride")
        void overridesPlanTier() {
            stubOrg();
            stubSubscription();
            PromoCode promo = PromoCode.builder()
                    .id("promo-2").code("GOPRO").active(true)
                    .validFrom(OffsetDateTime.now().minusDays(1))
                    .planOverride("PRO").currentUses(0).build();
            when(promoCodeRepository.findByCode("GOPRO")).thenReturn(Optional.of(promo));
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Subscription> subCap = ArgumentCaptor.forClass(Subscription.class);
            when(subscriptionRepository.save(subCap.capture())).thenAnswer(inv -> inv.getArgument(0));

            commercialAdminService.applyPromoCode(ORG_ID, "GOPRO");

            assertThat(subCap.getValue().getPlanTier()).isEqualTo(Subscription.PlanTier.PRO);
            assertThat(subCap.getValue().getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("throws BAD_REQUEST when promo code does not exist")
        void throwsBadRequestWhenPromoNotFound() {
            stubOrg();
            stubSubscription();
            when(promoCodeRepository.findByCode("INVALID")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commercialAdminService.applyPromoCode(ORG_ID, "INVALID"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── findAllPromoCodes ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAllPromoCodes()")
    class FindAllPromoCodes {

        @Test
        @DisplayName("returns mapped list from repository")
        void returnsMappedList() {
            PromoCode p1 = PromoCode.builder().id("p1").code("A10").active(true).build();
            PromoCode p2 = PromoCode.builder().id("p2").code("B20").active(false).build();
            when(promoCodeRepository.findAll()).thenReturn(List.of(p1, p2));

            List<PromoCodeResponse> result = commercialAdminService.findAllPromoCodes();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).code()).isEqualTo("A10");
            assertThat(result.get(1).code()).isEqualTo("B20");
        }

        @Test
        @DisplayName("returns empty list when no promo codes exist")
        void returnsEmptyListWhenNone() {
            when(promoCodeRepository.findAll()).thenReturn(List.of());

            List<PromoCodeResponse> result = commercialAdminService.findAllPromoCodes();

            assertThat(result).isEmpty();
        }
    }

    // ── createPromoCode ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPromoCode()")
    class CreatePromoCode {

        @Test
        @DisplayName("saves promo with uppercased code")
        void savesWithUppercasedCode() {
            when(promoCodeRepository.existsByCode("SUMMER50")).thenReturn(false);
            when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeResponse result = commercialAdminService.createPromoCode(
                    new PromoCodeDto("summer50", "Summer sale", 50, null, null, null, null, null, true));

            assertThat(result.code()).isEqualTo("SUMMER50");
        }

        @Test
        @DisplayName("defaults active to true when DTO active is null")
        void defaultsActiveToTrue() {
            when(promoCodeRepository.existsByCode("NEWCODE")).thenReturn(false);
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeResponse result = commercialAdminService.createPromoCode(
                    new PromoCodeDto("NEWCODE", "desc", null, null, null, null, null, null, null));

            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("respects explicit active=false")
        void respectsExplicitFalse() {
            when(promoCodeRepository.existsByCode("INACTIVE")).thenReturn(false);
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeResponse result = commercialAdminService.createPromoCode(
                    new PromoCodeDto("INACTIVE", "d", null, null, null, null, null, null, false));

            assertThat(result.active()).isFalse();
        }

        @Test
        @DisplayName("throws CONFLICT when code already exists")
        void throwsConflictWhenDuplicate() {
            when(promoCodeRepository.existsByCode("TAKEN")).thenReturn(true);

            assertThatThrownBy(() -> commercialAdminService.createPromoCode(
                    new PromoCodeDto("TAKEN", "d", null, null, null, null, null, null, true)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("uppercases planOverride when provided")
        void uppercasesPlanOverride() {
            when(promoCodeRepository.existsByCode("PLANUP")).thenReturn(false);
            ArgumentCaptor<PromoCode> cap = ArgumentCaptor.forClass(PromoCode.class);
            when(promoCodeRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            commercialAdminService.createPromoCode(
                    new PromoCodeDto("PLANUP", "d", null, null, "pro", null, null, null, true));

            assertThat(cap.getValue().getPlanOverride()).isEqualTo("PRO");
        }
    }

    // ── updatePromoCode ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("updatePromoCode()")
    class UpdatePromoCode {

        @Test
        @DisplayName("updates description and other fields on active promo")
        void updatesActivePromo() {
            PromoCode promo = PromoCode.builder().id("p1").code("OLD").active(true).build();
            when(promoCodeRepository.findById("p1")).thenReturn(Optional.of(promo));
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeResponse result = commercialAdminService.updatePromoCode("p1",
                    new PromoCodeDto("OLD", "New desc", 30, null, null, null, null, null, true));

            assertThat(result.description()).isEqualTo("New desc");
            assertThat(result.discountPercent()).isEqualTo(30);
        }

        @Test
        @DisplayName("deactivates active promo code when active=false sent")
        void deactivatesActiveCode() {
            PromoCode promo = PromoCode.builder().id("p2").code("ACT").active(true).build();
            when(promoCodeRepository.findById("p2")).thenReturn(Optional.of(promo));
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeResponse result = commercialAdminService.updatePromoCode("p2",
                    new PromoCodeDto("ACT", "d", null, null, null, null, null, null, false));

            assertThat(result.active()).isFalse();
        }

        @Test
        @DisplayName("reactivates soft-deleted promo when active=true sent")
        void reactivatesSoftDeletedCode() {
            PromoCode promo = PromoCode.builder().id("p3").code("DEAD").active(false).build();
            when(promoCodeRepository.findById("p3")).thenReturn(Optional.of(promo));
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeResponse result = commercialAdminService.updatePromoCode("p3",
                    new PromoCodeDto("DEAD", "d", null, null, null, null, null, null, true));

            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("throws CONFLICT when updating soft-deleted code without active=true")
        void throwsConflictOnSoftDeletedCodeWithNullActive() {
            PromoCode promo = PromoCode.builder().id("p4").code("GHOST").active(false).build();
            when(promoCodeRepository.findById("p4")).thenReturn(Optional.of(promo));

            assertThatThrownBy(() -> commercialAdminService.updatePromoCode("p4",
                    new PromoCodeDto("GHOST", "d", null, null, null, null, null, null, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("throws CONFLICT when updating soft-deleted code with active=false")
        void throwsConflictOnSoftDeletedCodeWithActiveFalse() {
            PromoCode promo = PromoCode.builder().id("p5").code("DEAD2").active(false).build();
            when(promoCodeRepository.findById("p5")).thenReturn(Optional.of(promo));

            assertThatThrownBy(() -> commercialAdminService.updatePromoCode("p5",
                    new PromoCodeDto("DEAD2", "d", null, null, null, null, null, null, false)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("throws 404 when promo code id does not exist")
        void throws404WhenNotFound() {
            when(promoCodeRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commercialAdminService.updatePromoCode("missing",
                    new PromoCodeDto("X", "d", null, null, null, null, null, null, true)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── deletePromoCode (soft delete) ─────────────────────────────────────────

    @Nested
    @DisplayName("deletePromoCode() — soft delete")
    class DeletePromoCode {

        @Test
        @DisplayName("sets active=false instead of physically deleting")
        void setsActiveFalse() {
            PromoCode promo = PromoCode.builder().id("del-1").code("BYE").active(true).build();
            when(promoCodeRepository.findById("del-1")).thenReturn(Optional.of(promo));
            ArgumentCaptor<PromoCode> cap = ArgumentCaptor.forClass(PromoCode.class);
            when(promoCodeRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            commercialAdminService.deletePromoCode("del-1");

            assertThat(cap.getValue().isActive()).isFalse();
            verify(promoCodeRepository, never()).delete(any(PromoCode.class));
            verify(promoCodeRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("throws 404 when promo code does not exist")
        void throws404WhenNotFound() {
            when(promoCodeRepository.findById("gone")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commercialAdminService.deletePromoCode("gone"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── validatePromoCode ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("validatePromoCode()")
    class ValidatePromoCode {

        @Test
        @DisplayName("returns promo for a valid active non-expired non-exhausted code")
        void returnsPromoWhenValid() {
            PromoCode promo = PromoCode.builder()
                    .id("v1").code("VALID").active(true)
                    .validFrom(OffsetDateTime.now().minusDays(5))
                    .validTo(OffsetDateTime.now().plusDays(5))
                    .maxUses(10).currentUses(3).build();
            when(promoCodeRepository.findByCode("VALID")).thenReturn(Optional.of(promo));

            PromoCode result = commercialAdminService.validatePromoCode("VALID");

            assertThat(result.getCode()).isEqualTo("VALID");
        }

        @Test
        @DisplayName("looks up code in uppercase regardless of input casing")
        void lookupsInUppercase() {
            PromoCode promo = PromoCode.builder()
                    .id("v2").code("UPPER").active(true)
                    .validFrom(OffsetDateTime.now().minusDays(1)).build();
            when(promoCodeRepository.findByCode("UPPER")).thenReturn(Optional.of(promo));

            commercialAdminService.validatePromoCode("upper");

            verify(promoCodeRepository).findByCode("UPPER");
        }

        @Test
        @DisplayName("throws BAD_REQUEST when code does not exist")
        void throwsBadRequestWhenNotFound() {
            when(promoCodeRepository.findByCode("GHOST")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commercialAdminService.validatePromoCode("GHOST"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("throws BAD_REQUEST when code is inactive")
        void throwsBadRequestWhenInactive() {
            PromoCode promo = PromoCode.builder()
                    .id("i1").code("INACTIVE").active(false).build();
            when(promoCodeRepository.findByCode("INACTIVE")).thenReturn(Optional.of(promo));

            assertThatThrownBy(() -> commercialAdminService.validatePromoCode("INACTIVE"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("throws BAD_REQUEST when code has not started yet (validFrom in future)")
        void throwsBadRequestWhenNotYetValid() {
            PromoCode promo = PromoCode.builder()
                    .id("nv1").code("FUTURE").active(true)
                    .validFrom(OffsetDateTime.now().plusDays(5)).build();
            when(promoCodeRepository.findByCode("FUTURE")).thenReturn(Optional.of(promo));

            assertThatThrownBy(() -> commercialAdminService.validatePromoCode("FUTURE"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("throws BAD_REQUEST when code has expired (validTo in past)")
        void throwsBadRequestWhenExpired() {
            PromoCode promo = PromoCode.builder()
                    .id("exp1").code("EXPIRED").active(true)
                    .validFrom(OffsetDateTime.now().minusDays(30))
                    .validTo(OffsetDateTime.now().minusDays(1)).build();
            when(promoCodeRepository.findByCode("EXPIRED")).thenReturn(Optional.of(promo));

            assertThatThrownBy(() -> commercialAdminService.validatePromoCode("EXPIRED"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("throws BAD_REQUEST when code has reached maxUses")
        void throwsBadRequestWhenExhausted() {
            PromoCode promo = PromoCode.builder()
                    .id("ex1").code("FULL").active(true)
                    .validFrom(OffsetDateTime.now().minusDays(1))
                    .maxUses(10).currentUses(10).build();
            when(promoCodeRepository.findByCode("FULL")).thenReturn(Optional.of(promo));

            assertThatThrownBy(() -> commercialAdminService.validatePromoCode("FULL"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("accepts code when maxUses is null (unlimited uses)")
        void acceptsUnlimitedUsesCode() {
            PromoCode promo = PromoCode.builder()
                    .id("ul1").code("UNLIMITED").active(true)
                    .validFrom(OffsetDateTime.now().minusDays(1))
                    .maxUses(null).currentUses(9999).build();
            when(promoCodeRepository.findByCode("UNLIMITED")).thenReturn(Optional.of(promo));

            PromoCode result = commercialAdminService.validatePromoCode("UNLIMITED");

            assertThat(result.getCode()).isEqualTo("UNLIMITED");
        }
    }

    // ── findAllPlanTemplates ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findAllPlanTemplates()")
    class FindAllPlanTemplates {

        @Test
        @DisplayName("returns mapped list in sortOrder ascending")
        void returnsMappedList() {
            PlanTemplate t1 = PlanTemplate.builder()
                    .id("t1").code("ESSENTIALS").displayName("Essentials Plan")
                    .maxEmployees(15).maxSites(1).active(true).sortOrder(1)
                    .features(new HashMap<>()).build();
            PlanTemplate t2 = PlanTemplate.builder()
                    .id("t2").code("PRO").displayName("Pro Plan")
                    .maxEmployees(100).maxSites(10).active(true).sortOrder(2)
                    .features(new HashMap<>()).build();
            when(planTemplateRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(t1, t2));

            List<PlanTemplateResponse> result = commercialAdminService.findAllPlanTemplates();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).code()).isEqualTo("ESSENTIALS");
            assertThat(result.get(1).code()).isEqualTo("PRO");
        }

        @Test
        @DisplayName("returns empty list when no templates exist")
        void returnsEmptyListWhenNone() {
            when(planTemplateRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

            List<PlanTemplateResponse> result = commercialAdminService.findAllPlanTemplates();

            assertThat(result).isEmpty();
        }
    }

    // ── createPlanTemplate ────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPlanTemplate()")
    class CreatePlanTemplate {

        @Test
        @DisplayName("saves template with uppercased code")
        void savesWithUppercasedCode() {
            when(planTemplateRepository.existsByCode("FREE")).thenReturn(false);
            ArgumentCaptor<PlanTemplate> cap = ArgumentCaptor.forClass(PlanTemplate.class);
            when(planTemplateRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            PlanTemplateDto dto = new PlanTemplateDto(
                    "free", "Free Plan", null, 5, 1,
                    null, null, 0, true, 0, null);

            commercialAdminService.createPlanTemplate(dto);

            assertThat(cap.getValue().getCode()).isEqualTo("FREE");
        }

        @Test
        @DisplayName("initialises features map from DTO")
        void initialisesFeatureMap() {
            when(planTemplateRepository.existsByCode("BASIC")).thenReturn(false);
            ArgumentCaptor<PlanTemplate> cap = ArgumentCaptor.forClass(PlanTemplate.class);
            when(planTemplateRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Boolean> features = Map.of("PLANNING", true, "REPORTS", false);
            PlanTemplateDto dto = new PlanTemplateDto(
                    "BASIC", "Basic Plan", null, 10, 1,
                    null, null, 0, true, 0, features);

            commercialAdminService.createPlanTemplate(dto);

            assertThat(cap.getValue().getFeatures()).containsEntry("PLANNING", true)
                    .containsEntry("REPORTS", false);
        }

        @Test
        @DisplayName("throws CONFLICT when code already exists")
        void throwsConflictOnDuplicateCode() {
            when(planTemplateRepository.existsByCode("PRO")).thenReturn(true);

            assertThatThrownBy(() -> commercialAdminService.createPlanTemplate(
                    new PlanTemplateDto("pro", "Pro", null, 100, 10,
                            null, null, 30, true, 2, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);

            verify(planTemplateRepository, never()).save(any());
        }
    }

    // ── updatePlanTemplate ────────────────────────────────────────────────────

    @Nested
    @DisplayName("updatePlanTemplate()")
    class UpdatePlanTemplate {

        @Test
        @DisplayName("updates all fields on existing template")
        void updatesAllFields() {
            PlanTemplate existing = PlanTemplate.builder()
                    .id("t1").code("OLD").displayName("Old").maxEmployees(10).maxSites(1)
                    .active(true).sortOrder(1).features(new HashMap<>()).build();
            when(planTemplateRepository.findById("t1")).thenReturn(Optional.of(existing));
            when(planTemplateRepository.existsByCodeAndIdNot("UPDATED", "t1")).thenReturn(false);
            when(planTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlanTemplateDto dto = new PlanTemplateDto(
                    "updated", "Updated Plan", "desc", 50, 5,
                    BigDecimal.valueOf(9.99), null, 14, true, 2, Map.of("PLANNING", true));

            PlanTemplateResponse result = commercialAdminService.updatePlanTemplate("t1", dto);

            assertThat(result.code()).isEqualTo("UPDATED");
            assertThat(result.maxEmployees()).isEqualTo(50);
            assertThat(result.features()).containsEntry("PLANNING", true);
        }

        @Test
        @DisplayName("throws CONFLICT when another template uses the same code")
        void throwsConflictWhenCodeConflict() {
            PlanTemplate existing = PlanTemplate.builder()
                    .id("t2").code("MINE").displayName("Mine").maxEmployees(10).maxSites(1)
                    .active(true).sortOrder(1).features(new HashMap<>()).build();
            when(planTemplateRepository.findById("t2")).thenReturn(Optional.of(existing));
            when(planTemplateRepository.existsByCodeAndIdNot("STOLEN", "t2")).thenReturn(true);

            assertThatThrownBy(() -> commercialAdminService.updatePlanTemplate("t2",
                    new PlanTemplateDto("stolen", "Stolen", null, 10, 1,
                            null, null, 0, true, 0, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("throws 404 when template does not exist")
        void throws404WhenNotFound() {
            when(planTemplateRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commercialAdminService.updatePlanTemplate("missing",
                    new PlanTemplateDto("X", "X", null, 1, 1, null, null, 0, true, 0, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ── deletePlanTemplate ────────────────────────────────────────────────────

    @Nested
    @DisplayName("deletePlanTemplate()")
    class DeletePlanTemplate {

        @Test
        @DisplayName("physically deletes template when no active subscriptions use it")
        void deletesWhenNotInUse() {
            PlanTemplate template = PlanTemplate.builder()
                    .id("td1").code("UNUSED").displayName("Unused").maxEmployees(5).maxSites(1)
                    .active(true).sortOrder(1).features(new HashMap<>()).build();
            when(planTemplateRepository.findById("td1")).thenReturn(Optional.of(template));
            // UNUSED does not match any PlanTier enum — resolvePlanTierOrNull returns null
            // No subscription lookup is needed

            commercialAdminService.deletePlanTemplate("td1");

            verify(planTemplateRepository).delete(template);
        }

        @Test
        @DisplayName("throws CONFLICT when plan tier is in active use by subscriptions")
        void throwsConflictWhenInUse() {
            // Use a code that matches a PlanTier enum value
            PlanTemplate template = PlanTemplate.builder()
                    .id("td2").code("ESSENTIALS").displayName("Essentials").maxEmployees(15).maxSites(1)
                    .active(true).sortOrder(0).features(new HashMap<>()).build();
            when(planTemplateRepository.findById("td2")).thenReturn(Optional.of(template));
            Subscription usingSub = Subscription.builder()
                    .id("sub-using").organisationId("org-1")
                    .planTier(Subscription.PlanTier.ESSENTIALS).build();
            when(subscriptionRepository.findByPlanTier(Subscription.PlanTier.ESSENTIALS))
                    .thenReturn(List.of(usingSub));

            assertThatThrownBy(() -> commercialAdminService.deletePlanTemplate("td2"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);

            verify(planTemplateRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws 404 when template does not exist")
        void throws404WhenNotFound() {
            when(planTemplateRepository.findById("nope")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commercialAdminService.deletePlanTemplate("nope"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
