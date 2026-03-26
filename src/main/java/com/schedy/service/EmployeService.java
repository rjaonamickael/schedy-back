package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.EmployeDto;
import com.schedy.dto.request.UpdateSystemRoleRequest;
import com.schedy.entity.Employe;
import com.schedy.entity.User;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.BanqueCongeRepository;
import com.schedy.repository.CreneauAssigneRepository;
import com.schedy.repository.DemandeCongeRepository;
import com.schedy.repository.EmployeRepository;
import com.schedy.repository.PointageRepository;
import com.schedy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeService {

    private final EmployeRepository employeRepository;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final CreneauAssigneRepository creneauAssigneRepository;
    private final PointageRepository pointageRepository;
    private final DemandeCongeRepository demandeCongeRepository;
    private final BanqueCongeRepository banqueCongeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<Employe> findAll(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findByOrganisationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Employe> findAll() {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findByOrganisationId(orgId);
    }

    @Transactional(readOnly = true)
    public Employe findById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", id));
    }

    /**
     * Find employee by raw PIN. Uses SHA-256 index for O(1) lookup,
     * then verifies with bcrypt for security.
     */
    @Transactional(readOnly = true)
    public Optional<Employe> findByPin(String rawPin) {
        String orgId = tenantContext.requireOrganisationId();
        String hash = sha256(rawPin);
        return employeRepository.findByPinHashAndOrganisationId(hash, orgId)
                .filter(emp -> emp.getPin() != null && passwordEncoder.matches(rawPin, emp.getPin()));
    }

    @Transactional(readOnly = true)
    public List<Employe> findByRole(String role) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findByRoleAndOrganisationId(role, orgId);
    }

    @Transactional(readOnly = true)
    public List<Employe> findBySiteId(String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findBySiteIdsContainingAndOrganisationId(siteId, orgId);
    }

    @Transactional(readOnly = true)
    public Page<Employe> findBySiteId(String siteId, Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findBySiteIdsContainingAndOrganisationId(siteId, orgId, pageable);
    }

    @Transactional
    public Employe create(EmployeDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Employe employe = Employe.builder()
                .nom(dto.nom())
                .role(dto.role())
                .telephone(dto.telephone())
                .email(dto.email())
                .dateNaissance(dto.dateNaissance())
                .dateEmbauche(dto.dateEmbauche())
                .pin(dto.pin() != null ? passwordEncoder.encode(dto.pin()) : null)
                .pinHash(dto.pin() != null ? sha256(dto.pin()) : null)
                .pinClair(dto.pin())
                .organisationId(orgId)
                .disponibilites(dto.disponibilites() != null ? dto.disponibilites() : Collections.emptyList())
                .siteIds(dto.siteIds() != null ? dto.siteIds() : Collections.emptyList())
                .build();
        return employeRepository.save(employe);
    }

    @Transactional
    public Employe update(String id, EmployeDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Employe employe = employeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", id));
        employe.setNom(dto.nom());
        employe.setRole(dto.role());
        employe.setTelephone(dto.telephone());
        employe.setEmail(dto.email());
        employe.setDateNaissance(dto.dateNaissance());
        employe.setDateEmbauche(dto.dateEmbauche());
        if (dto.pin() != null && !dto.pin().isBlank()) {
            employe.setPin(passwordEncoder.encode(dto.pin()));
            employe.setPinHash(sha256(dto.pin()));
            employe.setPinClair(dto.pin());
        }
        if (dto.disponibilites() != null) {
            employe.getDisponibilites().clear();
            employe.getDisponibilites().addAll(dto.disponibilites());
        }
        if (dto.siteIds() != null) {
            employe.getSiteIds().clear();
            employe.getSiteIds().addAll(dto.siteIds());
        }
        return employeRepository.save(employe);
    }

    /**
     * Verify a raw PIN against the hashed PIN stored for the employee.
     */
    public boolean verifyPin(String employeId, String rawPin) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findByIdAndOrganisationId(employeId, orgId)
                .map(emp -> emp.getPin() != null && passwordEncoder.matches(rawPin, emp.getPin()))
                .orElse(false);
    }

    @Transactional
    public void delete(String id) {
        String orgId = tenantContext.requireOrganisationId();
        Employe employe = employeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", id));
        creneauAssigneRepository.deleteByEmployeIdAndOrganisationId(id, orgId);
        pointageRepository.deleteByEmployeIdAndOrganisationId(id, orgId);
        demandeCongeRepository.deleteByEmployeIdAndOrganisationId(id, orgId);
        banqueCongeRepository.deleteByEmployeIdAndOrganisationId(id, orgId);
        employeRepository.delete(employe);
    }

    /** Find a User by email (no tenant scope — used for ownership checks). */
    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    /**
     * Returns a map of employeId -> User for all users in the current org.
     * Used for batch-loading user accounts when building lists of EmployeResponse.
     */
    @Transactional(readOnly = true)
    public Map<String, User> findAllUserMapByOrg() {
        String orgId = tenantContext.requireOrganisationId();
        return userRepository.findAllByOrganisationId(orgId).stream()
                .filter(u -> u.getEmployeId() != null)
                .collect(Collectors.toMap(User::getEmployeId, u -> u, (a, b) -> a));
    }

    /**
     * Promotes or demotes an employee's system role (MANAGER / EMPLOYEE).
     * <ul>
     *   <li>Promoting to MANAGER: creates a User account if none exists (requires tempPassword
     *       and a non-null email on the Employe), or upgrades the existing User's role.</li>
     *   <li>Demoting to EMPLOYEE: downgrades the existing User's role to EMPLOYEE; no-op if
     *       no User account exists.</li>
     * </ul>
     *
     * @return the auto-generated temporary password when a new User account is created,
     *         or {@code null} if the account already existed or the operation was a demotion.
     */
    @Transactional
    public String updateSystemRole(String employeId, UpdateSystemRoleRequest request) {
        String orgId = tenantContext.requireOrganisationId();
        Employe employe = employeRepository.findByIdAndOrganisationId(employeId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", employeId));

        User.UserRole targetRole;
        try {
            targetRole = User.UserRole.valueOf(request.systemRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "systemRole invalide. Valeurs acceptées : MANAGER, EMPLOYEE");
        }

        if (targetRole != User.UserRole.MANAGER && targetRole != User.UserRole.EMPLOYEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "systemRole invalide. Valeurs acceptées : MANAGER, EMPLOYEE");
        }

        Optional<User> existingUser = userRepository.findByEmployeId(employeId);

        if (targetRole == User.UserRole.MANAGER) {
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                user.setRole(User.UserRole.MANAGER);
                userRepository.save(user);
                // Account already existed — no new password to return
                return null;
            } else {
                // Create a new User account for this employee
                if (employe.getEmail() == null || employe.getEmail().isBlank()) {
                    throw new BusinessRuleException(
                            "L'employé doit avoir une adresse email pour créer un compte utilisateur.");
                }
                if (userRepository.existsByEmail(employe.getEmail())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Un compte existe déjà avec l'email : " + employe.getEmail());
                }
                // Auto-generate a temporary password — the manager will change it on first login
                String tempPassword = request.tempPassword() != null && !request.tempPassword().isBlank()
                        ? request.tempPassword()
                        : generateTempPassword();
                User newUser = User.builder()
                        .email(employe.getEmail())
                        .password(passwordEncoder.encode(tempPassword))
                        .role(User.UserRole.MANAGER)
                        .employeId(employe.getId())
                        .organisationId(orgId)
                        .nom(employe.getNom())
                        .build();
                userRepository.save(newUser);
                // Return the temp password so the admin can hand it to the new manager
                return tempPassword;
            }
        } else {
            // Demote to EMPLOYEE — only if a user account exists
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                user.setRole(User.UserRole.EMPLOYEE);
                userRepository.save(user);
            } else {
                log.info("Demotion no-op for employe {}: no User account exists", employeId);
            }
            return null;
        }
    }

    /**
     * Generates a random 12-character temporary password meeting the security policy:
     * at least 1 uppercase, 1 lowercase, 1 digit, 1 special character.
     */
    private String generateTempPassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%&*?";
        String all = upper + lower + digits + special;
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        sb.append(special.charAt(random.nextInt(special.length())));
        for (int i = 4; i < 12; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        // Shuffle
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp;
        }
        return new String(chars);
    }

    /**
     * Compute SHA-256 hex digest of a string. Delegates to the shared {@link com.schedy.util.CryptoUtil}
     * to avoid duplicating cryptographic logic across services.
     */
    public static String sha256(String input) {
        return com.schedy.util.CryptoUtil.sha256(input);
    }
}
