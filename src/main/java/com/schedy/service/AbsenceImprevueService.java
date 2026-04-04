package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.request.AbsenceImprevueRequest;
import com.schedy.dto.request.DecisionAbsenceRequest;
import com.schedy.dto.request.ReassignerCreneauRequest;
import com.schedy.entity.*;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AbsenceImprevueService {

    private static final int MAX_ACTIVE_ABSENCES_PER_WEEK = 3;

    private final AbsenceImprevueRepository absenceRepo;
    private final CreneauAssigneRepository creneauRepo;
    private final EmployeRepository employeRepo;
    private final UserRepository userRepo;
    private final ParametresRepository parametresRepo;
    private final TenantContext tenantContext;
    private final EmailService emailService;

    // ── Signaler une absence (dual mode EMPLOYEE / MANAGER) ──────

    @Transactional
    public AbsenceImprevue signalerAbsence(AbsenceImprevueRequest request) {
        String orgId = tenantContext.requireOrganisationId();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String callerEmail = auth.getName();
        boolean isEmployee = hasOnlyEmployeeRole(auth);

        // Résolution de l'employeId selon le rôle
        String targetEmployeId;
        AbsenceImprevue.Initiateur initiateur;

        if (isEmployee) {
            targetEmployeId = userRepo.findByEmail(callerEmail)
                    .map(User::getEmployeId)
                    .filter(eid -> eid != null && !eid.isBlank())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Votre compte n'est pas lié à un profil employé"));

            if (!targetEmployeId.equals(request.employeId())) {
                throw new AccessDeniedException(
                        "Un employé ne peut signaler une absence que pour lui-même");
            }
            initiateur = AbsenceImprevue.Initiateur.EMPLOYEE;
        } else {
            targetEmployeId = request.employeId();
            initiateur = AbsenceImprevue.Initiateur.MANAGER;
        }

        // Vérifier que l'employé existe dans l'org
        Employe employe = employeRepo.findByIdAndOrganisationId(targetEmployeId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", targetEmployeId));

        // Pas de doublon actif pour ce jour
        long existant = absenceRepo.countNonTerminalForEmployeeOnDate(targetEmployeId, orgId, request.dateAbsence());
        if (existant > 0) {
            throw new BusinessRuleException(
                    "Une absence est déjà signalée pour cet employé le " + request.dateAbsence());
        }

        // Anti-abus EMPLOYEE uniquement : max 3 absences actives / 7 jours glissants
        if (isEmployee) {
            LocalDate since = request.dateAbsence().minusDays(7);
            long recent = absenceRepo.countActiveAbsencesForEmployee(targetEmployeId, orgId, since);
            if (recent >= MAX_ACTIVE_ABSENCES_PER_WEEK) {
                throw new BusinessRuleException(
                        "Limite atteinte : " + MAX_ACTIVE_ABSENCES_PER_WEEK
                        + " absences actives sur 7 jours. Contactez votre manager directement.");
            }
        }

        // Auto-détection des créneaux impactés
        String semaine = toIsoWeek(request.dateAbsence());
        int jourOfWeek = request.dateAbsence().getDayOfWeek().getValue() - 1; // 0=Lundi

        List<String> creneauIds = request.creneauIds();
        if (creneauIds == null || creneauIds.isEmpty()) {
            creneauIds = creneauRepo
                    .findByEmployeIdAndSemaineAndOrganisationId(targetEmployeId, semaine, orgId)
                    .stream()
                    .filter(c -> c.getJour() == jourOfWeek)
                    .map(CreneauAssigne::getId)
                    .toList();
        }

        if (creneauIds.isEmpty()) {
            throw new BusinessRuleException(
                    "L'employé n'a aucun créneau planifié le " + request.dateAbsence());
        }

        // Déterminer le site (depuis le premier créneau)
        String siteId = creneauRepo.findByIdAndOrganisationId(creneauIds.get(0), orgId)
                .map(CreneauAssigne::getSiteId).orElse(null);

        // Statut initial : EMPLOYEE → SIGNALEE, MANAGER → VALIDEE (auto)
        StatutAbsenceImprevue statutInitial = isEmployee
                ? StatutAbsenceImprevue.SIGNALEE
                : StatutAbsenceImprevue.VALIDEE;

        AbsenceImprevue absence = AbsenceImprevue.builder()
                .employeId(targetEmployeId)
                .dateAbsence(request.dateAbsence())
                .motif(request.motif())
                .messageEmploye(request.messageEmploye())
                .signalePar(callerEmail)
                .initiateur(initiateur)
                .dateSignalement(Instant.now())
                .statut(statutInitial)
                .valideParEmail(isEmployee ? null : callerEmail)
                .dateValidation(isEmployee ? null : Instant.now())
                .creneauIds(creneauIds)
                .siteId(siteId)
                .organisationId(orgId)
                .build();

        absence = absenceRepo.save(absence);
        log.info("[Plan B] Absence signalée: id={}, employe={}, initiateur={}, statut={}, org={}",
                absence.getId(), targetEmployeId, initiateur, statutInitial, orgId);

        // Notifications selon l'initiateur
        if (isEmployee) {
            // L'employé signale sa propre absence → notifier les managers
            notifyManagers(absence, orgId, employe.getNom());
        } else {
            // Le manager crée l'absence pour l'employé → notifier l'employé absent
            // (statut déjà VALIDEE, l'email "absence validée" est sémantiquement correct)
            notifyEmployeeManagerInitiated(absence);
        }

        return absence;
    }

    // ── Valider (MANAGER) ────────────────────────────────────────

    @Transactional
    public AbsenceImprevue valider(String absenceId, DecisionAbsenceRequest request) {
        String orgId = tenantContext.requireOrganisationId();
        AbsenceImprevue absence = findAndCheckOrg(absenceId, orgId);

        if (absence.getStatut() != StatutAbsenceImprevue.SIGNALEE) {
            throw new BusinessRuleException(
                    "Seules les absences au statut SIGNALEE peuvent être validées");
        }

        String managerEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        absence.setStatut(StatutAbsenceImprevue.VALIDEE);
        absence.setValideParEmail(managerEmail);
        absence.setDateValidation(Instant.now());
        if (request != null && request.noteManager() != null) {
            absence.setNoteManager(request.noteManager());
        }

        absence = absenceRepo.save(absence);
        log.info("[Plan B] Absence {} validée par {}", absenceId, managerEmail);

        notifyEmployeeDecision(absence, true, null);
        return absence;
    }

    // ── Refuser (MANAGER) ────────────────────────────────────────

    @Transactional
    public AbsenceImprevue refuser(String absenceId, DecisionAbsenceRequest request) {
        String orgId = tenantContext.requireOrganisationId();
        AbsenceImprevue absence = findAndCheckOrg(absenceId, orgId);

        if (absence.getStatut() != StatutAbsenceImprevue.SIGNALEE) {
            throw new BusinessRuleException(
                    "Seules les absences au statut SIGNALEE peuvent être refusées");
        }

        if (request == null || request.noteRefus() == null || request.noteRefus().isBlank()) {
            throw new BusinessRuleException("Le motif de refus est obligatoire");
        }

        String managerEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        absence.setStatut(StatutAbsenceImprevue.REFUSEE);
        absence.setValideParEmail(managerEmail);
        absence.setDateValidation(Instant.now());
        absence.setNoteRefus(request.noteRefus());

        absence = absenceRepo.save(absence);
        log.info("[Plan B] Absence {} refusée par {}", absenceId, managerEmail);

        notifyEmployeeDecision(absence, false, request.noteRefus());
        return absence;
    }

    // ── Réassigner un créneau ────────────────────────────────────

    @Transactional
    public AbsenceImprevue reassignerCreneau(ReassignerCreneauRequest request) {
        String orgId = tenantContext.requireOrganisationId();

        AbsenceImprevue absence = findAndCheckOrg(request.absenceImprevueId(), orgId);

        // Auto-valider si encore SIGNALEE — l'action d'assigner implique l'acceptation
        if (absence.getStatut() == StatutAbsenceImprevue.SIGNALEE) {
            String managerEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            absence.setStatut(StatutAbsenceImprevue.VALIDEE);
            absence.setValideParEmail(managerEmail);
            absence.setDateValidation(Instant.now());
            absence = absenceRepo.save(absence);
            log.info("[Plan B] Absence {} auto-validée lors de la réassignation par {}", absence.getId(), managerEmail);
            notifyEmployeeDecision(absence, true, null);
        }

        if (absence.getStatut() != StatutAbsenceImprevue.VALIDEE
                && absence.getStatut() != StatutAbsenceImprevue.EN_COURS) {
            throw new BusinessRuleException(
                    "L'absence doit être validée avant de pouvoir réassigner des créneaux");
        }

        // Vérifier que le créneau fait partie des créneaux impactés
        if (!absence.getCreneauIds().contains(request.creneauId())) {
            throw new BusinessRuleException("Ce créneau n'est pas impacté par cette absence");
        }

        // Vérifier que le remplaçant existe
        employeRepo.findByIdAndOrganisationId(request.remplacantId(), orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", request.remplacantId()));

        // Réassigner le créneau
        CreneauAssigne creneau = creneauRepo.findByIdAndOrganisationId(request.creneauId(), orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Creneau", request.creneauId()));

        creneau.setEmployeId(request.remplacantId());
        creneauRepo.save(creneau);

        // Mettre à jour le statut de l'absence
        absence.setStatut(StatutAbsenceImprevue.EN_COURS);
        absence = absenceRepo.save(absence);

        log.info("[Plan B] Créneau {} réassigné à {} (absence {})",
                request.creneauId(), request.remplacantId(), absence.getId());

        return absence;
    }

    // ── Marquer comme traitée (tous les créneaux gérés) ──────────

    @Transactional
    public AbsenceImprevue marquerTraitee(String absenceId) {
        String orgId = tenantContext.requireOrganisationId();
        AbsenceImprevue absence = findAndCheckOrg(absenceId, orgId);

        if (absence.getStatut() != StatutAbsenceImprevue.VALIDEE
                && absence.getStatut() != StatutAbsenceImprevue.EN_COURS) {
            throw new BusinessRuleException("L'absence doit être validée ou en cours");
        }

        absence.setStatut(StatutAbsenceImprevue.TRAITEE);
        return absenceRepo.save(absence);
    }

    // ── Annuler ──────────────────────────────────────────────────

    @Transactional
    public AbsenceImprevue annuler(String absenceId) {
        String orgId = tenantContext.requireOrganisationId();
        AbsenceImprevue absence = findAndCheckOrg(absenceId, orgId);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isEmployee = hasOnlyEmployeeRole(auth);

        if (isEmployee) {
            String callerEmployeId = userRepo.findByEmail(auth.getName())
                    .map(User::getEmployeId).orElse(null);
            if (!absence.getEmployeId().equals(callerEmployeId)) {
                throw new AccessDeniedException("Vous ne pouvez annuler que vos propres absences");
            }
            if (absence.getStatut() != StatutAbsenceImprevue.SIGNALEE) {
                throw new BusinessRuleException(
                        "Vous ne pouvez annuler qu'une absence encore en attente de validation");
            }
        } else {
            if (absence.getStatut() == StatutAbsenceImprevue.TRAITEE
                    || absence.getStatut() == StatutAbsenceImprevue.ANNULEE) {
                throw new BusinessRuleException(
                        "Impossible d'annuler une absence au statut " + absence.getStatut());
            }
        }

        absence.setStatut(StatutAbsenceImprevue.ANNULEE);
        absence = absenceRepo.save(absence);
        log.info("[Plan B] Absence {} annulée par {}", absenceId, auth.getName());

        if (isEmployee) {
            notifyManagersAnnulation(absence, orgId);
        }

        return absence;
    }

    // ── Queries ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AbsenceImprevue> findMesAlertes() {
        String orgId = tenantContext.requireOrganisationId();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String employeId = userRepo.findByEmail(auth.getName())
                .map(User::getEmployeId)
                .filter(eid -> eid != null && !eid.isBlank())
                .orElseThrow(() -> new BusinessRuleException(
                        "Votre compte n'est pas lié à un profil employé"));
        return absenceRepo.findByEmployeIdAndOrganisationId(employeId, orgId);
    }

    @Transactional(readOnly = true)
    public List<AbsenceImprevue> findEnAttente() {
        String orgId = tenantContext.requireOrganisationId();
        return absenceRepo.findByStatutAndOrganisationId(StatutAbsenceImprevue.SIGNALEE, orgId);
    }

    @Transactional(readOnly = true)
    public List<AbsenceImprevue> findActives() {
        String orgId = tenantContext.requireOrganisationId();
        return absenceRepo.findByStatutInAndOrganisationId(
                List.of(StatutAbsenceImprevue.SIGNALEE,
                        StatutAbsenceImprevue.VALIDEE,
                        StatutAbsenceImprevue.EN_COURS),
                orgId);
    }

    @Transactional(readOnly = true)
    public AbsenceImprevue findById(String id) {
        return findAndCheckOrg(id, tenantContext.requireOrganisationId());
    }

    @Transactional(readOnly = true)
    public Page<AbsenceImprevue> findAll(Pageable pageable) {
        return absenceRepo.findByOrganisationId(tenantContext.requireOrganisationId(), pageable);
    }

    @Transactional(readOnly = true)
    public int getSeuilAbsenceVsConge(String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return loadParametres(orgId, siteId).getSeuilAbsenceVsCongeHeures();
    }

    // ── Helpers ──────────────────────────────────────────────────

    private AbsenceImprevue findAndCheckOrg(String id, String orgId) {
        return absenceRepo.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("AbsenceImprevue", id));
    }

    private boolean hasOnlyEmployeeRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE"))
                && auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private Parametres loadParametres(String orgId, String siteId) {
        if (siteId != null) {
            return parametresRepo.findBySiteIdAndOrganisationId(siteId, orgId)
                    .orElseGet(() -> parametresRepo.findBySiteIdIsNullAndOrganisationId(orgId)
                            .orElseGet(() -> Parametres.builder().build()));
        }
        return parametresRepo.findBySiteIdIsNullAndOrganisationId(orgId)
                .orElseGet(() -> Parametres.builder().build());
    }

    private static final List<User.UserRole> MANAGER_ROLES =
            List.of(User.UserRole.MANAGER, User.UserRole.ADMIN);

    private void notifyManagers(AbsenceImprevue absence, String orgId, String employeNom) {
        try {
            List<User> managers = userRepo.findByOrganisationIdAndRoleIn(orgId, MANAGER_ROLES);

            for (User manager : managers) {
                emailService.sendAbsenceSignaleeEmail(
                        manager.getEmail(), manager.getNom(), absence, employeNom);
            }
        } catch (Exception e) {
            log.error("[Plan B] Échec notification managers pour absence {}: {}",
                    absence.getId(), e.getMessage());
        }
    }

    private void notifyManagersAnnulation(AbsenceImprevue absence, String orgId) {
        try {
            Employe employe = employeRepo.findByIdAndOrganisationId(absence.getEmployeId(), orgId).orElse(null);
            String nom = employe != null ? employe.getNom() : absence.getEmployeId();

            List<User> managers = userRepo.findByOrganisationIdAndRoleIn(orgId, MANAGER_ROLES);

            for (User manager : managers) {
                emailService.sendAbsenceAnnuleeEmail(
                        manager.getEmail(), manager.getNom(), nom, absence.getDateAbsence().toString());
            }
        } catch (Exception e) {
            log.error("[Plan B] Échec notification annulation pour absence {}: {}",
                    absence.getId(), e.getMessage());
        }
    }

    private void notifyEmployeeManagerInitiated(AbsenceImprevue absence) {
        try {
            userRepo.findByEmployeId(absence.getEmployeId()).ifPresent(employee -> {
                // Réutilisation du template "absence validée" : le message indique déjà
                // "validée par votre manager. Un remplaçant sera assigné si nécessaire."
                // ce qui est sémantiquement correct pour une absence créée par le manager.
                emailService.sendAbsenceValideeEmail(
                        employee.getEmail(),
                        employee.getNom(),
                        absence.getDateAbsence().toString());
            });
        } catch (Exception e) {
            log.error("[Plan B] Échec notification employé (manager-initiated) pour absence {}: {}",
                    absence.getId(), e.getMessage());
        }
    }

    private void notifyEmployeeDecision(AbsenceImprevue absence, boolean accepted, String noteRefus) {
        try {
            userRepo.findByEmployeId(absence.getEmployeId()).ifPresent(employee -> {
                if (accepted) {
                    emailService.sendAbsenceValideeEmail(
                            employee.getEmail(), employee.getNom(), absence.getDateAbsence().toString());
                } else {
                    emailService.sendAbsenceRefuseeEmail(
                            employee.getEmail(), employee.getNom(),
                            absence.getDateAbsence().toString(), noteRefus);
                }
            });
        } catch (Exception e) {
            log.error("[Plan B] Échec notification employé pour absence {}: {}",
                    absence.getId(), e.getMessage());
        }
    }

    private String toIsoWeek(LocalDate date) {
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }
}
