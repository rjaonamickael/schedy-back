package com.schedy.service;

import com.schedy.config.JwtUtil;
import com.schedy.dto.request.AuthRequest;
import com.schedy.dto.request.RegisterRequest;
import com.schedy.dto.response.AuthResponse;
import com.schedy.entity.User;
import com.schedy.repository.UserRepository;
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
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

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
        when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(new AuthRequest(EMAIL, PASSWORD));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.role()).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("login() throws 404 for unknown email")
    void login_throws404_unknownEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(new AuthRequest(EMAIL, PASSWORD)));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("login() throws 401 for wrong password")
    void login_throws401_wrongPassword() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(buildUser(User.UserRole.EMPLOYEE)));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(new AuthRequest(EMAIL, PASSWORD)));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("register() blocks ADMIN role")
    void register_blocksAdmin() {
        RegisterRequest req = new RegisterRequest(EMAIL, PASSWORD, "ADMIN", null, ORG_ID);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register() creates user with encoded password")
    void register_createsUser() {
        RegisterRequest req = new RegisterRequest(EMAIL, PASSWORD, "EMPLOYEE", null, ORG_ID);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED);
        when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(req);

        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.role()).isEqualTo("EMPLOYEE");
    }
}
