package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.EmployeDto;
import com.schedy.dto.request.UpdateSystemRoleRequest;
import com.schedy.dto.response.EmployeImpactResponse;
import com.schedy.entity.Employe;
import com.schedy.entity.PlatformAnnouncement;
import com.schedy.entity.StatutDemande;
import com.schedy.entity.User;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.BanqueCongeRepository;
import com.schedy.repository.CreneauAssigneRepository;
import com.schedy.repository.DemandeCongeRepository;
import com.schedy.repository.EmployeRepository;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.PlatformAnnouncementRepository;
import com.schedy.repository.PointageRepository;
import com.schedy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
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
    private final EmailService emailService;
    private final OrganisationRepository organisationRepository;
    private final PlatformAnnouncementRepository announcementRepository;

    @Value("${schedy.invitation.expiry-hours:24}")
    private int invitationExpiryHours;

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
        if (dto.email() != null && !dto.email().isBlank()
                && employeRepository.existsByEmailAndOrganisationId(dto.email(), orgId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un employ\u00e9 avec l'email " + dto.email() + " existe d\u00e9j\u00e0.");
        }
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
        employeRepository.save(employe);

        // Create a User account for any employee who has an email address
        if (dto.email() != null && !dto.email().isBlank()) {
            if (!userRepository.existsByEmail(dto.email())) {
                String rawToken = generateSecureToken();
                String hashedToken = sha256(rawToken);
                User newUser = User.builder()
                        .email(dto.email())
                        .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                        .role(User.UserRole.EMPLOYEE)
                        .employeId(employe.getId())
                        .organisationId(orgId)
                        .nom(dto.nom())
                        .invitationToken(hashedToken)
                        .invitationTokenExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(invitationExpiryHours)))
                        .build();
                userRepository.save(newUser);
                // Best-effort email — do not roll back if sending fails
                try {
                    boolean isFrench = resolveIsFrench(orgId);
                    emailService.sendInvitationEmail(dto.email(), dto.nom(), rawToken, isFrench);
                } catch (Exception e) {
                    log.error("Failed to send invitation email to {} for new employee {}: {}",
                            dto.email(), employe.getId(), e.getMessage());
                }
            } else {
                log.warn("Employee created with email {} but a User account already exists for that address; skipping user creation.",
                        dto.email());
            }
        }

        return employe;
    }

    @Transactional
    public Employe update(String id, EmployeDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Employe employe = employeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", id));
        // Check email uniqueness if changed
        if (dto.email() != null && !dto.email().isBlank() && !dto.email().equals(employe.getEmail())) {
            if (employeRepository.existsByEmailAndOrganisationId(dto.email(), orgId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Un employ\u00e9 avec l'email " + dto.email() + " existe d\u00e9j\u00e0.");
            }
        }
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

    /**
     * Returns a lightweight impact summary for the given employee.
     * Called before the confirmation dialog is shown so the user understands
     * what data will be hard-deleted.
     *
     * All counts are performed with single COUNT queries — no collections are
     * loaded into memory.
     */
    @Transactional(readOnly = true)
    public EmployeImpactResponse getImpact(String id) {
        String orgId = tenantContext.requireOrganisationId();
        // Verify employee belongs to this org (throws 404 otherwise)
        Employe employe = employeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", id));

        // Current ISO week string, e.g. "2026-W13"
        LocalDate today = LocalDate.now();
        int year = today.get(IsoFields.WEEK_BASED_YEAR);
        int week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        String currentWeek = String.format("%04d-W%02d", year, week);

        long creneauxSemaineCourante = creneauAssigneRepository
                .countByEmployeIdAndOrganisationIdAndSemaine(id, orgId, currentWeek);
        long creneauxFuturs = creneauAssigneRepository
                .countFutureByEmployeIdAndOrganisationId(id, orgId, currentWeek);

        long demandesEnAttente = demandeCongeRepository
                .countByEmployeIdAndOrganisationIdAndStatut(id, orgId, StatutDemande.en_attente);
        long demandesApprouvees = demandeCongeRepository
                .countApprovedFutureByEmployeId(id, orgId, StatutDemande.approuve, today);

        long nbPointages = pointageRepository.countByEmployeIdAndOrganisationId(id, orgId);

        long nbSites = employe.getSiteIds() != null ? employe.getSiteIds().size() : 0;

        boolean hasCompteManager = userRepository.findByEmployeId(id)
                .map(u -> u.getRole() == User.UserRole.MANAGER)
                .orElse(false);

        return new EmployeImpactResponse(
                creneauxFuturs,
                creneauxSemaineCourante,
                nbSites,
                demandesEnAttente,
                demandesApprouvees,
                nbPointages,
                hasCompteManager
        );
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
        // Delete associated User account (if promoted to MANAGER)
        userRepository.findByEmployeId(id).ifPresent(userRepository::delete);
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
     *   <li>Promoting to MANAGER: if a User account already exists (created when the employee
     *       was added via {@link #create}), upgrades its role to MANAGER, sends a promotion
     *       notification email, and creates an org-scoped announcement. If no account exists yet
     *       (employee had no email at creation time), a new MANAGER account is created and an
     *       invitation email is sent so the employee can set their password.</li>
     *   <li>Demoting to EMPLOYEE: downgrades the existing User's role to EMPLOYEE; no-op if
     *       no User account exists.</li>
     * </ul>
     *
     * @return a map with {@code emailSent} (boolean) and {@code email} (string), or {@code null}
     *         on demotion.
     */
    @Transactional
    public Map<String, Object> updateSystemRole(String employeId, UpdateSystemRoleRequest request) {
        String orgId = tenantContext.requireOrganisationId();
        Employe employe = employeRepository.findByIdAndOrganisationId(employeId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", employeId));

        User.UserRole targetRole;
        try {
            targetRole = User.UserRole.valueOf(request.systemRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "systemRole invalide. Valeurs acceptees : MANAGER, EMPLOYEE");
        }

        if (targetRole != User.UserRole.MANAGER && targetRole != User.UserRole.EMPLOYEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "systemRole invalide. Valeurs acceptees : MANAGER, EMPLOYEE");
        }

        Optional<User> existingUser = userRepository.findByEmployeId(employeId);

        if (targetRole == User.UserRole.MANAGER) {
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                user.setRole(User.UserRole.MANAGER);
                userRepository.save(user);
                // Send promotion notification email (best-effort)
                boolean isFrench = resolveIsFrench(orgId);
                try {
                    emailService.sendPromotionEmail(employe.getEmail(), employe.getNom(), isFrench);
                } catch (Exception e) {
                    log.error("Failed to send promotion email to {}: {}", employe.getEmail(), e.getMessage());
                }
                // Create an org-scoped announcement visible to the whole organisation
                createPromotionAnnouncement(employe.getNom(), orgId, isFrench);
                return java.util.Map.of("emailSent", true, "email", employe.getEmail());
            } else {
                if (employe.getEmail() == null || employe.getEmail().isBlank()) {
                    throw new BusinessRuleException(
                            "L'employe doit avoir une adresse email pour creer un compte utilisateur.");
                }
                if (userRepository.existsByEmail(employe.getEmail())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Un compte existe deja avec l'email : " + employe.getEmail());
                }

                // Generate invitation token
                String rawToken = generateSecureToken();
                String hashedToken = sha256(rawToken);

                // Create user with unusable password
                User newUser = User.builder()
                        .email(employe.getEmail())
                        .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                        .role(User.UserRole.MANAGER)
                        .employeId(employe.getId())
                        .organisationId(orgId)
                        .nom(employe.getNom())
                        .invitationToken(hashedToken)
                        .invitationTokenExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(invitationExpiryHours)))
                        .build();
                userRepository.save(newUser);

                // Send invitation email (best-effort — do not roll back if email fails)
                boolean emailSent = false;
                try {
                    boolean isFrench = resolveIsFrench(orgId);
                    emailService.sendInvitationEmail(employe.getEmail(), employe.getNom(), rawToken, isFrench);
                    emailSent = true;
                } catch (Exception e) {
                    log.error("Failed to send invitation email to {} for employee {}: {}",
                            employe.getEmail(), employeId, e.getMessage());
                }

                return java.util.Map.of("emailSent", emailSent, "email", employe.getEmail());
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
     * Resends the invitation email for an employee with a pending invitation.
     * Generates a new token and resets the expiry.
     */
    @Transactional
    public void resendInvitation(String employeId) {
        String orgId = tenantContext.requireOrganisationId();
        Employe employe = employeRepository.findByIdAndOrganisationId(employeId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", employeId));

        User user = userRepository.findByEmployeId(employeId)
                .orElseThrow(() -> new BusinessRuleException("Aucun compte utilisateur pour cet employe."));

        if (employe.getEmail() == null || employe.getEmail().isBlank()) {
            throw new BusinessRuleException("L'employe doit avoir une adresse email.");
        }

        String rawToken = generateSecureToken();
        String hashedToken = sha256(rawToken);

        user.setInvitationToken(hashedToken);
        user.setInvitationTokenExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(invitationExpiryHours)));
        userRepository.save(user);

        boolean isFrench = resolveIsFrench(orgId);
        emailService.sendInvitationEmail(employe.getEmail(), employe.getNom(), rawToken, isFrench);
        log.info("Invitation email resent to {} for employee {}", employe.getEmail(), employeId);
    }

    /**
     * Generates a cryptographically secure random token (32 bytes, hex-encoded = 64 chars).
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder hex = new StringBuilder(64);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * Determines if the organisation's primary language is French,
     * based on the pays (ISO alpha-2/3) code.
     */
    private boolean resolveIsFrench(String orgId) {
        return organisationRepository.findById(orgId)
                .map(org -> {
                    String pays = org.getPays();
                    if (pays == null) return false;
                    String p = pays.toUpperCase();
                    return p.startsWith("FR") || "MDG".equals(p) || "MG".equals(p)
                            || "BE".equals(p) || "CH".equals(p) || "CA".equals(p)
                            || "SN".equals(p) || "CI".equals(p) || "CM".equals(p);
                })
                .orElse(false);
    }

    /**
     * Creates an org-scoped INFO announcement that a new Manager has been promoted.
     * The announcement expires after 7 days and is only visible within the given organisation.
     */
    private void createPromotionAnnouncement(String employeeName, String orgId, boolean isFrench) {
        // Both languages stored with ||| separator — frontend picks by active lang
        String titleFr = "Nouveau responsable\u00a0: " + employeeName;
        String titleEn = "New manager: " + employeeName;
        String bodyFr = employeeName + " a \u00e9t\u00e9 promu(e) au r\u00f4le de Manager et dispose d\u00e9sormais d\u2019un acc\u00e8s de gestion \u00e0 la plateforme.";
        String bodyEn = employeeName + " has been promoted to Manager and now has management access to the platform.";
        PlatformAnnouncement announcement = PlatformAnnouncement.builder()
                .title(titleFr + "|||" + titleEn)
                .body(bodyFr + "|||" + bodyEn)
                .severity(PlatformAnnouncement.Severity.INFO)
                .organisationId(orgId)
                .expiresAt(java.time.OffsetDateTime.now().plusDays(7))
                .build();
        announcementRepository.save(announcement);
    }

    /**
     * Compute SHA-256 hex digest of a string. Delegates to the shared {@link com.schedy.util.CryptoUtil}
     * to avoid duplicating cryptographic logic across services.
     */
    public static String sha256(String input) {
        return com.schedy.util.CryptoUtil.sha256(input);
    }
}
