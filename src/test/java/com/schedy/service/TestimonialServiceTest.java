package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.request.TestimonialDto;
import com.schedy.dto.response.TestimonialResponse;
import com.schedy.entity.Organisation;
import com.schedy.entity.Testimonial;
import com.schedy.entity.Testimonial.TestimonialStatus;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.SubscriptionRepository;
import com.schedy.repository.TestimonialRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TestimonialService}.
 *
 * <p>Focus areas (added in this session):
 * <ul>
 *   <li>Trim guarantees on quote + quoteTitle + author* fields in submit() and update().</li>
 *   <li>Delete flow: ownership enforcement (silent 404) and best-effort R2 logo cleanup.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TestimonialService unit tests")
class TestimonialServiceTest {

    @Mock private TestimonialRepository testimonialRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private com.schedy.repository.UserRepository userRepository;
    @Mock private TenantContext tenantContext;
    @Mock private R2StorageService r2StorageService;

    @InjectMocks private TestimonialService testimonialService;

    private static final String ORG_ID = "org-abc";
    private static final String OTHER_ORG_ID = "org-xyz";
    private static final String TESTIMONIAL_ID = "tst-123";

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
        lenient().when(organisationRepository.findById(ORG_ID))
                .thenReturn(Optional.of(Organisation.builder().id(ORG_ID).nom("Acme").pays("CA").build()));
        lenient().when(subscriptionRepository.findByOrganisationId(ORG_ID))
                .thenReturn(Optional.empty());
        // R2 validation is already unit-tested on its own — just let it accept any URL here
        // so submit() passes the logoUrl guard when we happen to provide one.
        lenient().when(r2StorageService.isOwnedUrl(any())).thenReturn(true);
        lenient().when(testimonialRepository.save(any(Testimonial.class)))
                .thenAnswer(inv -> {
                    Testimonial saved = inv.getArgument(0);
                    if (saved.getId() == null) saved.setId(TESTIMONIAL_ID);
                    if (saved.getCreatedAt() == null) saved.setCreatedAt(OffsetDateTime.now());
                    return saved;
                });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TestimonialDto buildDto(String authorName, String authorRole, String authorCity,
                                    String quote, String quoteTitle) {
        // V48 — DTO trimme : logoUrl/linkedin/website/fb/ig/twitter retires
        // (derives serveur via snapshot Organisation + User, ou supprimes).
        return new TestimonialDto(
                authorName, authorRole, authorCity, quote, quoteTitle,
                5, "fr",
                null, null, null
        );
    }

    private Testimonial buildExistingEntity(String orgId, String logoUrl) {
        return Testimonial.builder()
                .id(TESTIMONIAL_ID)
                .organisationId(orgId)
                .authorName("Alice")
                .authorRole("Cook")
                .authorCity("Montreal")
                .quote("Great app")
                .quoteTitle("Amazing")
                .stars(5)
                .language("fr")
                .logoUrl(logoUrl)
                .status(TestimonialStatus.PENDING)
                .displayOrder(0)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    // =========================================================================
    // submit()
    // =========================================================================

    @Nested
    @DisplayName("submit()")
    class SubmitTests {

        @Test
        @DisplayName("trims quote, quoteTitle and author fields before persisting")
        void submit_trimsAllStringFields() {
            when(testimonialRepository.findByOrganisationIdOrderByCreatedAtDesc(ORG_ID))
                    .thenReturn(List.of());

            TestimonialDto dto = buildDto(
                    "  Alice Martin  ",
                    "  Chef de cuisine  ",
                    "  Montréal  ",
                    "\n\n  Schedy nous fait gagner 5h/semaine.\n  ",
                    "  Un gain de temps énorme  "
            );

            testimonialService.submit(dto);

            ArgumentCaptor<Testimonial> captor = ArgumentCaptor.forClass(Testimonial.class);
            verify(testimonialRepository).save(captor.capture());
            Testimonial saved = captor.getValue();

            assertThat(saved.getAuthorName()).isEqualTo("Alice Martin");
            assertThat(saved.getAuthorRole()).isEqualTo("Chef de cuisine");
            assertThat(saved.getAuthorCity()).isEqualTo("Montréal");
            assertThat(saved.getQuote()).isEqualTo("Schedy nous fait gagner 5h/semaine.");
            assertThat(saved.getQuoteTitle()).isEqualTo("Un gain de temps énorme");
            assertThat(saved.getStatus()).isEqualTo(TestimonialStatus.PENDING);
        }

        @Test
        @DisplayName("blank optional fields are stored as null, not empty strings")
        void submit_blankOptionalFieldsBecomeNull() {
            when(testimonialRepository.findByOrganisationIdOrderByCreatedAtDesc(ORG_ID))
                    .thenReturn(List.of());

            TestimonialDto dto = buildDto(
                    "Alice",
                    "Cook",
                    "   ",    // whitespace-only city
                    "Great app",
                    "   \n\n  " // whitespace-only title
            );

            testimonialService.submit(dto);

            ArgumentCaptor<Testimonial> captor = ArgumentCaptor.forClass(Testimonial.class);
            verify(testimonialRepository).save(captor.capture());
            Testimonial saved = captor.getValue();

            assertThat(saved.getAuthorCity()).isNull();
            assertThat(saved.getQuoteTitle()).isNull();
        }

        @Test
        @DisplayName("throws 409 when a PENDING testimonial already exists for the org")
        void submit_throwsConflict_whenPendingExists() {
            when(testimonialRepository.findByOrganisationIdOrderByCreatedAtDesc(ORG_ID))
                    .thenReturn(List.of(buildExistingEntity(ORG_ID, null)));

            TestimonialDto dto = buildDto("Alice", "Cook", null, "Great", null);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> testimonialService.submit(dto));
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            verify(testimonialRepository, never()).save(any());
        }
    }

    // =========================================================================
    // update()
    // =========================================================================

    @Nested
    @DisplayName("update()")
    class UpdateTests {

        @Test
        @DisplayName("trims quote and quoteTitle on the existing entity")
        void update_trimsQuoteAndTitle() {
            Testimonial existing = buildExistingEntity(ORG_ID, null);
            when(testimonialRepository.findById(TESTIMONIAL_ID)).thenReturn(Optional.of(existing));

            TestimonialDto dto = buildDto(
                    "  Alice  ",
                    "  Cook  ",
                    "  Montreal  ",
                    "  \nNew quote with spaces\n  ",
                    "  New title  "
            );

            TestimonialResponse response = testimonialService.update(TESTIMONIAL_ID, dto);

            assertThat(existing.getQuote()).isEqualTo("New quote with spaces");
            assertThat(existing.getQuoteTitle()).isEqualTo("New title");
            assertThat(existing.getAuthorName()).isEqualTo("Alice");
            assertThat(existing.getAuthorRole()).isEqualTo("Cook");
            assertThat(existing.getAuthorCity()).isEqualTo("Montreal");
            assertThat(existing.getStatus()).isEqualTo(TestimonialStatus.PENDING);
            assertThat(response.quote()).isEqualTo("New quote with spaces");
        }

        @Test
        @DisplayName("returns 404 (enumeration defense) when the row belongs to another org")
        void update_returns404_whenOrgMismatch() {
            Testimonial foreign = buildExistingEntity(OTHER_ORG_ID, null);
            when(testimonialRepository.findById(TESTIMONIAL_ID)).thenReturn(Optional.of(foreign));

            TestimonialDto dto = buildDto("Alice", "Cook", null, "Great", null);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> testimonialService.update(TESTIMONIAL_ID, dto));
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(testimonialRepository, never()).save(any());
        }
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("happy path: deletes the row and fires best-effort R2 logo cleanup")
        void delete_success_triggersR2Cleanup() {
            String logoUrl = "https://cdn.example.com/logos/abc-123.svg";
            Testimonial existing = buildExistingEntity(ORG_ID, logoUrl);
            when(testimonialRepository.findById(TESTIMONIAL_ID)).thenReturn(Optional.of(existing));

            testimonialService.delete(TESTIMONIAL_ID);

            verify(testimonialRepository).delete(existing);
            verify(r2StorageService).deleteBlob(logoUrl);
        }

        @Test
        @DisplayName("skips R2 cleanup when the testimonial has no logo")
        void delete_skipsR2_whenNoLogo() {
            Testimonial existing = buildExistingEntity(ORG_ID, null);
            when(testimonialRepository.findById(TESTIMONIAL_ID)).thenReturn(Optional.of(existing));

            testimonialService.delete(TESTIMONIAL_ID);

            verify(testimonialRepository).delete(existing);
            verify(r2StorageService, never()).deleteBlob(any());
        }

        @Test
        @DisplayName("skips R2 cleanup when logo URL is blank")
        void delete_skipsR2_whenLogoBlank() {
            Testimonial existing = buildExistingEntity(ORG_ID, "   ");
            when(testimonialRepository.findById(TESTIMONIAL_ID)).thenReturn(Optional.of(existing));

            testimonialService.delete(TESTIMONIAL_ID);

            verify(testimonialRepository).delete(existing);
            verify(r2StorageService, never()).deleteBlob(any());
        }

        @Test
        @DisplayName("returns 404 (enumeration defense) when the row belongs to another org")
        void delete_returns404_whenOrgMismatch() {
            Testimonial foreign = buildExistingEntity(OTHER_ORG_ID, "https://cdn.example.com/logos/abc.svg");
            when(testimonialRepository.findById(TESTIMONIAL_ID)).thenReturn(Optional.of(foreign));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> testimonialService.delete(TESTIMONIAL_ID));
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(testimonialRepository, never()).delete(any(Testimonial.class));
            verify(r2StorageService, never()).deleteBlob(any());
        }

        @Test
        @DisplayName("returns 404 when the testimonial does not exist")
        void delete_returns404_whenNotFound() {
            when(testimonialRepository.findById(TESTIMONIAL_ID)).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> testimonialService.delete(TESTIMONIAL_ID));
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(testimonialRepository, never()).delete(any(Testimonial.class));
            verify(r2StorageService, never()).deleteBlob(any());
        }
    }
}
