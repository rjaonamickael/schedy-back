package com.schedy.service;

import com.schedy.config.JwtUtil;
import com.schedy.dto.request.AuthRequest;
import com.schedy.dto.request.RegisterRequest;
import com.schedy.entity.Organisation;
import com.schedy.entity.User;
import com.schedy.entity.UserSession;
import com.schedy.repository.EmployeRepository;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.UserRepository;
import com.schedy.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService unit tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSessionRepository sessionRepository;
    @Mock private EmployeRepository employeRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private HttpServletRequest httpReq;

    @InjectMocks private AuthService authService;

    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "secret123";
    private static final String ENCODED = "$2a$10$encoded";
    private static final String ORG_ID = "org-123";

    private User buildUser(User.UserRole role) {
        return User.builder().id(1L).email(EMAIL).password(ENCODED)
                .role(role).organisationId(ORG_ID).build();
    }

    @Test
    @DisplayName("login() returns tokens for valid credentials")
    void login_returnsTokens() {
        User user = buildUser(User.UserRole.MANAGER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);
        when(jwtUtil.generateAccessToken(anyString(), anyString(), any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh");
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(organisationRepository.findById(ORG_ID))
                .thenReturn(Optional.of(Organisation.builder().id(ORG_ID).nom("Test").pays("MG").build()));

        // SEC-20 / Sprint 11 : login now returns AuthResult carrying the raw refresh JWT
        // that the controller will pack into an HttpOnly cookie — the body has no refreshToken field.
        // V50 : a new UserSession row is created via sessionRepository instead of storing
        // the hash on the User entity, so multiple devices can stay logged in at once.
        AuthResult result = authService.login(new AuthRequest(EMAIL, PASSWORD), httpReq);

        assertThat(result.response().accessToken()).isEqualTo("access");
        assertThat(result.response().email()).isEqualTo(EMAIL);
        assertThat(result.response().role()).isEqualTo("MANAGER");
        assertThat(result.response().pays()).isEqualTo("MG");
        assertThat(result.rawRefreshToken()).isEqualTo("refresh");
    }

    @Test
    @DisplayName("login() throws 401 for unknown email (no user enumeration)")
    void login_throws401_unknownEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        // Dummy bcrypt hash is called for constant-time behavior
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(new AuthRequest(EMAIL, PASSWORD), httpReq));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getReason()).isEqualTo("Identifiant ou mot de passe incorrect");
    }

    @Test
    @DisplayName("login() throws 401 for wrong password")
    void login_throws401_wrongPassword() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(buildUser(User.UserRole.EMPLOYEE)));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(new AuthRequest(EMAIL, PASSWORD), httpReq));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getReason()).isEqualTo("Identifiant ou mot de passe incorrect");
    }

    @Test
    @DisplayName("register() blocks ADMIN role")
    void register_blocksAdmin() {
        RegisterRequest req = new RegisterRequest(EMAIL, PASSWORD, "ADMIN", null, ORG_ID);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> authService.register(req, httpReq));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register() creates user with encoded password")
    void register_createsUser() {
        RegisterRequest req = new RegisterRequest(EMAIL, PASSWORD, "EMPLOYEE", null, ORG_ID);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED);
        when(jwtUtil.generateAccessToken(anyString(), anyString(), any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));
        // register() creates a user with null organisationId (self-registration ignores org),
        // so resolveOrgPays(null) is called — no stub needed for organisationRepository here.

        // SEC-20 / Sprint 11 : register now returns AuthResult — same wrapper rationale as login.
        AuthResult result = authService.register(req, httpReq);

        assertThat(result.response().email()).isEqualTo(EMAIL);
        assertThat(result.response().role()).isEqualTo("EMPLOYEE");
        assertThat(result.rawRefreshToken()).isEqualTo("refresh");
    }
}
