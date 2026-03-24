package com.schedy.config;

import com.schedy.entity.*;
import com.schedy.repository.*;
import com.schedy.service.EmployeService;
import com.schedy.service.PointageCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Profile({"dev", "test"})
public class DataInitializer implements CommandLineRunner {

    private final OrganisationRepository organisationRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmployeRepository employeRepository;
    private final ExigenceRepository exigenceRepository;
    private final TypeCongeRepository typeCongeRepository;
    private final BanqueCongeRepository banqueCongeRepository;
    private final DemandeCongeRepository demandeCongeRepository;
    private final JourFerieRepository jourFerieRepository;
    private final PointageRepository pointageRepository;
    private final CreneauAssigneRepository creneauAssigneRepository;
    private final ParametresRepository parametresRepository;
    private final PointageCodeRepository pointageCodeRepository;
    private final PointageCodeService pointageCodeService;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionRepository subscriptionRepository;
    private final PromoCodeRepository promoCodeRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (organisationRepository.count() > 0) {
            log.info("Data already initialized, skipping...");
            return;
        }

        log.info("Initializing seed data...");

        // 1. Organisations
        Organisation entreprise = createOrganisation("Entreprise", "entreprise.com");
        Organisation company = createOrganisation("Company", "company.com");

        // 2. Sites
        Site siegeEntreprise = createSite("Siège Entreprise", "Antananarivo Centre", entreprise.getId());
        Site centreville = createSite("Centre-Ville", "12 Avenue de l'Indépendance, Antananarivo", company.getId());
        Site banlieue = createSite("Banlieue Sud", "Route d'Imerintsiatosika, Antananarivo", company.getId());
        Site zoneIndustrielle = createSite("Zone Industrielle", "Zone Industrielle Ankorondrano, Antananarivo", company.getId());

        // 3. Roles
        createRoles(entreprise.getId(), company.getId());

        // 4. Employés Entreprise + Users
        createEntrepriseEmployees(entreprise, siegeEntreprise);

        // 5. Employés Company + Users
        createCompanyEmployees(company, centreville, banlieue, zoneIndustrielle);

        // 6. Admin & Manager Users
        createAdminAndManagerUsers(entreprise, company);

        // 7. Exigences
        createExigences(entreprise, siegeEntreprise, company, centreville, banlieue, zoneIndustrielle);

        // 8. Types de congé
        List<TypeConge> typesConge = createTypesConge(entreprise.getId(), company.getId());

        // 9. Banques de congé
        createBanquesConge(typesConge, entreprise.getId(), company.getId());

        // 10. Jours fériés
        createJoursFeries(entreprise.getId(), company.getId());

        // 11. Paramètres
        createParametres(entreprise, siegeEntreprise, company, centreville, banlieue, zoneIndustrielle);

        // 12. Créneaux de test (semaine courante)
        createCreneauxTest(entreprise, siegeEntreprise, company, centreville, banlieue, zoneIndustrielle);

        // 13. Pointages de test
        createPointagesTest(entreprise, siegeEntreprise, company, centreville);

        // 14. Demandes de congé de test
        createDemandesCongeTest(typesConge, entreprise.getId(), company.getId());

        // 15. Pointage codes
        createPointageCodes(entreprise, siegeEntreprise, company, centreville, banlieue, zoneIndustrielle);

        // 16. SUPERADMIN platform user
        createSuperAdmin();

        // 17. Beta promo codes
        createBetaPromoCodes();

        log.info("Seed data initialized successfully!");
    }

    // ========== ORGANISATIONS ==========

    private Organisation createOrganisation(String nom, String domaine) {
        Organisation org = Organisation.builder()
                .nom(nom)
                .domaine(domaine)
                .build();
        org = organisationRepository.save(org);
        log.info("Organisation created: {} ({})", nom, domaine);
        return org;
    }

    // ========== SITES ==========

    private Site createSite(String nom, String adresse, String organisationId) {
        Site site = Site.builder()
                .nom(nom)
                .adresse(adresse)
                .organisationId(organisationId)
                .actif(true)
                .build();
        site = siteRepository.save(site);
        log.info("Site created: {} (org: {})", nom, organisationId);
        return site;
    }

    // ========== ROLES ==========

    private void createRoles(String entrepriseOrgId, String companyOrgId) {
        if (roleRepository.count() > 0) {
            return;
        }

        String[][] rolesData = {
                {"Caissier", "1", "#4CAF50"},
                {"Vendeur", "2", "#2196F3"},
                {"Responsable rayon", "3", "#FF9800"},
                {"Agent de sécurité", "4", "#F44336"},
                {"Manutentionnaire", "5", "#795548"},
                {"Accueil", "6", "#9C27B0"},
                {"Chef de caisse", "7", "#E91E63"},
                {"Magasinier", "8", "#607D8B"}
        };

        // Create roles for both organisations
        for (String orgId : List.of(entrepriseOrgId, companyOrgId)) {
            for (String[] data : rolesData) {
                Role role = Role.builder()
                        .nom(data[0])
                        .importance(Integer.parseInt(data[1]))
                        .couleur(data[2])
                        .organisationId(orgId)
                        .build();
                roleRepository.save(role);
            }
        }
        log.info("Roles created: {} (per org)", rolesData.length);
    }

    // ========== EMPLOYES ENTREPRISE ==========

    private void createEntrepriseEmployees(Organisation entreprise, Site siege) {
        String orgId = entreprise.getId();
        String siteId = siege.getId();

        createEmploye("ent-1", "Rakoto Jean", "Caissier", "jean.rakoto@entreprise.com",
                "+261341000001", "1234", orgId, List.of(siteId), 8, 17);
        createEmploye("ent-2", "Rabe Marie", "Vendeur", "marie.rabe@entreprise.com",
                "+261341000002", "5678", orgId, List.of(siteId), 9, 18);
        createEmploye("ent-3", "Andria Paul", "Responsable rayon", "paul.andria@entreprise.com",
                "+261341000003", "9012", orgId, List.of(siteId), 7, 16);
        createEmploye("ent-4", "Rasoa Nadia", "Caissier", "nadia.rasoa@entreprise.com",
                "+261341000004", "3456", orgId, List.of(siteId), 8, 17);
        createEmploye("ent-5", "Randria Hery", "Manutentionnaire", "hery.randria@entreprise.com",
                "+261341000005", "7890", orgId, List.of(siteId), 6, 14);

        // Employee user accounts
        createUser("jean.rakoto@entreprise.com", "employe123", User.UserRole.EMPLOYEE, "ent-1", orgId);
        createUser("marie.rabe@entreprise.com", "employe123", User.UserRole.EMPLOYEE, "ent-2", orgId);
        createUser("paul.andria@entreprise.com", "employe123", User.UserRole.EMPLOYEE, "ent-3", orgId);
        createUser("nadia.rasoa@entreprise.com", "employe123", User.UserRole.EMPLOYEE, "ent-4", orgId);
        createUser("hery.randria@entreprise.com", "employe123", User.UserRole.EMPLOYEE, "ent-5", orgId);

        log.info("Entreprise employees created: 5");
    }

    // ========== EMPLOYES COMPANY ==========

    private void createCompanyEmployees(Organisation company, Site centreville, Site banlieue, Site zoneIndustrielle) {
        String orgId = company.getId();
        String cvId = centreville.getId();
        String bsId = banlieue.getId();
        String ziId = zoneIndustrielle.getId();

        // Centre-Ville
        createEmploye("comp-1", "Raharison Luc", "Caissier", "luc.raharison@company.com",
                "+261342000001", "1111", orgId, List.of(cvId), 8, 17);
        createEmploye("comp-2", "Razafin Soa", "Vendeur", "soa.razafin@company.com",
                "+261342000002", "2222", orgId, List.of(cvId), 9, 18);
        createEmploye("comp-3", "Ravelona Aina", "Chef de caisse", "aina.ravelona@company.com",
                "+261342000003", "3333", orgId, List.of(cvId), 8, 17);
        createEmploye("comp-4", "Randriam Fidy", "Agent de sécurité", "fidy.randriam@company.com",
                "+261342000004", "4444", orgId, List.of(cvId), 7, 19);

        // Banlieue Sud
        createEmploye("comp-5", "Rakotoson Haja", "Vendeur", "haja.rakotoson@company.com",
                "+261342000005", "5555", orgId, List.of(bsId), 9, 18);
        createEmploye("comp-6", "Rasoar Vola", "Caissier", "vola.rasoar@company.com",
                "+261342000006", "6666", orgId, List.of(bsId), 8, 17);
        createEmploye("comp-7", "Andrianaivo Tiana", "Accueil", "tiana.andrianaivo@company.com",
                "+261342000007", "7777", orgId, List.of(bsId), 8, 18);

        // Zone Industrielle
        createEmploye("comp-8", "Rajaonarivelo Mamy", "Magasinier", "mamy.rajaonarivelo@company.com",
                "+261342000008", "8888", orgId, List.of(ziId), 6, 14);
        createEmploye("comp-9", "Ratsimba Koto", "Manutentionnaire", "koto.ratsimba@company.com",
                "+261342000009", "9999", orgId, List.of(ziId), 6, 14);
        createEmploye("comp-10", "Raharijaona Bako", "Responsable rayon", "bako.raharijaona@company.com",
                "+261342000010", "1010", orgId, List.of(ziId), 7, 16);

        // Multi-sites (mobile)
        createEmploye("comp-11", "Razafimahatratra Lova", "Vendeur", "lova.razafi@company.com",
                "+261342000011", "1122", orgId, List.of(cvId, bsId), 9, 18);
        createEmploye("comp-12", "Andriamanalina Ny Aina", "Agent de sécurité", "nyaina.andria@company.com",
                "+261342000012", "1133", orgId, List.of(cvId, ziId), 7, 19);

        // Employee user accounts
        createUser("luc.raharison@company.com", "employe123", User.UserRole.EMPLOYEE, "comp-1", orgId);
        createUser("soa.razafin@company.com", "employe123", User.UserRole.EMPLOYEE, "comp-2", orgId);
        createUser("aina.ravelona@company.com", "employe123", User.UserRole.EMPLOYEE, "comp-3", orgId);
        createUser("fidy.randriam@company.com", "employe123", User.UserRole.EMPLOYEE, "comp-4", orgId);
        createUser("haja.rakotoson@company.com", "employe123", User.UserRole.EMPLOYEE, "comp-5", orgId);
        createUser("vola.rasoar@company.com", "employe123", User.UserRole.EMPLOYEE, "comp-6", orgId);
        createUser("tiana.andrianaivo@company.com", "employe123", User.UserRole.EMPLOYEE, "comp-7", orgId);
        createUser("mamy.rajaonarivelo@company.com", "employe123", User.UserRole.EMPLOYEE, "comp-8", orgId);
        createUser("koto.ratsimba@company.com", "employe123", User.UserRole.EMPLOYEE, "comp-9", orgId);
        createUser("bako.raharijaona@company.com", "employe123", User.UserRole.EMPLOYEE, "comp-10", orgId);
        createUser("lova.razafi@company.com", "employe123", User.UserRole.EMPLOYEE, "comp-11", orgId);
        createUser("nyaina.andria@company.com", "employe123", User.UserRole.EMPLOYEE, "comp-12", orgId);

        log.info("Company employees created: 12");
    }

    // ========== ADMIN & MANAGER USERS ==========

    private void createAdminAndManagerUsers(Organisation entreprise, Organisation company) {
        String entId = entreprise.getId();
        String compId = company.getId();

        // Entreprise
        createUser("admin@entreprise.com", "admin123", User.UserRole.ADMIN, null, entId);
        createUser("manager@entreprise.com", "manager123", User.UserRole.MANAGER, null, entId);

        // Company
        createUser("admin@company.com", "admin123", User.UserRole.ADMIN, null, compId);
        createUser("manager.centre@company.com", "manager123", User.UserRole.MANAGER, null, compId);
        createUser("manager.sud@company.com", "manager123", User.UserRole.MANAGER, null, compId);

        log.info("Admin and manager users created");
    }

    // ========== EXIGENCES ==========

    private void createExigences(Organisation entreprise, Site siege, Organisation company, Site centreville, Site banlieue, Site zoneIndustrielle) {
        List<Integer> lunVen = Arrays.asList(1, 2, 3, 4, 5);

        // Siège Entreprise
        createExigence("Caisse matin", lunVen, 8, 13, "Caissier", 2, siege.getId(), entreprise.getId());
        createExigence("Vente après-midi", lunVen, 13, 18, "Vendeur", 1, siege.getId(), entreprise.getId());
        createExigence("Manutention matin", lunVen, 6, 12, "Manutentionnaire", 1, siege.getId(), entreprise.getId());

        // Centre-Ville Company
        createExigence("Caisse journée", lunVen, 8, 18, "Caissier", 2, centreville.getId(), company.getId());
        createExigence("Vente matin", lunVen, 9, 13, "Vendeur", 2, centreville.getId(), company.getId());
        createExigence("Vente après-midi", lunVen, 13, 18, "Vendeur", 2, centreville.getId(), company.getId());
        createExigence("Sécurité", lunVen, 7, 19, "Agent de sécurité", 1, centreville.getId(), company.getId());
        createExigence("Accueil", lunVen, 8, 18, "Accueil", 1, centreville.getId(), company.getId());

        // Banlieue Sud Company
        createExigence("Caisse", lunVen, 9, 17, "Caissier", 1, banlieue.getId(), company.getId());
        createExigence("Vente", lunVen, 9, 17, "Vendeur", 1, banlieue.getId(), company.getId());
        createExigence("Accueil", lunVen, 9, 17, "Accueil", 1, banlieue.getId(), company.getId());

        // Zone Industrielle Company
        createExigence("Manutention matin", lunVen, 6, 12, "Manutentionnaire", 2, zoneIndustrielle.getId(), company.getId());
        createExigence("Magasin", lunVen, 6, 14, "Magasinier", 1, zoneIndustrielle.getId(), company.getId());
        createExigence("Supervision", lunVen, 7, 16, "Responsable rayon", 1, zoneIndustrielle.getId(), company.getId());

        log.info("Exigences created: 14");
    }

    // ========== TYPES DE CONGE ==========

    private List<TypeConge> createTypesConge(String entrepriseOrgId, String companyOrgId) {
        if (typeCongeRepository.count() > 0) {
            return typeCongeRepository.findAll();
        }

        java.util.ArrayList<TypeConge> allTypes = new java.util.ArrayList<>();

        for (String orgId : List.of(entrepriseOrgId, companyOrgId)) {
            TypeConge congesPayes = TypeConge.builder()
                    .nom("Congé payé")
                    .categorie(CategorieConge.paye)
                    .unite(UniteConge.jours)
                    .couleur("#4CAF50")
                    .modeQuota("annuel")
                    .quotaIllimite(false)
                    .autoriserNegatif(false)
                    .organisationId(orgId)
                    .build();

            TypeConge congeSansSolde = TypeConge.builder()
                    .nom("Congé sans solde")
                    .categorie(CategorieConge.non_paye)
                    .unite(UniteConge.jours)
                    .couleur("#FF9800")
                    .modeQuota("illimite")
                    .quotaIllimite(true)
                    .autoriserNegatif(false)
                    .organisationId(orgId)
                    .build();

            TypeConge arretMaladie = TypeConge.builder()
                    .nom("Arrêt maladie")
                    .categorie(CategorieConge.paye)
                    .unite(UniteConge.jours)
                    .couleur("#F44336")
                    .modeQuota("illimite")
                    .quotaIllimite(true)
                    .autoriserNegatif(false)
                    .organisationId(orgId)
                    .build();

            TypeConge congeMaternite = TypeConge.builder()
                    .nom("Congé maternité")
                    .categorie(CategorieConge.paye)
                    .unite(UniteConge.jours)
                    .couleur("#E91E63")
                    .modeQuota("evenement")
                    .quotaIllimite(false)
                    .autoriserNegatif(false)
                    .organisationId(orgId)
                    .build();

            congesPayes = typeCongeRepository.save(congesPayes);
            congeSansSolde = typeCongeRepository.save(congeSansSolde);
            arretMaladie = typeCongeRepository.save(arretMaladie);
            congeMaternite = typeCongeRepository.save(congeMaternite);

            allTypes.addAll(List.of(congesPayes, congeSansSolde, arretMaladie, congeMaternite));
        }

        log.info("Types de congé created: {} (per org: 4)", allTypes.size());
        return allTypes;
    }

    // ========== BANQUES DE CONGE ==========

    private void createBanquesConge(List<TypeConge> typesConge, String entrepriseOrgId, String companyOrgId) {
        if (banqueCongeRepository.count() > 0) {
            return;
        }

        // typesConge: first 4 are entreprise, next 4 are company (both start with "Congé payé")
        TypeConge congesPayesEntreprise = typesConge.get(0);
        TypeConge congesPayesCompany = typesConge.get(4);
        LocalDate debutAnnee = LocalDate.of(2026, 1, 1);
        LocalDate finAnnee = LocalDate.of(2026, 12, 31);

        // Entreprise employees
        String[] entrepriseEmployeeIds = {"ent-1", "ent-2", "ent-3", "ent-4", "ent-5"};
        for (String empId : entrepriseEmployeeIds) {
            BanqueConge banque = BanqueConge.builder()
                    .employeId(empId)
                    .typeCongeId(congesPayesEntreprise.getId())
                    .quota(20.0)
                    .utilise(0)
                    .enAttente(0)
                    .dateDebut(debutAnnee)
                    .dateFin(finAnnee)
                    .organisationId(entrepriseOrgId)
                    .build();
            banqueCongeRepository.save(banque);
        }

        // Company employees
        String[] companyEmployeeIds = {
                "comp-1", "comp-2", "comp-3", "comp-4", "comp-5", "comp-6",
                "comp-7", "comp-8", "comp-9", "comp-10", "comp-11", "comp-12"
        };
        for (String empId : companyEmployeeIds) {
            BanqueConge banque = BanqueConge.builder()
                    .employeId(empId)
                    .typeCongeId(congesPayesCompany.getId())
                    .quota(20.0)
                    .utilise(0)
                    .enAttente(0)
                    .dateDebut(debutAnnee)
                    .dateFin(finAnnee)
                    .organisationId(companyOrgId)
                    .build();
            banqueCongeRepository.save(banque);
        }

        log.info("Banques de congé created: {}", entrepriseEmployeeIds.length + companyEmployeeIds.length);
    }

    // ========== JOURS FERIES ==========

    private void createJoursFeries(String entrepriseOrgId, String companyOrgId) {
        if (jourFerieRepository.count() > 0) {
            return;
        }

        int year = LocalDate.now().getYear();

        Object[][] feries = {
                {"Nouvel An", LocalDate.of(year, 1, 1), true},
                {"Journée des Martyrs", LocalDate.of(year, 3, 29), true},
                {"Fête du Travail", LocalDate.of(year, 5, 1), true},
                {"Fête de l'Indépendance", LocalDate.of(year, 6, 26), true},
                {"Assomption", LocalDate.of(year, 8, 15), true},
                {"Toussaint", LocalDate.of(year, 11, 1), true},
                {"Noël", LocalDate.of(year, 12, 25), true}
        };

        // Create holidays for both organisations
        for (String orgId : List.of(entrepriseOrgId, companyOrgId)) {
            for (Object[] f : feries) {
                JourFerie jf = JourFerie.builder()
                        .nom((String) f[0])
                        .date((LocalDate) f[1])
                        .recurrent((boolean) f[2])
                        .organisationId(orgId)
                        .build();
                jourFerieRepository.save(jf);
            }
        }

        log.info("Jours fériés created: {} (per org)", feries.length);
    }

    // ========== PARAMETRES ==========

    private void createParametres(Organisation entreprise, Site siege, Organisation company, Site centreville, Site banlieue, Site zoneIndustrielle) {
        if (parametresRepository.count() > 0) {
            return;
        }

        List<Integer> joursActifs = Arrays.asList(1, 2, 3, 4, 5);

        // Entreprise site
        Parametres paramsSiege = Parametres.builder()
                .heureDebut(6)
                .heureFin(22)
                .joursActifs(joursActifs)
                .premierJour(1)
                .dureeMinAffectation(1.0)
                .siteId(siege.getId())
                .organisationId(entreprise.getId())
                .build();
        parametresRepository.save(paramsSiege);

        // Company sites
        for (Site site : List.of(centreville, banlieue, zoneIndustrielle)) {
            Parametres params = Parametres.builder()
                    .heureDebut(6)
                    .heureFin(22)
                    .joursActifs(joursActifs)
                    .premierJour(1)
                    .dureeMinAffectation(1.0)
                    .siteId(site.getId())
                    .organisationId(company.getId())
                    .build();
            parametresRepository.save(params);
        }

        log.info("Parametres created for 4 sites");
    }

    // ========== CRENEAUX DE TEST ==========

    private void createCreneauxTest(Organisation entreprise, Site siege, Organisation company, Site centreville, Site banlieue, Site zoneIndustrielle) {
        if (creneauAssigneRepository.count() > 0) {
            return;
        }

        // Current week string (format: yyyy-Www, e.g. 2026-W12)
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weekNumber = monday.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int weekYear = monday.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
        String semaine = String.format("%d-W%02d", weekYear, weekNumber);

        String entOrgId = entreprise.getId();
        String compOrgId = company.getId();

        // Siège Entreprise - Lundi à Vendredi
        for (int jour = 1; jour <= 5; jour++) {
            createCreneau("ent-1", jour, 8, 13, semaine, siege.getId(), entOrgId);   // Rakoto Jean - Caissier matin
            createCreneau("ent-4", jour, 8, 13, semaine, siege.getId(), entOrgId);   // Rasoa Nadia - Caissier matin
            createCreneau("ent-2", jour, 13, 18, semaine, siege.getId(), entOrgId);  // Rabe Marie - Vendeur après-midi
            createCreneau("ent-5", jour, 6, 12, semaine, siege.getId(), entOrgId);   // Randria Hery - Manutention matin
        }

        // Centre-Ville Company - Lundi à Vendredi
        for (int jour = 1; jour <= 5; jour++) {
            createCreneau("comp-1", jour, 8, 18, semaine, centreville.getId(), compOrgId);  // Raharison Luc - Caisse journée
            createCreneau("comp-3", jour, 8, 17, semaine, centreville.getId(), compOrgId);  // Ravelona Aina - Chef caisse
            createCreneau("comp-2", jour, 9, 13, semaine, centreville.getId(), compOrgId);  // Razafin Soa - Vente matin
            createCreneau("comp-11", jour, 13, 18, semaine, centreville.getId(), compOrgId); // Razafi Lova - Vente après-midi
            createCreneau("comp-4", jour, 7, 19, semaine, centreville.getId(), compOrgId);  // Randriam Fidy - Sécurité
        }

        // Banlieue Sud Company - Lundi à Vendredi
        for (int jour = 1; jour <= 5; jour++) {
            createCreneau("comp-6", jour, 9, 17, semaine, banlieue.getId(), compOrgId);  // Rasoar Vola - Caisse
            createCreneau("comp-5", jour, 9, 17, semaine, banlieue.getId(), compOrgId);  // Rakotoson Haja - Vente
            createCreneau("comp-7", jour, 9, 17, semaine, banlieue.getId(), compOrgId);  // Andrianaivo Tiana - Accueil
        }

        // Zone Industrielle Company - Lundi à Vendredi
        for (int jour = 1; jour <= 5; jour++) {
            createCreneau("comp-9", jour, 6, 12, semaine, zoneIndustrielle.getId(), compOrgId);   // Ratsimba Koto - Manutention
            createCreneau("comp-12", jour, 6, 12, semaine, zoneIndustrielle.getId(), compOrgId);  // Andriamanalina - Manutention/Sécurité
            createCreneau("comp-8", jour, 6, 14, semaine, zoneIndustrielle.getId(), compOrgId);   // Rajaonarivelo Mamy - Magasin
            createCreneau("comp-10", jour, 7, 16, semaine, zoneIndustrielle.getId(), compOrgId);  // Raharijaona Bako - Supervision
        }

        log.info("Créneaux created for week {}", semaine);
    }

    // ========== POINTAGES DE TEST ==========

    private void createPointagesTest(Organisation entreprise, Site siege, Organisation company, Site centreville) {
        if (pointageRepository.count() > 0) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // Skip if yesterday was a weekend
        if (yesterday.getDayOfWeek() == DayOfWeek.SATURDAY || yesterday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            yesterday = yesterday.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY));
        }

        String entOrgId = entreprise.getId();
        String compOrgId = company.getId();

        // Siège Entreprise - yesterday's pointages
        createPointage("ent-1", "entree", yesterday.atTime(7, 55), "pin", "valide", null, siege.getId(), entOrgId);
        createPointage("ent-1", "sortie", yesterday.atTime(13, 2), "pin", "valide", null, siege.getId(), entOrgId);
        createPointage("ent-2", "entree", yesterday.atTime(12, 58), "web", "valide", null, siege.getId(), entOrgId);
        createPointage("ent-2", "sortie", yesterday.atTime(18, 5), "web", "valide", null, siege.getId(), entOrgId);
        createPointage("ent-4", "entree", yesterday.atTime(8, 10), "pin", "anomalie", "retard", siege.getId(), entOrgId);
        createPointage("ent-4", "sortie", yesterday.atTime(13, 0), "pin", "valide", null, siege.getId(), entOrgId);
        createPointage("ent-5", "entree", yesterday.atTime(5, 58), "pin", "valide", null, siege.getId(), entOrgId);
        createPointage("ent-5", "sortie", yesterday.atTime(12, 3), "pin", "valide", null, siege.getId(), entOrgId);

        // Centre-Ville Company - yesterday's pointages
        createPointage("comp-1", "entree", yesterday.atTime(7, 50), "pin", "valide", null, centreville.getId(), compOrgId);
        createPointage("comp-1", "sortie", yesterday.atTime(18, 0), "pin", "valide", null, centreville.getId(), compOrgId);
        createPointage("comp-2", "entree", yesterday.atTime(9, 5), "qr", "valide", null, centreville.getId(), compOrgId);
        createPointage("comp-2", "sortie", yesterday.atTime(13, 0), "qr", "valide", null, centreville.getId(), compOrgId);
        createPointage("comp-3", "entree", yesterday.atTime(8, 0), "web", "valide", null, centreville.getId(), compOrgId);
        createPointage("comp-3", "sortie", yesterday.atTime(17, 2), "web", "valide", null, centreville.getId(), compOrgId);
        createPointage("comp-4", "entree", yesterday.atTime(6, 55), "pin", "valide", null, centreville.getId(), compOrgId);
        createPointage("comp-4", "sortie", yesterday.atTime(19, 5), "pin", "valide", null, centreville.getId(), compOrgId);

        // Today's entries (employees who already clocked in)
        createPointage("ent-1", "entree", today.atTime(7, 58), "pin", "valide", null, siege.getId(), entOrgId);
        createPointage("comp-1", "entree", today.atTime(7, 52), "pin", "valide", null, centreville.getId(), compOrgId);
        createPointage("comp-4", "entree", today.atTime(6, 58), "pin", "valide", null, centreville.getId(), compOrgId);

        log.info("Pointages created: 19");
    }

    // ========== DEMANDES DE CONGE TEST ==========

    private void createDemandesCongeTest(List<TypeConge> typesConge, String entrepriseOrgId, String companyOrgId) {
        if (demandeCongeRepository.count() > 0) {
            return;
        }

        // typesConge: first 4 are entreprise, next 4 are company
        TypeConge congesPayesEnt = typesConge.get(0);
        TypeConge congesPayesComp = typesConge.get(4);
        TypeConge arretMaladieComp = typesConge.get(6);

        LocalDate nextWeekMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        // Rabe Marie (ent-2) - 3 jours de congé payé la semaine prochaine (approuvé)
        DemandeConge demande1 = DemandeConge.builder()
                .employeId("ent-2")
                .typeCongeId(congesPayesEnt.getId())
                .dateDebut(nextWeekMonday)
                .dateFin(nextWeekMonday.plusDays(2))
                .duree(3)
                .statut(StatutDemande.approuve)
                .motif("Vacances familiales")
                .organisationId(entrepriseOrgId)
                .build();
        demandeCongeRepository.save(demande1);

        // Razafin Soa (comp-2) - 5 jours de congé payé (en attente)
        DemandeConge demande2 = DemandeConge.builder()
                .employeId("comp-2")
                .typeCongeId(congesPayesComp.getId())
                .dateDebut(nextWeekMonday.plusWeeks(1))
                .dateFin(nextWeekMonday.plusWeeks(1).plusDays(4))
                .duree(5)
                .statut(StatutDemande.en_attente)
                .motif("Voyage personnel")
                .organisationId(companyOrgId)
                .build();
        demandeCongeRepository.save(demande2);

        // Ratsimba Koto (comp-9) - Arrêt maladie 2 jours (approuvé)
        DemandeConge demande3 = DemandeConge.builder()
                .employeId("comp-9")
                .typeCongeId(arretMaladieComp.getId())
                .dateDebut(LocalDate.now().minusDays(2))
                .dateFin(LocalDate.now().minusDays(1))
                .duree(2)
                .statut(StatutDemande.approuve)
                .motif("Certificat médical fourni")
                .organisationId(companyOrgId)
                .build();
        demandeCongeRepository.save(demande3);

        // Andrianaivo Tiana (comp-7) - 1 jour congé payé (refusé)
        DemandeConge demande4 = DemandeConge.builder()
                .employeId("comp-7")
                .typeCongeId(congesPayesComp.getId())
                .dateDebut(nextWeekMonday)
                .dateFin(nextWeekMonday)
                .duree(1)
                .statut(StatutDemande.refuse)
                .motif("Rendez-vous personnel")
                .organisationId(companyOrgId)
                .build();
        demandeCongeRepository.save(demande4);

        // Rakoto Jean (ent-1) - 2 jours congé payé (en attente)
        DemandeConge demande5 = DemandeConge.builder()
                .employeId("ent-1")
                .typeCongeId(congesPayesEnt.getId())
                .dateDebut(nextWeekMonday.plusWeeks(2))
                .dateFin(nextWeekMonday.plusWeeks(2).plusDays(1))
                .duree(2)
                .statut(StatutDemande.en_attente)
                .motif("Affaires personnelles")
                .organisationId(entrepriseOrgId)
                .build();
        demandeCongeRepository.save(demande5);

        log.info("Demandes de congé created: 5");
    }

    // ========== HELPER METHODS ==========

    private void createEmploye(String id, String nom, String role, String email,
                               String telephone, String pin, String organisationId,
                               List<String> siteIds, int heureDebut, int heureFin) {
        // Build availability for Mon-Fri
        List<DisponibilitePlage> disponibilites = new java.util.ArrayList<>();
        for (int jour = 1; jour <= 5; jour++) {
            disponibilites.add(DisponibilitePlage.builder()
                    .jour(jour)
                    .heureDebut(heureDebut)
                    .heureFin(heureFin)
                    .build());
        }

        Employe employe = Employe.builder()
                .id(id)
                .nom(nom)
                .role(role)
                .email(email)
                .telephone(telephone)
                .pin(pin != null ? passwordEncoder.encode(pin) : null)
                .pinHash(pin != null ? EmployeService.sha256(pin) : null)
                .organisationId(organisationId)
                .dateEmbauche(LocalDate.of(2024, 1, 15))
                .disponibilites(disponibilites)
                .siteIds(new java.util.ArrayList<>(siteIds))
                .build();
        employeRepository.save(employe);
    }

    private void createUser(String email, String password, User.UserRole role,
                            String employeId, String organisationId) {
        if (!userRepository.existsByEmail(email)) {
            User user = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .role(role)
                    .employeId(employeId)
                    .organisationId(organisationId)
                    .build();
            userRepository.save(user);
        }
    }

    private void createExigence(String libelle, List<Integer> jours, double heureDebut,
                                double heureFin, String role, int nombreRequis, String siteId, String organisationId) {
        Exigence exigence = Exigence.builder()
                .libelle(libelle)
                .jours(new java.util.ArrayList<>(jours))
                .heureDebut(heureDebut)
                .heureFin(heureFin)
                .role(role)
                .nombreRequis(nombreRequis)
                .siteId(siteId)
                .organisationId(organisationId)
                .build();
        exigenceRepository.save(exigence);
    }

    private void createCreneau(String employeId, int jour, double heureDebut,
                               double heureFin, String semaine, String siteId, String organisationId) {
        CreneauAssigne creneau = CreneauAssigne.builder()
                .employeId(employeId)
                .jour(jour)
                .heureDebut(heureDebut)
                .heureFin(heureFin)
                .semaine(semaine)
                .siteId(siteId)
                .organisationId(organisationId)
                .build();
        creneauAssigneRepository.save(creneau);
    }

    private void createPointage(String employeId, String type, java.time.LocalDateTime localHorodatage,
                                String methode, String statut, String anomalie, String siteId, String organisationId) {
        // Convert LocalDateTime to OffsetDateTime (UTC) for the entity
        OffsetDateTime horodatage = localHorodatage.atOffset(ZoneOffset.UTC);
        Pointage pointage = Pointage.builder()
                .employeId(employeId)
                .type(TypePointage.valueOf(type))
                .horodatage(horodatage)
                .methode(MethodePointage.valueOf(methode))
                .statut(StatutPointage.valueOf(statut))
                .anomalie(anomalie)
                .siteId(siteId)
                .organisationId(organisationId)
                .build();
        pointageRepository.save(pointage);
    }

    private void createPointageCodes(Organisation entreprise, Site siege, Organisation company, Site centreville, Site banlieue, Site zoneIndustrielle) {
        // Entreprise site
        if (pointageCodeRepository.findBySiteIdAndActifTrue(siege.getId()).isEmpty()) {
            pointageCodeService.createForSiteInternal(siege.getId(), PointageCode.FrequenceRotation.QUOTIDIEN, entreprise.getId());
            log.info("Pointage code created for site: {}", siege.getNom());
        }

        // Company sites
        for (Site site : List.of(centreville, banlieue, zoneIndustrielle)) {
            if (pointageCodeRepository.findBySiteIdAndActifTrue(site.getId()).isEmpty()) {
                pointageCodeService.createForSiteInternal(site.getId(), PointageCode.FrequenceRotation.QUOTIDIEN, company.getId());
                log.info("Pointage code created for site: {}", site.getNom());
            }
        }
    }

    // ========== SUPERADMIN ==========

    private void createSuperAdmin() {
        boolean exists = userRepository.findAll().stream()
                .anyMatch(u -> u.getRole() == User.UserRole.SUPERADMIN);
        if (exists) {
            log.info("SUPERADMIN already exists, skipping.");
            return;
        }
        User superAdmin = User.builder()
                .email("superadmin@schedy.io")
                .password(passwordEncoder.encode("SuperAdmin123!"))
                .role(User.UserRole.SUPERADMIN)
                .organisationId(null)
                .employeId(null)
                .build();
        userRepository.save(superAdmin);
        log.info("SUPERADMIN created: superadmin@schedy.io");
    }

    // ========== BETA PROMO CODES ==========

    private void createBetaPromoCodes() {
        if (promoCodeRepository.count() > 0) {
            log.info("Promo codes already exist, skipping.");
            return;
        }

        createPromoCode("BETA2026",  "100% de réduction pendant 3 mois — plan PRO",   100, 3,  "PRO",  50);
        createPromoCode("EARLYBIRD", "50% de réduction pendant 6 mois",                50,  6,  null,   100);
        createPromoCode("MADAGASCAR","100% de réduction pendant 12 mois — plan PRO",  100, 12, "PRO",  20);

        log.info("Beta promo codes created: BETA2026, EARLYBIRD, MADAGASCAR");
    }

    private void createPromoCode(String code, String description,
                                 int discountPercent, int discountMonths,
                                 String planOverride, int maxUses) {
        if (!promoCodeRepository.existsByCode(code)) {
            PromoCode promo = PromoCode.builder()
                    .code(code)
                    .description(description)
                    .discountPercent(discountPercent)
                    .discountMonths(discountMonths)
                    .planOverride(planOverride)
                    .maxUses(maxUses)
                    .currentUses(0)
                    .active(true)
                    .validFrom(OffsetDateTime.now())
                    .build();
            promoCodeRepository.save(promo);
        }
    }
}
