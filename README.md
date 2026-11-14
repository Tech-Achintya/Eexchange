# EExchange — Campus Marketplace

A production-deployed, student-only campus marketplace. Students verify with their college email, list
items, browse a college-scoped feed, message interested buyers, and admins moderate everything from a
separate console with a complete audit trail.

| | |
|---|---|
| **Live app** | https://eexchange.app.achinty.me |

Monorepo: `backend/` (Java 21 · Spring Boot) and `Frontend/Eexchange/` (React 19 · Vite).

---

## Highlights

- **Two-phase signup** — email domain is validated against a college registry, an OTP is issued, and the
  user row is only created after the code is verified.
- **AI-assisted moderation** — every listing (text + images) is reviewed asynchronously by a vision-language
  model, with retries, budget control and a human override path.
- **College-scoped feed** with category filters, price range, search, sorting and cursor-safe pagination.
- **Buyer interest flow**, saved posts, unread counts and post expiry.
- **Separate admin experience** — moderation queue, report handling, user blocking, post management and a
  paginated audit log, on an isolated JWT chain.

---

## Tech stack

**Backend** — Java 21 · Spring Boot 3 · Spring Security · Spring Data JPA · Hibernate · Flyway · Bean
Validation · Actuator · Maven · Lombok · JUnit 5 + MockMvc

**Data** — PostgreSQL (managed Postgres) · Redis (managed Redis) for cache, rate limits, OTP state, lockout
counters and token revocation

**Integrations** — Amazon S3 (image storage) · transactional email API (OTP and password-reset mail) ·
OpenRouter vision-language model (moderation)

**Frontend** — React 19 · Vite · React Router 7 · Axios · Framer Motion · React Hot Toast · plain CSS

**Infrastructure** — Docker (multi-stage image) · AWS EC2 · Nginx reverse proxy · Let's Encrypt TLS ·
GitHub Actions · GitHub Pages

---

## Architecture

A **modular monolith**: one deployable, but internally split along business capabilities so any module can
be extracted into its own service later without rewriting callers.

```
Controller  →  Service  →  Repository  →  PostgreSQL
                   │
                   ├── Cache layer (Redis, version-keyed)
                   ├── Event bus (application events → async moderation)
                   ├── Storage port (S3 implementation)
                   └── Moderation port (AI provider / deterministic stub)

Filters → JWT · request-id · auth rate limiting   Interceptors → endpoint rate limits
```

**Why it stays decoupled**

- **Layered boundaries** — controllers never touch repositories or entities; every response crosses a DTO
  contract, so the persistence model can change without breaking the API.
- **Ports for external systems** — storage and moderation sit behind interfaces with swappable
  implementations (S3 in production, a deterministic stub locally), selected by configuration.
- **Events instead of calls** — post creation publishes an application event; the moderation pipeline
  consumes it asynchronously, so the write path never blocks on an external model.
- **Cache behind a service** — the feed service asks a cache abstraction for a page; cache failures are
  swallowed and degrade to the database (fail-open).
- **Domain modules** — authentication, users, posts, interests, reports, moderation, admin and audit each
  own their service, repository and DTO boundary.

---

## Data model

Schema is owned by **three versioned Flyway migrations** (baseline, seed data, additive change) and applied
automatically at startup. **11 tables**:

| Table | Purpose |
|---|---|
| `colleges` | College registry used to scope users and feeds |
| `college_email_domains` | Verified email domains mapped to a college |
| `users` | Student accounts (college, status, credentials) |
| `categories` | Marketplace categories |
| `posts` | Listings with status, price, expiry and revision counters |
| `post_images` | Ordered image references per post |
| `saved_posts` | User bookmarks |
| `interest_messages` | Buyer → seller interest requests and read state |
| `reports` | User-submitted reports with resolution state |
| `admins` | Admin accounts and roles |
| `admin_audit_log` | Append-only admin history with JSONB metadata |

Entities are modelled in JPA; the audit table is written through a **custom JDBC append-only repository** so
history cannot be mutated through the ORM.

---

## API surface

**43 endpoints across 14 controllers**, grouped by capability:

| Area | Endpoints |
|---|---|
| Authentication (signup, OTP, login, password reset) | 7 |
| Posts (feed, detail, create, edit, sold, delete, interest, report) | 7 |
| Interests (received, sent, unread, mark read) | 5 |
| Users (profile, update, change password, delete account) | 4 |
| Admin: users, moderation, posts, reports | 13 |
| Saved posts, catalog, audit log, admin auth, reports | 7 |

- Uniform envelope: `{ success, message, data, timestamp }`.
- **Two independent JWT chains** (student and admin) with different secrets, lifetimes and revocation events.
- Method-level rate limits on write endpoints, plus a global auth rate-limit filter keyed by IP.

---

## Redis usage

Redis is not just a cache here — it carries five concerns:

1. **OTP state** — hashed codes, attempt counters, cooldown and hourly caps.
2. **Password reset** — single-use tokens, cooldown and hourly caps.
3. **Login lockout** — failure counters per identifier and source IP.
4. **Token revocation** — a per-user "valid after" cutoff so password changes kill live sessions.
5. **Feed cache** — version-keyed pages with TTL and a distributed fill lock.

Cache safety: invalidation is a single atomic version increment (no key sweeps), the version is bumped
**after commit**, and only one writer may populate a key (token-verified distributed lock). Redis errors
never fail a request.

---

## Async moderation pipeline

```
POST /api/posts → commit → PostCreatedEvent
                                ↓ (async executor)
                        moderation worker → AI model (text + images)
                                ↓
        APPROVED | REJECTED | MANUAL_REVIEW | RETRY_LATER
                                ↓
     scheduled retry job (backoff, capped attempts, daily budget)
```

- Retries are **per revision**, not per post; a lifetime edit counter freezes a post in manual review.
- Admins can approve, reject or requeue low-confidence verdicts.
- The feed cache is invalidated **only** when visibility actually changes.

---

## Security

- JWT with short-lived admin tokens and separate signing secrets; BCrypt password hashing.
- Login lockout keyed on identifier **and** source IP.
- Single-use, expiring password-reset tokens; OTP attempt ceilings.
- Stateless auth, CORS allow-list (no wildcards), security headers via Spring Security.
- HTML escaping and validation on all user-supplied text; image type and size checks before storage.
- Append-only admin audit log with actor, target, reason, JSONB metadata and request correlation ID.
- Secrets are read from the environment only — never committed, never baked into the image.

---

## Project structure

```
backend/
  src/main/java/com/eexchange/backend/
    Controller/     REST endpoints (student + admin)
    Service/        Business logic, cache, moderation, storage
    Repository/     Spring Data JPA + custom audit repository
    Entity/         JPA entities and enums
    DTO/            Request/response contracts
    Configuration/  Security, filters, executors, Redis scripts, properties
    Utils/          JWT, password policy, hashing, escaping
  src/main/resources/
    application*.properties      profile-based config (no secrets)
    db/migration/                Flyway migrations
  Dockerfile · .dockerignore

Frontend/Eexchange/
  src/pages/        student pages + admin pages
  src/components/   shared UI
  src/context/      auth and admin state
  src/hooks/        reusable hooks
  src/lib/          API client and constants
```

---

## Local development

**Prerequisites:** JDK 21, Maven, Node 18+, Docker.

```bash
# backend
cd backend
docker compose up -d          # local PostgreSQL + Redis
cp .env.example .env          # fill in your own secrets
mvn spring-boot:run           # http://localhost:8080

# frontend
cd Frontend/Eexchange
npm install
npm run dev                   # http://localhost:5173
```

Profiles: `local` (Docker Postgres/Redis) and `prod` (managed Postgres/Redis over TLS). There is no default
profile — the app fails fast if none is set. Flyway creates the schema on first startup.

Environment variable names (values are yours to provide): `DB_*`, `POSTGRES_*`, `REDIS_*`, `APP_JWT_*`,
`APP_CORS_ALLOWED_ORIGINS`, `BREVO_*`, `AWS_*`, `S3_*`, `OPENROUTER_*`, `APP_ADMIN_*`. See
`backend/.env.example` for the full list. The frontend reads a single `VITE_API_URL`.

> The frontend never receives secrets — it only holds the public API base URL.

---

## Testing

**42 test classes** covering services, controllers (MockMvc), security filters, cache versioning, moderation
parsing, scheduled jobs and JWT behaviour.

```bash
cd backend && mvn test
cd Frontend/Eexchange && npm run build
```

---

## Deployment

- **Backend** — multi-stage Docker image pulled onto an EC2 instance and bound to loopback; Nginx terminates
  TLS with auto-renewing Let's Encrypt certificates and proxies `/api` → container. Image uploads go to S3;
  the container is memory-capped for a small instance.
- **Configuration** — a single `.env` file on the server, read at container creation. Changing config means
  recreating the container, not restarting it.
- **Frontend** — static build published through GitHub Actions to GitHub Pages behind a custom domain.
- **Observability** — Actuator health endpoint (database + cache) used as the external check.

`DEPLOYMENT.md` documents the full runbook, including update and rollback procedures.

---

## Practices applied

Layered boundaries with DTO contracts · versioned schema migrations · fail-fast configuration ·
12-factor secrets · stateless authentication · defence in depth (validation, escaping, rate limits, lockout) ·
transaction-aware cache invalidation · idempotent seed data · graceful degradation · async work off the
request path · append-only auditability · automated tests on the critical paths.

---

## Author

**Achintya Srivastava** — [GitHub](https://github.com/Tech-Achintya) · [LinkedIn](https://linkedin.com/in/achintya-srivastava-9a5447326)
