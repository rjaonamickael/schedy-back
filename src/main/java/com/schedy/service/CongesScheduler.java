package com.schedy.service;

import com.schedy.entity.BanqueConge;
import com.schedy.entity.FrequenceAccrual;
import com.schedy.entity.Organisation;
import com.schedy.entity.TypeConge;
import com.schedy.entity.TypeLimite;
import com.schedy.repository.BanqueCongeRepository;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.TypeCongeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Daily scheduler for leave management :
 *
 * <ol>
 *   <li><b>Accrual</b> — credits banques of {@link TypeLimite#ACCRUAL} types on the
 *       1st of the month (monthly) or every Monday (weekly). Yearly accrual is
 *       handled by the renewal job below.</li>
 *   <li><b>Renewal</b> — on the day of {@link Organisation#getDateRenouvellementConges()},
 *       resets {@code utilise} and {@code enAttente} to 0 on all banques of
 *       {@link TypeLimite#ENVELOPPE_ANNUELLE} types in that organisation.
 *       Keeps the quota untouched (admins may have overridden per-employee).</li>
 * </ol>
 *
 * <p>Runs daily at 02:00. Each organisation is processed in its own transaction
 * so a failure on one tenant does not roll back the others.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CongesScheduler {

    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM-dd");

    private final TypeCongeRepository typeCongeRepository;
    private final BanqueCongeRepository banqueCongeRepository;
    private final OrganisationRepository organisationRepository;

    /**
     * Runs every day at 02:00. Does two passes in sequence : accrual, then renewal.
     * Split into a single scheduler to avoid two jobs competing on the same banques.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void runDailyCongesJob() {
        LocalDate today = LocalDate.now();
        log.info("CongesScheduler starting for {}", today);

        try {
            creditAccruals(today);
        } catch (Exception e) {
            log.error("Accrual pass failed: {}", e.getMessage(), e);
        }

        try {
            renewAnnualEnvelopes(today);
        } catch (Exception e) {
            log.error("Renewal pass failed: {}", e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // ACCRUAL
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Credits banques of ACCRUAL types according to their frequency :
     * monthly ones on the 1st, weekly ones every Monday. Yearly accruals
     * are credited at renewal time (below), not here.
     */
    void creditAccruals(LocalDate today) {
        boolean monthlyTick = today.getDayOfMonth() == 1;
        boolean weeklyTick = today.getDayOfWeek() == DayOfWeek.MONDAY;
        if (!monthlyTick && !weeklyTick) {
            return;
        }

        List<TypeConge> accrualTypes = typeCongeRepository.findByTypeLimite(TypeLimite.ACCRUAL);
        for (TypeConge type : accrualTypes) {
            if (type.getAccrualMontant() == null || type.getAccrualFrequence() == null) continue;

            boolean shouldTick = switch (type.getAccrualFrequence()) {
                case mensuel -> monthlyTick;
                case hebdomadaire -> weeklyTick;
                case annuel -> false; // handled by renewal
            };
            if (!shouldTick) continue;

            // Respect temporal validity
            if (!isActiveOn(type, today)) continue;

            try {
                creditBanquesForType(type);
            } catch (Exception e) {
                log.error("Failed to credit accrual for type {} (org {}): {}",
                        type.getId(), type.getOrganisationId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    void creditBanquesForType(TypeConge type) {
        List<BanqueConge> banques = banqueCongeRepository.findByTypeCongeId(type.getId());
        double montant = type.getAccrualMontant();
        int credited = 0;
        for (BanqueConge b : banques) {
            double current = b.getQuota() != null ? b.getQuota() : 0.0;
            b.setQuota(current + montant);
            banqueCongeRepository.save(b);
            credited++;
        }
        if (credited > 0) {
            log.info("Accrual: +{} credited to {} banque(s) of type {} (org {})",
                    montant, credited, type.getNom(), type.getOrganisationId());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // RENEWAL (annual envelope reset)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * For each organisation whose dateRenouvellementConges matches today's MM-DD,
     * resets utilise/enAttente to 0 on every banque linked to an ENVELOPPE_ANNUELLE type.
     * Leaves the quota untouched so admin per-employee overrides are preserved.
     */
    void renewAnnualEnvelopes(LocalDate today) {
        String todayKey = today.format(MM_DD);
        List<Organisation> orgs = organisationRepository.findByDateRenouvellementConges(todayKey);
        for (Organisation org : orgs) {
            try {
                resetOrgEnvelopes(org);
            } catch (Exception e) {
                log.error("Failed to renew envelopes for org {}: {}", org.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    void resetOrgEnvelopes(Organisation org) {
        List<TypeConge> annualTypes = typeCongeRepository.findByOrganisationIdAndTypeLimite(
                org.getId(), TypeLimite.ENVELOPPE_ANNUELLE);
        LocalDate today = LocalDate.now();
        LocalDate nextRenewal = today.plusYears(1);
        int reset = 0;
        for (TypeConge type : annualTypes) {
            List<BanqueConge> banques = banqueCongeRepository.findByTypeCongeId(type.getId());
            for (BanqueConge b : banques) {
                if (!org.getId().equals(b.getOrganisationId())) continue;
                // Reset counters
                b.setUtilise(0);
                b.setEnAttente(0);
                // Slide the validity window forward by one year so the banque tracks the
                // current period. Quota itself is preserved (admin overrides per-employee
                // are sacred — see provisionBanquesForType comment in CongeService).
                b.setDateDebut(today);
                b.setDateFin(nextRenewal);
                banqueCongeRepository.save(b);
                reset++;
            }
        }
        if (reset > 0) {
            log.info("Renewal: reset and slid {} banque(s) in org {} ({}) to [{} → {}]",
                    reset, org.getNom(), org.getId(), today, nextRenewal);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    /** Returns true if the type is within its optional validity window. */
    private static boolean isActiveOn(TypeConge type, LocalDate today) {
        LocalDate start = type.getDateDebutValidite();
        LocalDate end = type.getDateFinValidite();
        if (start != null && today.isBefore(start)) return false;
        if (end != null && today.isAfter(end)) return false;
        return true;
    }
}
