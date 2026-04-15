# Schedy — Backend

API REST Spring Boot de Schedy : **planning, pointage et congés** pour PME. Premier module d'une suite bureautique en cours de construction.

> Schedy couvre **planning + pointage + congés uniquement**. Pas de paie, pas de RH.

## Stack

- **Spring Boot 3.4.4** sur **Java 21**
- **PostgreSQL** + **Flyway** (`V1` … `V45`, dernière : `stripe_billing`)
- **Spring Security** + **JWT** (`jjwt` 0.12.5) — access token 30 min, refresh token 7 jours
- **Spring Data JPA** / Hibernate (`open-in-view: false`, batch size 50)
- **TOTP 2FA** (`samstevens.totp`) + codes de récupération + fallback 2FA par email
- **Rate limiting** via `bucket4j` (endpoints sensibles : login, 2FA, upload avatar)
- **Cache** Caffeine
- **Actuator** + **Micrometer / Prometheus** (observability)
- **Stripe** (`stripe-java` 32.0.0) — Checkout Sessions, Customer Portal, webhooks signés
- **Cloudflare R2** via AWS SDK v2 S3 — stockage des avatars/logos testimonials
- **Brevo SMTP** pour emails transactionnels
- **spring-dotenv** pour charger `.env` en local
- **logstash-logback-encoder** — logs JSON structurés en prod uniquement
- **springdoc-openapi 2.8.5** pour Swagger UI
- **Lombok**

## Démarrage

```bash
# Prérequis : PostgreSQL local, Java 21, Maven
cp .env.example .env          # puis renseigner les variables
mvn spring-boot:run           # démarre sur http://localhost:8083
```

- Swagger UI : http://localhost:8083/swagger-ui/index.html
- Actuator : http://localhost:8083/actuator
- Metrics Prometheus : http://localhost:8083/actuator/prometheus

## Fresh database

Flyway est activé en dev et prod (`ddl-auto: validate`). Le schéma est géré exclusivement par les migrations. Pour repartir d'une base vierge :

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

Puis relancer l'application — Flyway rejoue `V1` … `V45`.

## Variables d'environnement

Voir `.env.example` pour la liste complète.

| Groupe | Variables | Notes |
|---|---|---|
| Database | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL |
| JWT | `JWT_SECRET`, `JWT_ACCESS_EXPIRATION`, `JWT_REFRESH_EXPIRATION` | Générer : `openssl rand -hex 64` |
| Mail (Brevo) | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP Brevo |
| 2FA | `TOTP_ENCRYPTION_KEY` | Générer : `openssl rand -base64 32` |
| Kiosk | `KIOSK_ADMIN_CODE` | Code admin 6+ chiffres |
| Server | `SERVER_PORT`, `SPRING_PROFILES_ACTIVE` | `dev` ou `prod` |
| CORS / Frontend | `CORS_ALLOWED_ORIGINS`, `FRONTEND_URL` | URL du front Angular |
| R2 | `R2_ENDPOINT`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`, `R2_BUCKET`, `R2_PUBLIC_URL_BASE`, `R2_REGION` | Cloudflare R2 — dev namespacé sous `dev/` |

## Structure

```
src/main/java/com/schedy/
├── controller/     # REST controllers (/api/**)
├── service/        # Logique métier + sous-packages affectation/, email/
├── entity/         # ~40 entités JPA
├── repository/     # Spring Data repositories
├── dto/            # Request/Response DTOs
├── config/         # SecurityConfig, CorsConfig, CacheConfig, R2Config,
│                   # RateLimitFilter, JwtAuthFilter, OpenApiConfig, TenantContext
├── util/           # JwtUtil, CryptoUtil, SvgSanitizer, …
└── exception/      # Exceptions métier + handler global

src/main/resources/
├── application.yml                   # Config commune
├── logback-spring.xml                 # Logs (JSON en prod)
├── email/                             # Templates email
└── db/migration/V1..V45__*.sql        # Migrations Flyway
```

## Modules fonctionnels

- **Auth** — Login, JWT refresh, 2FA TOTP/email, invitation, set-password, reset password
- **Organisations** — Multi-tenant (`TenantContext`), multi-sites, paramètres
- **Employés** — CRUD, multi-rôles, genre, PIN pointage (avec rotation programmée et audit)
- **Planning** — Grille hebdo, créneaux assignés, affectation auto, templates de plan
- **Pointage** — Web, QR code, kiosque ; feuilles de temps, règles de pause, détection d'absence imprévue
- **Congés** — Demandes, soldes, banques, accrual programmé, types modulaires
- **Exigences** — Couverture planning par rôle et période
- **Testimonials** — Soumission avec avatar R2, liens sociaux, plan tier, certifications
- **SuperAdmin** — Gestion plateforme : organisations, annonces, impersonation (audit), promo codes, pro waitlist, feature flags
- **Billing** — Stripe Checkout + Customer Portal, souscriptions, webhooks signés, codes promo

## Règles techniques importantes

- **`@ElementCollection` : TOUJOURS `FetchType.EAGER` + `@BatchSize(50)`.** Jamais LAZY — la sérialisation Jackson se fait après fermeture de session Hibernate (`open-in-view: false`), ce qui casse en `LazyInitializationException`.
- **`open-in-view: false`** globalement. Les entités doivent être complètement chargées dans le service avant la sortie.
- **Flyway obligatoire pour toute modif de schéma.** Ne jamais modifier une migration déjà appliquée — créer `V{N+1}__*.sql`.
- **Hibernate batch size** 50, `order_inserts` + `order_updates` activés.
- **Upload multipart** plafonné à 256 KB (config globale) ; `SvgSanitizer` impose 100 KB sur les SVG.
- **Rate limit** sur endpoints sensibles via `RateLimitFilter` + `bucket4j`.
- **JWT secret** et autres secrets chargés depuis env vars uniquement — pas de valeurs par défaut dans `application.yml`.

## Tests

```bash
mvn test              # tests unitaires
mvn verify            # tests + intégration
```

Le contexte de test utilise H2 in-memory.

## Frontend associé

Le frontend Angular est dans `../Front/`. Les deux projets forment un ensemble full-stack et doivent être analysés ensemble.
