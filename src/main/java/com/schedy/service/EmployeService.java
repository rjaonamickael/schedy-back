package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.EmployeDto;
import com.schedy.dto.request.UpdateSystemRoleRequest;
import com.schedy.dto.response.EmployeImpactResponse;
import com.schedy.dto.response.PinRegenerationResponse;
import com.schedy.dto.response.PinSheetEntryResponse;
import com.schedy.entity.Employe;
import com.schedy.entity.PinAuditLog;
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
import com.schedy.repository.SiteRepository;
import com.schedy.repository.SubscriptionRepository;
import com.schedy.repository.UserRepository;
import com.schedy.util.CryptoUtil;
import com.schedy.util.TotpEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeService {

    private final EmployeRepository employeRepository;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final SiteRepository siteRepository;
    private final CreneauAssigneRepository creneauAssigneRepository;
    private final PointageRepository pointageRepository;
    private final DemandeCongeRepository demandeCongeRepository;
    private final BanqueCongeRepository banqueCongeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final OrganisationRepository organisationRepository;
    private final PlatformAnnouncementRepository announcementRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TotpEncryptionUtil pinEncryptionUtil;
    private final CongeService congeService;
    private final PinAuditLogger pinAuditLogger;

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
        Employe employe = employeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", id));
        // SEC-12: EMPLOYEE may only read their own profile (prevents PII enumeration)
        checkOwnershipIfEmployee(employe.getId());
        return employe;
    }

    /**
     * Find employee by raw PIN. Uses SHA-256 index for O(1) lookup,
     * then verifies with bcrypt for security.
     */
    @Transactional(readOnly = true)
    public Optional<Employe> findByPin(String rawPin) {
        String orgId = tenantContext.requireOrganisationId();
        String hash = CryptoUtil.sha256(rawPin);
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
        long currentCount = employeRepository.countByOrganisationId(orgId);
        int maxEmployees = subscriptionRepository.findByOrganisationId(orgId)
                .map(sub -> sub.getMaxEmployees())
                .orElse(15); // FREE tier default
        if (currentCount >= maxEmployees) {
            throw new BusinessRuleException(
                    "Limite d'employ\u00e9s atteinte (" + maxEmployees
                    + "). Veuillez mettre \u00e0 niveau votre abonnement pour ajouter de nouveaux employ\u00e9s.");
        }
        if (dto.email() != null && !dto.email().isBlank()
                && employeRepository.existsByEmailAndOrganisationId(dto.email(), orgId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un employ\u00e9 avec l'email " + dto.email() + " existe d\u00e9j\u00e0.");
        }
        // Require at least one site when the organisation has sites
        long siteCount = siteRepository.countByOrganisationId(orgId);
        if (siteCount > 0 && (dto.siteIds() == null || dto.siteIds().isEmpty())) {
            throw new BusinessRuleException(
                    "L'employ\u00e9 doit \u00eatre affect\u00e9 \u00e0 au moins un site.");
        }
        Employe employe = Employe.builder()
                .nom(dto.nom())
                // Sprint 16 / Feature 2 : roles is an ordered list (index 0 = principal).
                .roles(dto.roles() != null ? new java.util.ArrayList<>(dto.roles()) : new java.util.ArrayList<>())
                .telephone(dto.telephone())
                .email(dto.email())
                .dateNaissance(dto.dateNaissance())
                .dateEmbauche(dto.dateEmbauche())
                .pin(dto.pin() != null ? passwordEncoder.encode(dto.pin()) : null)
                .pinHash(dto.pin() != null ? CryptoUtil.sha256(dto.pin()) : null)
                .pinClair(dto.pin() != null ? pinEncryptionUtil.encrypt(dto.pin()) : null)
                .pinClairEncrypted(dto.pin() != null)
                .organisationId(orgId)
                .disponibilites(dto.disponibilites() != null ? dto.disponibilites() : Collections.emptyList())
                .siteIds(dto.siteIds() != null ? dto.siteIds() : Collections.emptyList())
                .build();
        employeRepository.save(employe);

        // Auto-provision : the new employee gets a banque for every existing leave type
        // in the org (invariant : every (employe, type, org) triple has exactly one banque).
        // Quotas can be overridden per-employee afterwards via the banque update endpoint.
        congeService.provisionBanquesForEmploye(employe.getId(), orgId);

        // Create a User account for any employee who has an email address
        if (dto.email() != null && !dto.email().isBlank()) {
            if (!userRepository.existsByEmail(dto.email())) {
                String rawToken = CryptoUtil.generateSecureToken();
                String hashedToken = CryptoUtil.sha256(rawToken);
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
                    emailService.sendInvitationEmail(dto.email(), dto.nom(), rawToken);
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
        // Sprint 16 : clear-and-addAll keeps the same List instance managed by Hibernate.
        if (dto.roles() != null) {
            employe.getRoles().clear();
            employe.getRoles().addAll(dto.roles());
        }
        employe.setTelephone(dto.telephone());
        employe.setEmail(dto.email());
        // Fix CODE-01: guard nullable date fields — do not overwrite existing
        // values with null on partial updates (would break anciennete/age rules)
        if (dto.dateNaissance() != null) {
            employe.setDateNaissance(dto.dateNaissance());
        }
        if (dto.dateEmbauche() != null) {
            employe.setDateEmbauche(dto.dateEmbauche());
        }
        if (dto.pin() != null && !dto.pin().isBlank()) {
            employe.setPin(passwordEncoder.encode(dto.pin()));
            employe.setPinHash(CryptoUtil.sha256(dto.pin()));
            employe.setPinClair(pinEncryptionUtil.encrypt(dto.pin()));
            employe.setPinClairEncrypted(true);
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
     * Returns the decrypted plain-text PIN for an employee, for display in their dashboard.
     *
     * <p>Stored PINs are AES-256-GCM encrypted from application version V10 onward.
     * The {@code pinClairEncrypted} flag (added in Flyway V10) reliably distinguishes:
     * <ul>
     *   <li>{@code true}  — post-migration row: decrypt with AES-256-GCM.</li>
     *   <li>{@code false} — pre-migration row: value is plain text; returned as-is and a
     *       warning is logged. The value will be re-encrypted on the next PIN update.</li>
     * </ul>
     *
     * @param employeId the employee id (must belong to the current org)
     * @return the plain-text PIN, or an empty string if none is set
     */
    @Transactional(readOnly = true)
    public String getDecryptedPin(String employeId) {
        Employe employe = findById(employeId);
        String stored = employe.getPinClair();
        if (stored == null || stored.isBlank()) {
            return "";
        }
        if (!employe.isPinClairEncrypted()) {
            // Pre-V10 plaintext row — return as-is and let the caller know
            log.warn("Returning plaintext PIN for pre-migration employe {} — "
                    + "PIN will be re-encrypted on next update.", employeId);
            return stored;
        }
        try {
            return pinEncryptionUtil.decrypt(stored);
        } catch (Exception e) {
            // Should not happen for rows where pinClairEncrypted=true, but guard defensively
            log.error("AES-256-GCM decryption failed for employe {} despite encrypted flag being true. "
                    + "Key rotation or data corruption suspected. Error: {}", employeId, e.getMessage());
            throw new RuntimeException("PIN decryption failed — contact support.", e);
        }
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
     * Returns an {@link com.schedy.dto.response.EmployeResponse} for a single employee
     * with the linked {@link User} account populated (systemRole, hasUserAccount,
     * invitationPending).  Used by GET /{id}, POST, and PUT /{id} so that the
     * frontend always receives accurate role-badge data for single-employee responses.
     *
     * @param employe the already-loaded/saved Employe entity
     * @return EmployeResponse with User data if a linked account exists, otherwise with
     *         systemRole=null and hasUserAccount=false
     */
    @Transactional(readOnly = true)
    public com.schedy.dto.response.EmployeResponse toResponseWithUser(Employe employe) {
        User linkedUser = userRepository.findByEmployeId(employe.getId()).orElse(null);
        return com.schedy.dto.response.EmployeResponse.from(employe, linkedUser);
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
                try {
                    emailService.sendPromotionEmail(employe.getEmail(), employe.getNom());
                } catch (Exception e) {
                    log.error("Failed to send promotion email to {}: {}", employe.getEmail(), e.getMessage());
                }
                // Create an org-scoped announcement visible to the whole organisation
                createPromotionAnnouncement(employe.getNom(), orgId);
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
                String rawToken = CryptoUtil.generateSecureToken();
                String hashedToken = CryptoUtil.sha256(rawToken);

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
                    emailService.sendInvitationEmail(employe.getEmail(), employe.getNom(), rawToken);
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

        String rawToken = CryptoUtil.generateSecureToken();
        String hashedToken = CryptoUtil.sha256(rawToken);

        user.setInvitationToken(hashedToken);
        user.setInvitationTokenExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(invitationExpiryHours)));
        userRepository.save(user);

        try {
            emailService.sendInvitationEmail(employe.getEmail(), employe.getNom(), rawToken);
            log.info("Invitation email resent to {} for employee {}", employe.getEmail(), employeId);
        } catch (Exception e) {
            log.error("Failed to resend invitation email to {} for employee {}: {}",
                    employe.getEmail(), employeId, e.getMessage());
        }
    }

    /**
     * Creates an org-scoped INFO announcement that a new Manager has been promoted.
     * The announcement expires after 7 days and is only visible within the given organisation.
     */
    private void createPromotionAnnouncement(String employeeName, String orgId) {
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
     * SEC-12: Enforces row-level ownership for the EMPLOYEE role on employee reads.
     *
     * <p>ADMIN and MANAGER may read any employee in their organisation (tenant scoping
     * already enforced upstream). EMPLOYEE may only read their own profile; any attempt
     * to enumerate IDs and access another employee's PII (email, phone, date of birth)
     * results in a 403 Forbidden response.
     *
     * <p>The check is intentionally lenient when no SecurityContext is present
     * (e.g. unit tests that exercise the service without setting up authentication)
     * — in that case the call is treated as a trusted internal invocation.
     *
     * @param targetEmployeId the employeId of the resource being accessed
     * @throws ResponseStatusException 403 Forbidden if the caller is an EMPLOYEE
     *         and {@code targetEmployeId} does not match their own employeId
     */
    private void checkOwnershipIfEmployee(String targetEmployeId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return;
        }
        boolean isEmployee = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE"));
        if (!isEmployee) {
            return;
        }
        // Caller is strictly an EMPLOYEE — verify ownership
        String callerEmail = auth.getName();
        String callerEmployeId = userRepository.findByEmail(callerEmail)
                .map(User::getEmployeId)
                .orElse(null);
        if (callerEmployeId == null || !callerEmployeId.equals(targetEmployeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Acces refuse: vous ne pouvez consulter que vos propres donnees");
        }
    }

    // ===== V36: PIN regeneration + printable sheet + audit trail =====

    private static final SecureRandom PIN_RANDOM = new SecureRandom();
    private static final int PIN_GENERATION_MAX_ATTEMPTS = 20;
    private static final int PIN_SHEET_MAX_ENTRIES = 200;
    private static final int PIN_BATCH_MAX_SIZE = 200;

    /**
     * V36: regenerate a single employee's kiosk PIN on admin demand. Returns
     * the freshly generated PIN so the caller can immediately display + print
     * it. The new PIN is guaranteed unique against any other employee sharing
     * at least one site with this one (closes a long-standing latent bug
     * where two employees on the same site could collide on a 4-digit PIN).
     * Old and new SHA-256 hashes are written to {@code pin_audit_log}.
     */
    @Transactional
    public PinRegenerationResponse regenerateIndividualPin(String employeId, String motif) {
        String orgId = tenantContext.requireOrganisationId();
        Employe employe = employeRepository.findByIdAndOrganisationId(employeId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", employeId));

        String oldPinHash = employe.getPinHash();
        String newPin = generateUniquePinForEmploye(employe, orgId);
        String newPinHash = CryptoUtil.sha256(newPin);

        applyNewPin(employe, newPin, newPinHash);
        employeRepository.save(employe);

        String adminUserId = getCurrentAdminUserId();
        pinAuditLogger.write(PinAuditLog.builder()
                .employeId(employeId)
                .adminUserId(adminUserId)
                .action(PinAuditLog.Action.REGENERATE_INDIVIDUAL)
                .source(PinAuditLog.Source.ADMIN_UI)
                .oldPinHash(oldPinHash)
                .newPinHash(newPinHash)
                .motif(motif)
                .organisationId(orgId)
                .build());

        log.info("Individual PIN regenerated for employe {} (admin={})", employeId, adminUserId);
        return new PinRegenerationResponse(
                employe.getId(),
                newPin,
                employe.getPinGeneratedAt(),
                employe.getPinVersion());
    }

    /**
     * V36: regenerate PINs for a batch of employees in one atomic transaction.
     * Capped at {@value #PIN_BATCH_MAX_SIZE} to keep the transaction bounded
     * and avoid PIN regeneration storms on large organisations.
     */
    @Transactional
    public List<PinRegenerationResponse> regeneratePinsBatch(List<String> employeIds, String motif) {
        if (employeIds == null || employeIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (employeIds.size() > PIN_BATCH_MAX_SIZE) {
            throw new BusinessRuleException(
                    "Maximum " + PIN_BATCH_MAX_SIZE + " employes par lot de regeneration.");
        }
        String orgId = tenantContext.requireOrganisationId();
        String adminUserId = getCurrentAdminUserId();

        List<Employe> employes = employeRepository.findAllById(employeIds).stream()
                .filter(e -> orgId.equals(e.getOrganisationId()))
                .toList();

        List<PinRegenerationResponse> results = new ArrayList<>(employes.size());
        for (Employe employe : employes) {
            String oldPinHash = employe.getPinHash();
            String newPin = generateUniquePinForEmploye(employe, orgId);
            String newPinHash = CryptoUtil.sha256(newPin);

            applyNewPin(employe, newPin, newPinHash);

            pinAuditLogger.write(PinAuditLog.builder()
                    .employeId(employe.getId())
                    .adminUserId(adminUserId)
                    .action(PinAuditLog.Action.REGENERATE_INDIVIDUAL)
                    .source(PinAuditLog.Source.BATCH_OP)
                    .oldPinHash(oldPinHash)
                    .newPinHash(newPinHash)
                    .motif(motif)
                    .organisationId(orgId)
                    .build());

            results.add(new PinRegenerationResponse(
                    employe.getId(),
                    newPin,
                    employe.getPinGeneratedAt(),
                    employe.getPinVersion()));
        }
        employeRepository.saveAll(employes);

        log.info("Batch PIN regeneration: {} employes (admin={})", employes.size(), adminUserId);
        return results;
    }

    /**
     * V36: returns the data needed to render a printable PIN sheet. Each call
     * also writes a {@link PinAuditLog.Action#PRINT} audit entry per employee
     * so admins can answer "when was this card last issued ?".
     */
    @Transactional
    public List<PinSheetEntryResponse> getPinSheetData(List<String> employeIds) {
        if (employeIds == null || employeIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (employeIds.size() > PIN_SHEET_MAX_ENTRIES) {
            throw new BusinessRuleException(
                    "Maximum " + PIN_SHEET_MAX_ENTRIES + " employes par feuille d'impression.");
        }
        String orgId = tenantContext.requireOrganisationId();
        String adminUserId = getCurrentAdminUserId();

        List<Employe> employes = employeRepository.findAllById(employeIds).stream()
                .filter(e -> orgId.equals(e.getOrganisationId()))
                .toList();

        // Resolve site names in a single batch to avoid N+1
        Set<String> allSiteIds = new HashSet<>();
        for (Employe e : employes) {
            if (e.getSiteIds() != null) allSiteIds.addAll(e.getSiteIds());
        }
        Map<String, String> siteNamesById = allSiteIds.isEmpty()
                ? Collections.emptyMap()
                : siteRepository.findAllById(allSiteIds).stream()
                    .collect(Collectors.toMap(s -> s.getId(), s -> s.getNom(), (a, b) -> a));

        List<PinSheetEntryResponse> results = new ArrayList<>(employes.size());
        for (Employe employe : employes) {
            String firstSite = (employe.getSiteIds() != null && !employe.getSiteIds().isEmpty())
                    ? siteNamesById.get(employe.getSiteIds().get(0))
                    : null;
            String pinClair = decryptPinClair(employe);

            results.add(new PinSheetEntryResponse(
                    employe.getId(),
                    employe.getNom(),
                    firstSite,
                    employe.getPrimaryRole(),
                    pinClair,
                    employe.getPinGeneratedAt(),
                    employe.getPinVersion()));

            pinAuditLogger.write(PinAuditLog.builder()
                    .employeId(employe.getId())
                    .adminUserId(adminUserId)
                    .action(PinAuditLog.Action.PRINT)
                    .source(PinAuditLog.Source.ADMIN_UI)
                    .organisationId(orgId)
                    .build());
        }

        log.info("PIN sheet generated for {} employes (admin={})", employes.size(), adminUserId);
        return results;
    }

    /** Applies a freshly generated PIN to the entity (hashes, encryption, version, timestamp). */
    private void applyNewPin(Employe employe, String newPin, String newPinHash) {
        employe.setPin(passwordEncoder.encode(newPin));
        employe.setPinHash(newPinHash);
        employe.setPinClair(pinEncryptionUtil.encrypt(newPin));
        employe.setPinClairEncrypted(true);
        employe.setPinGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
        employe.setPinVersion((employe.getPinVersion() == null ? 1 : employe.getPinVersion()) + 1);
    }

    /**
     * Generates a 4-digit PIN guaranteed unique against any other employee
     * sharing at least one site with this one. Bounded retry; throws 500 if no
     * unique PIN found after {@value #PIN_GENERATION_MAX_ATTEMPTS} attempts
     * (extremely unlikely with 4 digits and < 50 employees per site).
     */
    private String generateUniquePinForEmploye(Employe employe, String orgId) {
        String employeId = employe.getId();
        List<String> siteIds = employe.getSiteIds();
        for (int i = 0; i < PIN_GENERATION_MAX_ATTEMPTS; i++) {
            String candidate = String.format("%04d", PIN_RANDOM.nextInt(10000));
            String hash = CryptoUtil.sha256(candidate);
            if (!isPinHashCollidingOnSites(hash, employeId, siteIds, orgId)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible de generer un PIN unique apres "
                + PIN_GENERATION_MAX_ATTEMPTS + " tentatives");
    }

    private boolean isPinHashCollidingOnSites(String hash, String selfId,
                                              List<String> siteIds, String orgId) {
        if (siteIds == null || siteIds.isEmpty()) {
            return false;
        }
        List<Employe> sameHash = employeRepository.findAllByPinHashAndOrganisationId(hash, orgId);
        for (Employe other : sameHash) {
            if (other.getId().equals(selfId)) continue;
            if (other.getSiteIds() == null) continue;
            for (String s : other.getSiteIds()) {
                if (siteIds.contains(s)) return true;
            }
        }
        return false;
    }

    /**
     * Shared decryption helper used by {@link #getDecryptedPin(String)} and
     * the printable sheet path. Encapsulates the pre-V10 plaintext fallback so
     * the legacy branch lives in exactly one place.
     */
    private String decryptPinClair(Employe employe) {
        String stored = employe.getPinClair();
        if (stored == null || stored.isBlank()) {
            return "";
        }
        if (!employe.isPinClairEncrypted()) {
            log.warn("Returning plaintext PIN for pre-migration employe {} — "
                    + "PIN will be re-encrypted on next update.", employe.getId());
            return stored;
        }
        try {
            return pinEncryptionUtil.decrypt(stored);
        } catch (Exception e) {
            log.error("AES-256-GCM decryption failed for employe {}: {}",
                    employe.getId(), e.getMessage());
            return "";
        }
    }

    private String getCurrentAdminUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName())
                .map(u -> String.valueOf(u.getId()))
                .orElse(null);
    }

}
