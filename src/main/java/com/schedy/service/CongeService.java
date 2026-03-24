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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CongeService {

    private final TypeCongeRepository typeCongeRepository;
    private final BanqueCongeRepository banqueCongeRepository;
    private final DemandeCongeRepository demandeCongeRepository;
    private final JourFerieRepository jourFerieRepository;
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
        TypeConge type = TypeConge.builder()
                .nom(dto.nom())
                .categorie(parseCategorieConge(dto.categorie()))
                .unite(parseUniteConge(dto.unite()))
                .couleur(dto.couleur())
                .modeQuota(dto.modeQuota())
                .quotaIllimite(dto.quotaIllimite())
                .autoriserNegatif(dto.autoriserNegatif())
                .accrualMontant(dto.accrualMontant())
                .accrualFrequence(dto.accrualFrequence() != null ? parseFrequenceAccrual(dto.accrualFrequence()) : null)
                .reportMax(dto.reportMax())
                .reportDuree(dto.reportDuree())
                .organisationId(orgId)
                .build();
        return typeCongeRepository.save(type);
    }

    @Transactional
    public TypeConge updateType(String id, TypeCongeDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        TypeConge type = typeCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Type de conge", id));
        type.setNom(dto.nom());
        type.setCategorie(parseCategorieConge(dto.categorie()));
        type.setUnite(parseUniteConge(dto.unite()));
        type.setCouleur(dto.couleur());
        type.setModeQuota(dto.modeQuota());
        type.setQuotaIllimite(dto.quotaIllimite());
        type.setAutoriserNegatif(dto.autoriserNegatif());
        type.setAccrualMontant(dto.accrualMontant());
        type.setAccrualFrequence(dto.accrualFrequence() != null ? parseFrequenceAccrual(dto.accrualFrequence()) : null);
        type.setReportMax(dto.reportMax());
        type.setReportDuree(dto.reportDuree());
        return typeCongeRepository.save(type);
    }

    private CategorieConge parseCategorieConge(String value) {
        try {
            return CategorieConge.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Valeur invalide pour categorie: " + value);
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
        return demandeCongeRepository.findByEmployeIdAndOrganisationId(employeId, orgId);
    }

    @Transactional(readOnly = true)
    public DemandeConge findDemandeById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        return demandeCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de conge", id));
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

        // Quota verification with the locked row
        lockedBanque.ifPresent(banque -> {
            if (banque.getQuota() != null) {
                TypeConge typeConge = typeCongeRepository.findByIdAndOrganisationId(dto.typeCongeId(), orgId)
                        .orElse(null);
                boolean autoriserNegatif = typeConge != null && typeConge.isAutoriserNegatif();
                boolean quotaIllimite = typeConge != null && typeConge.isQuotaIllimite();
                if (!quotaIllimite && !autoriserNegatif) {
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
        if (demande.getStatut() != StatutDemande.en_attente) {
            throw new BusinessRuleException("Seules les demandes en attente peuvent etre approuvees");
        }

        demande.setStatut(StatutDemande.approuve);
        demande.setNoteApprobation(note);
        log.info("Leave request {} approved", id);
        demandeCongeRepository.save(demande);

        // Move from enAttente to utilise (optimistic locking guards against concurrent quota updates)
        try {
            banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(demande.getEmployeId(), demande.getTypeCongeId(), orgId)
                    .ifPresent(banque -> {
                        banque.setEnAttente(Math.max(0, banque.getEnAttente() - demande.getDuree()));
                        banque.setUtilise(banque.getUtilise() + demande.getDuree());
                        banqueCongeRepository.save(banque);
                    });
        } catch (ObjectOptimisticLockingFailureException e) {
            // B-18: Use Spring's wrapper rather than JPA's raw OptimisticLockException
            throw new BusinessRuleException("Requete concurrente, reessayez");
        }

        return demande;
    }

    @Transactional
    public DemandeConge refuseDemande(String id, String note) {
        String orgId = tenantContext.requireOrganisationId();
        DemandeConge demande = demandeCongeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de conge", id));
        if (demande.getStatut() != StatutDemande.en_attente) {
            throw new BusinessRuleException("Seules les demandes en attente peuvent etre refusees");
        }

        demande.setStatut(StatutDemande.refuse);
        demande.setNoteApprobation(note);
        log.info("Leave request {} refused", id);
        demandeCongeRepository.save(demande);

        // Rollback enAttente (optimistic locking guards against concurrent quota updates)
        try {
            banqueCongeRepository.findByEmployeIdAndTypeCongeIdAndOrganisationId(demande.getEmployeId(), demande.getTypeCongeId(), orgId)
                    .ifPresent(banque -> {
                        banque.setEnAttente(Math.max(0, banque.getEnAttente() - demande.getDuree()));
                        banqueCongeRepository.save(banque);
                    });
        } catch (ObjectOptimisticLockingFailureException e) {
            // B-18: Use Spring's wrapper rather than JPA's raw OptimisticLockException
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
}
