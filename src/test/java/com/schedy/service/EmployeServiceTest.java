package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.EmployeDto;
import com.schedy.entity.Employe;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.*;
import com.schedy.repository.UserRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeService unit tests")
class EmployeServiceTest {

    @Mock private EmployeRepository employeRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantContext tenantContext;
    @Mock private CreneauAssigneRepository creneauAssigneRepository;
    @Mock private PointageRepository pointageRepository;
    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private BanqueCongeRepository banqueCongeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private com.schedy.util.TotpEncryptionUtil pinEncryptionUtil;
    @Mock private SiteRepository siteRepository;
    @Mock private EmailService emailService;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private PlatformAnnouncementRepository announcementRepository;

    @InjectMocks private EmployeService employeService;

    private static final String ORG_ID = "org-123";
    private static final String EMPLOYE_ID = "emp-456";
    private static final String RAW_PIN = "1234";
    private static final String ENCODED_PIN = "$2a$10$encodedBcryptHash";

    @BeforeEach
    void setUp() {
        // lenient: not all tests need all stubs
        lenient().when(tenantContext.requireOrganisationId()).thenReturn(ORG_ID);
        // Subscription: allow up to 100 employees by default
        lenient().when(subscriptionRepository.findByOrganisationId(ORG_ID))
                .thenReturn(Optional.of(com.schedy.entity.Subscription.builder()
                        .maxEmployees(100).build()));
        lenient().when(employeRepository.countByOrganisationId(ORG_ID)).thenReturn(0L);
        // PIN encryption
        lenient().when(pinEncryptionUtil.encrypt(anyString())).thenAnswer(inv -> "ENC_" + inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("returns employees scoped to current organisation")
        void findAll_returnsEmployeesForCurrentOrg() {
            List<Employe> expected = List.of(
                    Employe.builder().id("e1").nom("Alice").organisationId(ORG_ID).build(),
                    Employe.builder().id("e2").nom("Bob").organisationId(ORG_ID).build());
            when(employeRepository.findByOrganisationId(ORG_ID)).thenReturn(expected);

            List<Employe> result = employeService.findAll();

            assertThat(result).hasSize(2).extracting(Employe::getNom).containsExactly("Alice", "Bob");
            verify(tenantContext).requireOrganisationId();
            verify(employeRepository).findByOrganisationId(ORG_ID);
        }

        @Test
        @DisplayName("returns empty list when organisation has no employees")
        void findAll_returnsEmptyList_whenNoEmployees() {
            when(employeRepository.findByOrganisationId(ORG_ID)).thenReturn(List.of());

            List<Employe> result = employeService.findAll();

            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("returns employee when found in current org")
        void findById_existingEmployee_returnsEmployee() {
            Employe emp = Employe.builder().id(EMPLOYE_ID).nom("Alice").organisationId(ORG_ID).build();
            when(employeRepository.findByIdAndOrganisationId(EMPLOYE_ID, ORG_ID)).thenReturn(Optional.of(emp));

            Employe result = employeService.findById(EMPLOYE_ID);

            assertThat(result.getId()).isEqualTo(EMPLOYE_ID);
            assertThat(result.getNom()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when employee not found")
        void findById_nonExisting_throwsResourceNotFoundException() {
            when(employeRepository.findByIdAndOrganisationId(EMPLOYE_ID, ORG_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> employeService.findById(EMPLOYE_ID));
        }
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("encodes PIN with bcrypt and stores SHA-256 pinHash")
        void create_encodesPin_and_computesPinHash() {
            EmployeDto dto = new EmployeDto(null, "Alice", "employe", null, null,
                    null, null, RAW_PIN, null, List.of(), List.of());
            when(passwordEncoder.encode(RAW_PIN)).thenReturn(ENCODED_PIN);
            ArgumentCaptor<Employe> captor = ArgumentCaptor.forClass(Employe.class);
            when(employeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            employeService.create(dto);

            Employe saved = captor.getValue();
            assertThat(saved.getPin()).isEqualTo(ENCODED_PIN);
            // pinHash must be the deterministic SHA-256 of the raw PIN
            String expectedHash = EmployeService.sha256(RAW_PIN);
            assertThat(saved.getPinHash()).isEqualTo(expectedHash);
            verify(passwordEncoder).encode(RAW_PIN);
        }

        @Test
        @DisplayName("stores null pin and null pinHash when dto.pin() is null")
        void create_nullPin_storesBothNull() {
            EmployeDto dto = new EmployeDto(null, "Bob", "employe", null, null,
                    null, null, null, null, List.of(), List.of());
            ArgumentCaptor<Employe> captor = ArgumentCaptor.forClass(Employe.class);
            when(employeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            employeService.create(dto);

            Employe saved = captor.getValue();
            assertThat(saved.getPin()).isNull();
            assertThat(saved.getPinHash()).isNull();
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("sets organisationId from TenantContext")
        void create_savesWithOrg() {
            EmployeDto dto = new EmployeDto(null, "Alice", "employe", "0600000000",
                    "alice@example.com", null, null, "1234", null, List.of(), List.of("site-1"));
            when(passwordEncoder.encode(any())).thenReturn(ENCODED_PIN);
            when(employeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Employe result = employeService.create(dto);

            assertThat(result.getNom()).isEqualTo("Alice");
            assertThat(result.getOrganisationId()).isEqualTo(ORG_ID);
        }
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("re-encodes PIN and recomputes pinHash when new PIN provided")
        void update_withNewPin_reEncodesAndReHashesPin() {
            String newPin = "5678";
            String newEncoded = "$2a$10$newHash";
            Employe existing = Employe.builder().id(EMPLOYE_ID).nom("Alice")
                    .pin(ENCODED_PIN).pinHash(EmployeService.sha256(RAW_PIN))
                    .organisationId(ORG_ID)
                    .disponibilites(new ArrayList<>()).siteIds(new ArrayList<>())
                    .build();
            EmployeDto dto = new EmployeDto(EMPLOYE_ID, "Alice Updated", "employe",
                    null, null, null, null, newPin, null, List.of(), List.of());
            when(employeRepository.findByIdAndOrganisationId(EMPLOYE_ID, ORG_ID)).thenReturn(Optional.of(existing));
            when(passwordEncoder.encode(newPin)).thenReturn(newEncoded);
            when(employeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Employe result = employeService.update(EMPLOYE_ID, dto);

            assertThat(result.getPin()).isEqualTo(newEncoded);
            assertThat(result.getPinHash()).isEqualTo(EmployeService.sha256(newPin));
            verify(passwordEncoder).encode(newPin);
        }

        @Test
        @DisplayName("keeps existing PIN when dto.pin() is null")
        void update_withNullPin_keepsExistingPin() {
            Employe existing = Employe.builder().id(EMPLOYE_ID).nom("Alice")
                    .pin(ENCODED_PIN).pinHash(EmployeService.sha256(RAW_PIN))
                    .organisationId(ORG_ID)
                    .disponibilites(new ArrayList<>()).siteIds(new ArrayList<>())
                    .build();
            EmployeDto dto = new EmployeDto(EMPLOYE_ID, "Alice Updated", "employe",
                    null, null, null, null, null, null, List.of(), List.of());
            when(employeRepository.findByIdAndOrganisationId(EMPLOYE_ID, ORG_ID)).thenReturn(Optional.of(existing));
            when(employeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Employe result = employeService.update(EMPLOYE_ID, dto);

            // PIN must remain unchanged
            assertThat(result.getPin()).isEqualTo(ENCODED_PIN);
            assertThat(result.getPinHash()).isEqualTo(EmployeService.sha256(RAW_PIN));
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("keeps existing PIN when dto.pin() is blank")
        void update_withBlankPin_keepsExistingPin() {
            Employe existing = Employe.builder().id(EMPLOYE_ID).nom("Alice")
                    .pin(ENCODED_PIN).pinHash(EmployeService.sha256(RAW_PIN))
                    .organisationId(ORG_ID)
                    .disponibilites(new ArrayList<>()).siteIds(new ArrayList<>())
                    .build();
            EmployeDto dto = new EmployeDto(EMPLOYE_ID, "Alice Updated", "employe",
                    null, null, null, null, "   ", null, List.of(), List.of());
            when(employeRepository.findByIdAndOrganisationId(EMPLOYE_ID, ORG_ID)).thenReturn(Optional.of(existing));
            when(employeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            employeService.update(EMPLOYE_ID, dto);

            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when employee does not exist")
        void update_nonExisting_throwsResourceNotFoundException() {
            EmployeDto dto = new EmployeDto(EMPLOYE_ID, "X", null, null, null,
                    null, null, null, null, null, null);
            when(employeRepository.findByIdAndOrganisationId(EMPLOYE_ID, ORG_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> employeService.update(EMPLOYE_ID, dto));
        }
    }

    // -------------------------------------------------------------------------
    // findByPin
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findByPin()")
    class FindByPin {

        @Test
        @DisplayName("returns employee when SHA-256 matches and bcrypt verifies")
        void findByPin_matchingHash_verifiesBcrypt() {
            String hash = EmployeService.sha256(RAW_PIN);
            Employe emp = Employe.builder().id(EMPLOYE_ID).nom("Alice")
                    .pin(ENCODED_PIN).pinHash(hash).organisationId(ORG_ID).build();
            when(employeRepository.findByPinHashAndOrganisationId(hash, ORG_ID)).thenReturn(Optional.of(emp));
            when(passwordEncoder.matches(RAW_PIN, ENCODED_PIN)).thenReturn(true);

            Optional<Employe> result = employeService.findByPin(RAW_PIN);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(EMPLOYE_ID);
            verify(passwordEncoder).matches(RAW_PIN, ENCODED_PIN);
        }

        @Test
        @DisplayName("returns empty when no employee has the given PIN hash")
        void findByPin_noMatch_returnsEmpty() {
            String hash = EmployeService.sha256(RAW_PIN);
            when(employeRepository.findByPinHashAndOrganisationId(hash, ORG_ID)).thenReturn(Optional.empty());

            Optional<Employe> result = employeService.findByPin(RAW_PIN);

            assertThat(result).isEmpty();
            // bcrypt must never be called when the hash lookup yields nothing
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("returns empty when SHA-256 hash matches but bcrypt verification fails")
        void findByPin_hashMatchButBcryptFails_returnsEmpty() {
            // Edge case: same SHA-256 prefix but bcrypt doesn't match (theoretically
            // possible if the stored PIN has been changed without updating the hash, or
            // in a collision scenario).
            String hash = EmployeService.sha256(RAW_PIN);
            Employe emp = Employe.builder().id(EMPLOYE_ID).nom("Alice")
                    .pin(ENCODED_PIN).pinHash(hash).organisationId(ORG_ID).build();
            when(employeRepository.findByPinHashAndOrganisationId(hash, ORG_ID)).thenReturn(Optional.of(emp));
            when(passwordEncoder.matches(RAW_PIN, ENCODED_PIN)).thenReturn(false);

            Optional<Employe> result = employeService.findByPin(RAW_PIN);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when employee has null stored PIN")
        void findByPin_nullStoredPin_returnsEmpty() {
            String hash = EmployeService.sha256(RAW_PIN);
            // Employee found by hash but pin field is null (data inconsistency guard)
            Employe emp = Employe.builder().id(EMPLOYE_ID).nom("Alice")
                    .pin(null).pinHash(hash).organisationId(ORG_ID).build();
            when(employeRepository.findByPinHashAndOrganisationId(hash, ORG_ID)).thenReturn(Optional.of(emp));

            Optional<Employe> result = employeService.findByPin(RAW_PIN);

            assertThat(result).isEmpty();
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("cascades all related data before deleting the employee")
        void delete_cascadesAllRelatedData() {
            Employe emp = Employe.builder().id(EMPLOYE_ID).nom("Alice").organisationId(ORG_ID).build();
            when(employeRepository.findByIdAndOrganisationId(EMPLOYE_ID, ORG_ID)).thenReturn(Optional.of(emp));

            employeService.delete(EMPLOYE_ID);

            verify(creneauAssigneRepository).deleteByEmployeIdAndOrganisationId(EMPLOYE_ID, ORG_ID);
            verify(pointageRepository).deleteByEmployeIdAndOrganisationId(EMPLOYE_ID, ORG_ID);
            verify(demandeCongeRepository).deleteByEmployeIdAndOrganisationId(EMPLOYE_ID, ORG_ID);
            verify(banqueCongeRepository).deleteByEmployeIdAndOrganisationId(EMPLOYE_ID, ORG_ID);
            verify(employeRepository).delete(emp);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when employee not found")
        void delete_throwsWhenNotFound() {
            when(employeRepository.findByIdAndOrganisationId(EMPLOYE_ID, ORG_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> employeService.delete(EMPLOYE_ID));
            // No cascade deletes must happen if the employee was not found
            verifyNoInteractions(creneauAssigneRepository, pointageRepository,
                    demandeCongeRepository, banqueCongeRepository);
        }
    }

    // -------------------------------------------------------------------------
    // sha256 utility
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("sha256()")
    class Sha256 {

        @Test
        @DisplayName("produces identical digest for identical input")
        void sha256_consistentResults() {
            String hash1 = EmployeService.sha256("1234");
            String hash2 = EmployeService.sha256("1234");

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("produces different digest for different inputs")
        void sha256_differentInputs_differentHashes() {
            String hash1 = EmployeService.sha256("1234");
            String hash2 = EmployeService.sha256("5678");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("produces a 64-character hexadecimal string")
        void sha256_returns64CharHex() {
            String hash = EmployeService.sha256("anyInput");

            // SHA-256 produces 32 bytes = 64 hex characters
            assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("well-known digest for PIN 1234")
        void sha256_knownVector() {
            // echo -n "1234" | sha256sum = 03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4
            String hash = EmployeService.sha256("1234");

            assertThat(hash).isEqualTo("03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4");
        }
    }

    // ── C-06 — toResponseWithUser ──

    @Nested
    @DisplayName("toResponseWithUser()")
    class ToResponseWithUser {

        @Test
        @DisplayName("returns correct systemRole when User exists")
        void withLinkedUser_populatesSystemRole() {
            Employe emp = Employe.builder()
                    .id(EMPLOYE_ID).nom("Alice").role("employe")
                    .organisationId(ORG_ID)
                    .disponibilites(new java.util.ArrayList<>())
                    .siteIds(new java.util.ArrayList<>())
                    .build();
            com.schedy.entity.User user = com.schedy.entity.User.builder()
                    .id(1L).email("alice@example.com").password("x")
                    .role(com.schedy.entity.User.UserRole.MANAGER)
                    .employeId(EMPLOYE_ID).organisationId(ORG_ID)
                    .build();
            when(userRepository.findByEmployeId(EMPLOYE_ID)).thenReturn(Optional.of(user));

            com.schedy.dto.response.EmployeResponse result = employeService.toResponseWithUser(emp);

            assertThat(result.hasUserAccount()).isTrue();
            assertThat(result.systemRole()).isEqualTo("MANAGER");
        }

        @Test
        @DisplayName("returns hasUserAccount=false when no User linked")
        void noLinkedUser_hasUserAccountFalse() {
            Employe emp = Employe.builder()
                    .id(EMPLOYE_ID).nom("Bob").role("employe")
                    .organisationId(ORG_ID)
                    .disponibilites(new java.util.ArrayList<>())
                    .siteIds(new java.util.ArrayList<>())
                    .build();
            when(userRepository.findByEmployeId(EMPLOYE_ID)).thenReturn(Optional.empty());

            com.schedy.dto.response.EmployeResponse result = employeService.toResponseWithUser(emp);

            assertThat(result.hasUserAccount()).isFalse();
            assertThat(result.systemRole()).isNull();
        }
    }
}
