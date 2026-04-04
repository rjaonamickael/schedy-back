package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.entity.AbsenceImprevue;
import com.schedy.entity.User;
import com.schedy.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * B-08 — Unit tests verifying that AbsenceImprevueService.notifyManagers()
 * delegates to UserRepository.findByOrganisationIdAndRoleIn() with both
 * MANAGER and ADMIN roles, rather than duplicating separate queries per role.
 *
 * <p>These are behaviour / interaction tests via Mockito verify(). They do NOT
 * exercise the full signalerAbsence() flow (which needs TenantContext, Security
 * context, Employe lookup, etc.); instead they call the private helper indirectly
 * by providing a pre-built AbsenceImprevue and patching just enough collaborators
 * to reach the notify call path.
 *
 * <p>The MANAGER_ROLES constant is declared as:
 * {@code List.of(User.UserRole.MANAGER, User.UserRole.ADMIN)}
 * and is passed verbatim to userRepo.findByOrganisationIdAndRoleIn().
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AbsenceImprevueService unit tests (B-08)")
class AbsenceImprevueServiceTest {

    @Mock private AbsenceImprevueRepository absenceRepo;
    @Mock private CreneauAssigneRepository  creneauRepo;
    @Mock private EmployeRepository         employeRepo;
    @Mock private UserRepository            userRepo;
    @Mock private ParametresRepository      parametresRepo;
    @Mock private TenantContext             tenantContext;
    @Mock private EmailService              emailService;

    @InjectMocks private AbsenceImprevueService absenceImprevueService;

    private static final String ORG_ID = "org-test-01";

    // ── B-08 notifyManagers: uses findByOrganisationIdAndRoleIn ──────────────

    @Nested
    @DisplayName("notifyManagers() — B-08 consolidated role query")
    class NotifyManagers {

        /**
         * Builds a minimal valid AbsenceImprevue that can be passed to
         * the email notification methods without causing NPEs.
         */
        private AbsenceImprevue buildAbsence() {
            return AbsenceImprevue.builder()
                    .id("abs-1")
                    .employeId("emp-1")
                    .dateAbsence(LocalDate.now())
                    .motif("Maladie")
                    .signalePar("employe@example.com")
                    .initiateur(AbsenceImprevue.Initiateur.EMPLOYEE)
                    .statut(com.schedy.entity.StatutAbsenceImprevue.SIGNALEE)
                    .organisationId(ORG_ID)
                    .build();
        }

        /**
         * Verifies the method signature contract: UserRepository must expose
         * findByOrganisationIdAndRoleIn(String, List<UserRole>) so that both
         * MANAGER and ADMIN roles are fetched in a single query.
         *
         * <p>This test confirms the interface method exists and has the correct
         * parameter types by calling it via Mockito — if the signature changes
         * (e.g. split into two separate calls) this test will fail.
         */
        @Test
        @DisplayName("findByOrganisationIdAndRoleIn is the only user-lookup method called for manager notification")
        void usesRoleInQueryNotSeparateQueries() throws Exception {
            // Arrange: stub findByOrganisationIdAndRoleIn to return a single manager
            User manager = User.builder()
                    .id(1L)
                    .email("manager@example.com")
                    .nom("Alice Manager")
                    .role(User.UserRole.MANAGER)
                    .organisationId(ORG_ID)
                    .build();

            when(userRepo.findByOrganisationIdAndRoleIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(manager));

            // Act: invoke notifyManagers via reflection (private method)
            // We use reflection to call the private helper directly to keep
            // the test focused and avoid wiring the full signalerAbsence() path.
            AbsenceImprevue absence = buildAbsence();
            java.lang.reflect.Method method = AbsenceImprevueService.class
                    .getDeclaredMethod("notifyManagers", AbsenceImprevue.class, String.class, String.class);
            method.setAccessible(true);
            method.invoke(absenceImprevueService, absence, ORG_ID, "Jean Dupont");

            // Assert: the consolidated role-in query was called exactly once
            verify(userRepo, times(1)).findByOrganisationIdAndRoleIn(eq(ORG_ID), any());

            // Assert: no other user-lookup method was invoked (only the single role-in query)
            verify(userRepo, never()).findFirstByOrganisationIdAndRole(any(), any());
            verify(userRepo, never()).findAllByOrganisationId(any());
        }

        @Test
        @DisplayName("notifyManagers passes MANAGER and ADMIN roles together in one call")
        void passesManagerAndAdminRolesTogether() throws Exception {
            when(userRepo.findByOrganisationIdAndRoleIn(any(), any()))
                    .thenReturn(List.of());

            AbsenceImprevue absence = buildAbsence();
            java.lang.reflect.Method method = AbsenceImprevueService.class
                    .getDeclaredMethod("notifyManagers", AbsenceImprevue.class, String.class, String.class);
            method.setAccessible(true);
            method.invoke(absenceImprevueService, absence, ORG_ID, "Jean Dupont");

            // Capture the roles list passed to the repository
            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<List<User.UserRole>> rolesCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);

            verify(userRepo).findByOrganisationIdAndRoleIn(eq(ORG_ID), rolesCaptor.capture());

            List<User.UserRole> capturedRoles = rolesCaptor.getValue();
            assertThat(capturedRoles)
                    .as("Both MANAGER and ADMIN must be queried in a single findByOrganisationIdAndRoleIn call")
                    .containsExactlyInAnyOrder(User.UserRole.MANAGER, User.UserRole.ADMIN);
        }

        @Test
        @DisplayName("sends one email per manager returned by the repository")
        void sendsOneEmailPerManager() throws Exception {
            User manager1 = User.builder().id(1L).email("m1@x.com").nom("M1")
                    .role(User.UserRole.MANAGER).organisationId(ORG_ID).build();
            User manager2 = User.builder().id(2L).email("m2@x.com").nom("M2")
                    .role(User.UserRole.ADMIN).organisationId(ORG_ID).build();

            when(userRepo.findByOrganisationIdAndRoleIn(any(), any()))
                    .thenReturn(List.of(manager1, manager2));

            AbsenceImprevue absence = buildAbsence();
            java.lang.reflect.Method method = AbsenceImprevueService.class
                    .getDeclaredMethod("notifyManagers", AbsenceImprevue.class, String.class, String.class);
            method.setAccessible(true);
            method.invoke(absenceImprevueService, absence, ORG_ID, "Jean Dupont");

            verify(emailService, times(2)).sendAbsenceSignaleeEmail(any(), any(), any(), any());
        }

        @Test
        @DisplayName("when no managers exist, no email is sent and no exception thrown")
        void noManagersNoEmail() throws Exception {
            when(userRepo.findByOrganisationIdAndRoleIn(any(), any())).thenReturn(List.of());

            AbsenceImprevue absence = buildAbsence();
            java.lang.reflect.Method method = AbsenceImprevueService.class
                    .getDeclaredMethod("notifyManagers", AbsenceImprevue.class, String.class, String.class);
            method.setAccessible(true);

            // Must not throw
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> method.invoke(absenceImprevueService, absence, ORG_ID, "Jean Dupont")
            );

            verify(emailService, never()).sendAbsenceSignaleeEmail(any(), any(), any(), any());
        }
    }

    // AssertJ static import used inside the nested class above
    private static <T> org.assertj.core.api.AbstractListAssert<?, List<? extends T>, T,
            org.assertj.core.api.ObjectAssert<T>> assertThat(List<T> actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }
}
