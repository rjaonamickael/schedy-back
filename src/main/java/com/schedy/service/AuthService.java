package com.schedy.service;

import com.schedy.config.JwtUtil;
import com.schedy.dto.request.AuthRequest;
import com.schedy.dto.request.RefreshRequest;
import com.schedy.dto.request.RegisterRequest;
import com.schedy.dto.response.AuthResponse;
import com.schedy.entity.User;
import com.schedy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalStateException("Un utilisateur avec cet email existe déjà");
        }

        User.UserRole role = User.UserRole.EMPLOYEE;
        if (request.role() != null) {
            try {
                role = User.UserRole.valueOf(request.role().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Rôle invalide : " + request.role());
            }
        }
        if (role == User.UserRole.ADMIN) {
            throw new IllegalArgumentException("L'inscription en tant qu'ADMIN n'est pas autorisée");
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(role)
                .employeId(request.employeId())
                .organisationId(request.organisationId())
                .build();

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getOrganisationId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        user.setRefreshToken(hashToken(refreshToken));

        userRepository.save(user);
        log.info("New user registered: {} with role {}", request.email(), role);

        return new AuthResponse(accessToken, refreshToken, user.getEmail(), user.getRole().name(), user.getEmployeId(), user.getOrganisationId());
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun compte associé à cette adresse e-mail"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("Failed login attempt for: {}", request.email());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Mot de passe incorrect");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getOrganisationId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        user.setRefreshToken(hashToken(refreshToken));
        userRepository.save(user);
        log.info("User logged in: {}", request.email());

        return new AuthResponse(accessToken, refreshToken, user.getEmail(), user.getRole().name(), user.getEmployeId(), user.getOrganisationId());
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();

        if (!jwtUtil.isTokenValid(token) || !jwtUtil.isRefreshToken(token)) {
            throw new IllegalArgumentException("Refresh token invalide");
        }

        String email = jwtUtil.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));

        if (!hashToken(token).equals(user.getRefreshToken())) {
            throw new IllegalArgumentException("Refresh token ne correspond pas");
        }

        String newAccessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getOrganisationId());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        user.setRefreshToken(hashToken(newRefreshToken));
        userRepository.save(user);
        log.debug("Token refreshed for: {}", email);

        return new AuthResponse(newAccessToken, newRefreshToken, user.getEmail(), user.getRole().name(), user.getEmployeId(), user.getOrganisationId());
    }

    @Transactional
    public void logout(String refreshToken) {
        String hashedToken = hashToken(refreshToken);
        userRepository.findByRefreshToken(hashedToken).ifPresent(user -> {
            user.setRefreshToken(null);
            userRepository.save(user);
            log.info("User logged out: {}", user.getEmail());
        });
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
