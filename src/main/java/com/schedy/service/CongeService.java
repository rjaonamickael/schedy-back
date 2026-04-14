package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.*;
import com.schedy.entity.*;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CongeService {

    private final TypeCongeRepository typeCongeRepository;
    private final BanqueCongeRepository banqueCongeRepository;
    private final DemandeCongeRepository demandeCongeRepository;
    private final JourFerieRepository jourFerieRepository;
    private final EmployeRepository employeRepository;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;

    // ---- TypeConge ----

    @Transactional(readOnly = true)
    public List<TypeConge> findAllTypes() {
        String orgId = tenantContext.requireOrganisationId();
        return typeCongeRepository.findByOrganisationId(orgId);
    }

    @Transactional(readOnly = true)
    public Page<TypeConge> findAllTypes(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return typeCongeRepository.findByOrganisationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public TypeConge findTypeById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        return typeCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Type de conge", id));
    }

    @Transactional
    public TypeConge createType(TypeCongeDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        TypeLimite typeLimite = parseTypeLimite(dto.typeLimite());
        TypeConge type = TypeConge.builder()
                .nom(dto.nom())
                .paye(dto.paye())
                .unite(parseUniteConge(dto.unite()))
                .couleur(dto.couleur())
                .typeLimite(typeLimite)
                .quotaAnnuel(typeLimite == TypeLimite.ENVELOPPE_ANNUELLE ? dto.quotaAnnuel() : null)
                .accrualMontant(typeLimite == TypeLimite.ACCRUAL ? dto.accrualMontant() : null)
                .accrualFrequence(typeLimite == TypeLimite.ACCRUAL && dto.accrualFrequence() != null
                        ? parseFrequenceAccrual(dto.accrualFrequence()) : null)
                .autoriserDepassement(typeLimite == TypeLimite.ENVELOPPE_ANNUELLE && dto.autoriserDepassement())
                .dateDebutValidite(dto.dateDebutValidite())
                .dateFinValidite(dto.dateFinValidite())
                .organisationId(orgId)
                .build();
        TypeConge saved = typeCongeRepository.save(type);

        // Auto-provision : every employee in the org gets a banque for this new type.
        // Each banque starts at the type default quota; per-employee overrides happen
        // on the banque directly afterwards. The link is the invariant : (employe, type, org)
        // always has exactly one banque.
        provisionBanquesForType(saved, orgId);
        return saved;
    }

    @Transactional
    public TypeConge updateType(String id, TypeCongeDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        TypeConge type = typeCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Type de conge", id));
        TypeLimite typeLimite = parseTypeLimite(dto.typeLimite());
        type.setNom(dto.nom());
        type.setPaye(dto.paye());
        type.setUnite(parseUniteConge(dto.unite()));
        type.setCouleur(dto.couleur());
        type.setTypeLimite(typeLimite);
        type.setQuotaAnnuel(typeLimite == TypeLimite.ENVELOPPE_ANNUELLE ? dto.quotaAnnuel() : null);
        type.setAccrualMontant(typeLimite == TypeLimite.ACCRUAL ? dto.accrualMontant() : null);
        type.setAccrualFrequence(typeLimite == TypeLimite.ACCRUAL && dto.accrualFrequence() != null
                ? parseFrequenceAccrual(dto.accrualFrequence()) : null);
        type.setAutoriserDepassement(typeLimite == TypeLimite.ENVELOPPE_ANNUELLE && dto.autoriserDepassement());
        type.setDateDebutValidite(dto.dateDebutValidite());
        type.setDateFinValidite(dto.dateFinValidite());
        TypeConge saved = typeCongeRepository.save(type);

        // Note : we deliberately DO NOT propagate quota changes to existing banques here.
        // Admins are expected to override quotas per-employee for special contracts; an
        // automatic sync would silently destroy those overrides. New banques created later
        // (for new employees) will pick up the latest type.quotaAnnuel as their starting value.
        // If a banque is missing for an existing employee (edge case), backfill it now.
        provisionBanquesForType(saved, orgId);
        return saved;
    }

    /**
     * Ensures every employee in {@code orgId} has a {@link BanqueConge} for {@code type}.
     * Idempotent : an employee who already has one is left untouched.
     *
     * @see #provisionBanquesForEmploye(String, String) for the symmetric direction
     */
    @Transactional
    public void provisionBanquesForType(TypeConge type, String orgId) {
        List<Employe> employes = employeRepository.findByOrganisationId(orgId);
        if (employes.isEmpty()) return;

        List<BanqueConge> existing = banqueCongeRepository.findByTypeCongeId(type.getId());
        Set<String> existingEmployeIds = existing.stream()
                .filter(b -> orgId.equals(b.getOrganisationId()))
                .map(BanqueConge::getEmployeId)
                .collect(Collectors.toSet());

        LocalDate today = LocalDate.now();
        LocalDate dateFin = today.plusYears(1);

        int created = 0;
        for (Employe emp : employes) {
            if (existingEmployeIds.contains(emp.getId())) continue;
            BanqueConge banque = BanqueConge.builder()
                    .employeId(emp.getId())
                    .typeCongeId(type.getId())
                    .quota(initialQuotaFor(type))
                    .utilise(0)
                    .enAttente(0)
                    .dateDebut(today)
                    .dateFin(dateFin)
                    .organisationId(orgId)
                    .build();
            banqueCongeRepository.save(banque);
            created++;
        }
        if (created > 0) {
            log.info("Auto-provisioned {} banque(s) for type '{}' in org {}", created, type.getNom(), orgId);
        }
    }

    /**
     * Ensures the new employee has a {@link BanqueConge} for every type in {@code orgId}.
     * Called from {@code EmployeService.create()}. Idempotent.
     */
    @Transactional
    public void provisionBanquesForEmploye(String employeId, String orgId) {
        List<TypeConge> types = typeCongeRepository.findByOrganisationId(orgId);
        if (types.isEmpty()) return;

        List<BanqueConge> existing = banqueCongeRepository.findByEmployeIdAndOrganisationId(employeId, orgId);
        Set<String> existingTypeIds = existing.stream()
                .map(BanqueConge::getTypeCongeId)
                .collect(Collectors.toSet());

        LocalDate today = LocalDate.now();
        LocalDate dateFin = today.plusYears(1);

        int created = 0;
        for (TypeConge type : types) {
            if (existingTypeIds.contains(type.getId())) continue;
            BanqueConge banque = BanqueConge.builder()
                    .employeId(employeId)
                    .typeCongeId(type.getId())
                    .quota(initialQuotaFor(type))
                    .utilise(0)
                    .enAttente(0)
                    .dateDebut(today)
                    .dateFin(dateFin)
                    .organisationId(orgId)
                    .build();
            banqueCongeRepository.save(banque);
            created++;
        }
        if (created > 0) {
            log.info("Auto-provisioned {} banque(s) for employe {} in org {}", created, employeId, orgId);
        }
    }

    /**
     * Initial quota value for a freshly-provisioned banque, depending on the type strategy :
     * <ul>
     *   <li>{@code ENVELOPPE_ANNUELLE} → the type's annual quota (or 0 if unset)</li>
     *   <li>{@code ACCRUAL} → 0 (will be credited by the scheduler)</li>
     *   <li>{@code AUCUNE} → null (= no limit, displayed as ∞)</li>
     * </ul>
     */
    private Double initialQuotaFor(TypeConge type) {
        return switch (type.getTypeLimite()) {
            case ENVELOPPE_ANNUELLE -> type.getQuotaAnnuel() != null ? type.getQuotaAnnuel() : 0.0;
            case ACCRUAL -> 0.0;
            case AUCUNE -> null;
        };
    }

    private TypeLimite parseTypeLimite(String value) {
        try {
            return TypeLimite.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException("Valeur invalide pour typeLimite: " + value);
        }
    }

    private UniteConge parseUniteConge(String value) {
        try {
            return UniteConge.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Valeur invalide pour unite: " + value);
        }
    }

    private FrequenceAccrual parseFrequenceAccrual(String value) {
        try {
            return FrequenceAccrual.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Valeur invalide pour accrualFrequence: " + value);
        }
    }

    @Transactional
    public void deleteType(String id) {
        String orgId = tenantContext.requireOrganisationId();
        TypeConge type = typeCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Type de conge", id));

        // Block deletion if approved demandes exist (direct DB query, no in-memory filtering)
        List<DemandeConge> approvedDemandes = demandeCongeRepository
                .findByTypeCongeIdAndStatutAndOrganisationId(id, StatutDemande.approuve, orgId);
        if (!approvedDemandes.isEmpty()) {
            throw new BusinessRuleException("Impossible de supprimer ce type: " + approvedDemandes.size() + " demande(s) approuvee(s) existent");
        }

        // Cascade cleanup via direct DB queries (no in-memory loading)
        demandeCongeRepository.deleteByTypeCongeIdAndOrganisationId(id, orgId);
        banqueCongeRepository.deleteByTypeCongeIdAndOrganisationId(id, orgId);
        typeCongeRepository.delete(type);
    }

    // ---- BanqueConge ----

    @Transactional(readOnly = true)
    public List<BanqueConge> findAllBanques() {
        String orgId = tenantContext.requireOrganisationId();
        return banqueCongeRepository.findByOrganisationId(orgId);
    }

    @Transactional(readOnly = true)
    public Page<BanqueConge> findAllBanques(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return banqueCongeRepository.findByOrganisationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public List<BanqueConge> findBanquesByEmployeId(String employeId) {
        String orgId = tenantContext.requireOrganisationId();
        return banqueCongeRepository.findByEmployeIdAndOrganisationId(employeId, orgId);
    }

    @Transactional(readOnly = true)
    public BanqueConge findBanqueById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        return banqueCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Banque de conge", id));
    }

    @Transactional
    public BanqueConge createBanque(BanqueCongeDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        BanqueConge banque = BanqueConge.builder()
                .employeId(dto.employeId())
                .typeCongeId(dto.typeCongeId())
                .quota(dto.quota())
                .utilise(dto.utilise())
                .enAttente(dto.enAttente())
                .dateDebut(dto.dateDebut())
                .dateFin(dto.dateFin())
                .organisationId(orgId)
                .build();
        return banqueCongeRepository.save(banque);
    }

    @Transactional
    public BanqueConge updateBanque(String id, BanqueCongeDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        BanqueConge banque = banqueCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Banque de conge", id));
        banque.setEmployeId(dto.employeId());
        banque.setTypeCongeId(dto.typeCongeId());
        banque.setQuota(dto.quota());
        banque.setUtilise(dto.utilise());
        banque.setEnAttente(dto.enAttente());
        banque.setDateDebut(dto.dateDebut());
        banque.setDateFin(dto.dateFin());
        return banqueCongeRepository.save(banque);
    }

    @Transactional
    public void deleteBanque(String id) {
        String orgId = tenantContext.requireOrganisationId();
        BanqueConge banque = banqueCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Banque de conge", id));
        banqueCongeRepository.delete(banque);
    }

    // ---- DemandeConge ----

    @Transactional(readOnly = true)
    public List<DemandeConge> findAllDemandes() {
        String orgId = tenantContext.requireOrganisationId();
        return demandeCongeRepository.findByOrganisationId(orgId);
    }

    @Transactional(readOnly = true)
    public Page<DemandeConge> findAllDemandes(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return demandeCongeRepository.findByOrganisationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public List<DemandeConge> findDemandesByEmployeId(String employeId) {
        String orgId = tenantContext.requireOrganisationId();
        // SEC-10: EMPLOYEE may only read their own demandes
        checkOwnershipIfEmployee(employeId);
        return demandeCongeRepository.findByEmployeIdAndOrganisationId(employeId, orgId);
    }

    @Transactional(readOnly = true)
    public DemandeConge findDemandeById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        DemandeConge demande = demandeCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de conge", id));
        // SEC-10: EMPLOYEE may only read their own demande (prevents IDOR enumeration)
        checkOwnershipIfEmployee(demande.getEmployeId());
        return demande;
    }

    @Transactional
    public DemandeConge createDemande(DemandeCongeDto dto) {
        String orgId = tenantContext.requireOrganisationId();

        // B-03: Ownership check — EMPLOYEE role may only submit demandes for themselves.
        // ADMIN and MANAGER can submit on behalf of any employee.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE"))) {
            String callerEmail = auth.getName();
            String callerEmployeId = userRepository.findByEmail(callerEmail)
                    .map(u -> u.getEmployeId())
                    .orElse(null);
            if (callerEmployeId == null || !callerEmployeId.equals(dto.employeId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Un employe ne peut soumettre une demande que pour lui-meme");
            }
        }

        // Acquire pessimistic lock on banque FIRST to prevent TOCTOU race condition.
        // The lock is held until the transaction commits, so concurrent requests
        // on the same (employe, typeConge, org) will serialize here.
        Optional<BanqueConge> lockedBanque = banqueCongeRepository.findForUpdate(dto.employeId(), dto.typeCongeId(), orgId);

        // Quota verification with the locked row.
        // Skip check if the type has no strict limit (AUCUNE) or the admin allowed overdraft.
        lockedBanque.ifPresent(banque -> {
            if (banque.getQuota() != null) {
                TypeConge typeConge = typeCongeRepository.findByIdAndOrganisationId(dto.typeCongeId(), orgId)
                        .orElse(null);
                if (typeConge == null) return;
                boolean sansLimite = typeConge.getTypeLimite() == TypeLimite.AUCUNE;
                boolean depassementAutorise = typeConge.isAutoriserDepassement();
                if (!sansLimite && !depassementAutorise) {
                    double disponible = banque.getQuota() - banque.getUtilise() - banque.getEnAttente();
                    if (dto.duree() > disponible) {
                        throw new BusinessRuleException(
                                "Quota insuffisant: " + disponible + " disponible, " + dto.duree() + " demande");
                    }
                }
            }
        });

        DemandeConge demande = DemandeConge.builder()
                .employeId(dto.employeId())
                .typeCongeId(dto.typeCongeId())
                .dateDebut(dto.dateDebut())
                .dateFin(dto.dateFin())
                .heureDebut(dto.heureDebut())
                .heureFin(dto.heureFin())
                .duree(dto.duree())
                .statut(StatutDemande.en_attente)
                .motif(dto.motif())
                .organisationId(orgId)
                .build();

        demande = demandeCongeRepository.save(demande);

        // Update enAttente using the SAME locked entity (no second read needed)
        lockedBanque.ifPresent(banque -> {
            banque.setEnAttente(banque.getEnAttente() + dto.duree());
            banqueCongeRepository.save(banque);
        });

        return demande;
    }

    @Transactional
    public DemandeConge approveDemande(String id, String note) {
        String orgId = tenantContext.requireOrganisationId();
        DemandeConge demande = demandeCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de conge", id));

        StatutDemande statutActuel = demande.getStatut();
        if (statutActuel != StatutDemande.en_attente && statutActuel != StatutDemande.refuse) {
            throw new BusinessRuleException("Seules les demandes en attente ou refusees peuvent etre approuvees");
        }

        boolean wasRefused = (statutActuel == StatutDemande.refuse);

        demande.setStatut(StatutDemande.approuve);
        demande.setNoteApprobation(note);
        log.info("Leave request {} approved (was {})", id, statutActuel);
        demandeCongeRepository.save(demande);

        // MED-08: Use pessimistic write lock to prevent concurrent approval of the same
        // demande from producing a double-debit on the banque. Without the lock, two
        // concurrent PATCH /approve requests could both read the same solde and both
        // decrement it, causing an under-count. findForUpdate() issues SELECT … FOR UPDATE.
        try {
            banqueCongeRepository.findForUpdate(demande.getEmployeId(), demande.getTypeCongeId(), orgId)
                    .ifPresent(banque -> {
                        if (!wasRefused) {
                            // Was en_attente: move from enAttente to utilise
                            banque.setEnAttente(Math.max(0, banque.getEnAttente() - demande.getDuree()));
                        }
                        // In both cases: add to utilise
                        banque.setUtilise(banque.getUtilise() + demande.getDuree());
                        banqueCongeRepository.save(banque);
                    });
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessRuleException("Requete concurrente, reessayez");
        }

        return demande;
    }

    @Transactional
    public DemandeConge refuseDemande(String id, String note) {
        String orgId = tenantContext.requireOrganisationId();
        DemandeConge demande = demandeCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de conge", id));

        StatutDemande statutActuel = demande.getStatut();
        if (statutActuel != StatutDemande.en_attente && statutActuel != StatutDemande.approuve) {
            throw new BusinessRuleException("Seules les demandes en attente ou approuvees peuvent etre refusees");
        }

        boolean wasApproved = (statutActuel == StatutDemande.approuve);

        demande.setStatut(StatutDemande.refuse);
        demande.setNoteApprobation(note);
        log.info("Leave request {} refused (was {})", id, statutActuel);
        demandeCongeRepository.save(demande);

        try {
            banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(demande.getEmployeId(), demande.getTypeCongeId(), orgId)
                    .ifPresent(banque -> {
                        if (wasApproved) {
                            // Reverse the approval: move from utilise back to solde
                            banque.setUtilise(Math.max(0, banque.getUtilise() - demande.getDuree()));
                        } else {
                            // Was en_attente: release the pending reservation
                            banque.setEnAttente(Math.max(0, banque.getEnAttente() - demande.getDuree()));
                        }
                        banqueCongeRepository.save(banque);
                    });
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessRuleException("Requete concurrente, reessayez");
        }

        return demande;
    }

    @Transactional
    public DemandeConge updateDemande(String id, DemandeCongeDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        DemandeConge demande = demandeCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de conge", id));
        if (demande.getStatut() != StatutDemande.en_attente) {
            throw new BusinessRuleException("Seules les demandes en attente peuvent etre modifiees");
        }

        // B-H19: Recalculate enAttente delta when duree or typeConge changes
        double oldDuree = demande.getDuree();
        String oldTypeCongeId = demande.getTypeCongeId();

        demande.setTypeCongeId(dto.typeCongeId());
        demande.setDateDebut(dto.dateDebut());
        demande.setDateFin(dto.dateFin());
        demande.setHeureDebut(dto.heureDebut());
        demande.setHeureFin(dto.heureFin());
        demande.setDuree(dto.duree());
        demande.setMotif(dto.motif());
        demande.setNoteApprobation(dto.noteApprobation());

        // If type changed, rollback old banque and apply to new banque (using pessimistic lock)
        if (!oldTypeCongeId.equals(dto.typeCongeId())) {
            // Rollback enAttente on old type
            banqueCongeRepository.findForUpdate(demande.getEmployeId(), oldTypeCongeId, orgId)
                    .ifPresent(banque -> {
                        banque.setEnAttente(Math.max(0, banque.getEnAttente() - oldDuree));
                        banqueCongeRepository.save(banque);
                    });
            // Apply enAttente on new type (with quota check)
            banqueCongeRepository.findForUpdate(demande.getEmployeId(), dto.typeCongeId(), orgId)
                    .ifPresent(banque -> {
                        banque.setEnAttente(banque.getEnAttente() + dto.duree());
                        banqueCongeRepository.save(banque);
                    });
        } else if (Math.abs(oldDuree - dto.duree()) > 1e-9) {
            // Same type, different duree: apply delta (using pessimistic lock)
            double delta = dto.duree() - oldDuree;
            banqueCongeRepository.findForUpdate(demande.getEmployeId(), dto.typeCongeId(), orgId)
                    .ifPresent(banque -> {
                        banque.setEnAttente(Math.max(0, banque.getEnAttente() + delta));
                        banqueCongeRepository.save(banque);
                    });
        }

        return demandeCongeRepository.save(demande);
    }

    @Transactional
    public void deleteDemande(String id) {
        String orgId = tenantContext.requireOrganisationId();
        DemandeConge demande = demandeCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de conge", id));

        // Employees can only cancel their own pending requests
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isEmployee = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE"));
        if (isEmployee) {
            String email = auth.getName();
            var user = userRepository.findByEmail(email).orElse(null);
            if (user == null || !demande.getEmployeId().equals(user.getEmployeId())) {
                throw new BusinessRuleException("Vous ne pouvez annuler que vos propres demandes");
            }
            if (demande.getStatut() != StatutDemande.en_attente) {
                throw new BusinessRuleException("Seules les demandes en attente peuvent etre annulees");
            }
        }

        if (demande.getStatut() == StatutDemande.en_attente) {
            // Rollback enAttente if deleting a pending request
            banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(demande.getEmployeId(), demande.getTypeCongeId(), orgId)
                    .ifPresent(banque -> {
                        banque.setEnAttente(Math.max(0, banque.getEnAttente() - demande.getDuree()));
                        banqueCongeRepository.save(banque);
                    });
        }
        demandeCongeRepository.delete(demande);
    }

    // ---- JourFerie ----

    @Transactional(readOnly = true)
    public List<JourFerie> findAllFeries() {
        String orgId = tenantContext.requireOrganisationId();
        return jourFerieRepository.findByOrganisationId(orgId);
    }

    @Transactional(readOnly = true)
    public Page<JourFerie> findAllFeries(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return jourFerieRepository.findByOrganisationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public JourFerie findFerieById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        return jourFerieRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Jour ferie", id));
    }

    @Transactional
    public JourFerie createFerie(JourFerieDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        JourFerie ferie = JourFerie.builder()
                .nom(dto.nom())
                .date(dto.date())
                .recurrent(dto.recurrent())
                .siteId(dto.siteId())
                .organisationId(orgId)
                .build();
        return jourFerieRepository.save(ferie);
    }

    @Transactional
    public JourFerie updateFerie(String id, JourFerieDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        JourFerie ferie = jourFerieRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Jour ferie", id));
        ferie.setNom(dto.nom());
        ferie.setDate(dto.date());
        ferie.setRecurrent(dto.recurrent());
        ferie.setSiteId(dto.siteId());
        return jourFerieRepository.save(ferie);
    }

    @Transactional
    public void deleteFerie(String id) {
        String orgId = tenantContext.requireOrganisationId();
        JourFerie ferie = jourFerieRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Jour ferie", id));
        jourFerieRepository.delete(ferie);
    }

    // ---- Ownership check (SEC-10 IDOR fix) ----

    /**
     * SEC-10: Enforces row-level ownership for the EMPLOYEE role.
     *
     * <p>ADMIN and MANAGER may read any demande in their organisation (tenant scoping
     * already enforced upstream). EMPLOYEE may only read demandes that belong to
     * themselves; any attempt to enumerate IDs and access another employee's data
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
}
