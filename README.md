# HR-Suite

> **Status:** Pre-alpha — versioned request types (core/antragstyp) on multi-tenant RLS
> **License:** [Apache License 2.0](./LICENSE)
> **Spec & decisions:** [HR-Suite-notes](https://github.com/manormachine2207/HR-Suite-notes)

A low-code HR application platform for federal agencies. HR staff design
request-based processes (forms, approval workflows, SAP actions)
themselves, without developer support.

**Out of scope:** payroll, recruiting, time tracking — these stay in the
existing systems.

## Why

Switzerland's federal IT office (BIT) is building the HR-Suite first for
**its own staff**, on the basis that other federal agencies should be able
to consume it later as a managed service or run their own instance. Many
HR processes today are paper- or PDF-based, with hand-routed approvals and
manual SAP SuccessFactors updates. The HR-Suite gives HR a generic platform
to model these request types as forms + workflows + SAP actions, and gives
employees a single application to submit, track, and resolve requests.

The platform is **multi-tenant from day one** so a second agency can either
onboard as an additional tenant in the same instance or run an independent
instance for tighter isolation. Either path is supported because the platform
is **open-source from day one** so other administrations and organisations
with similar request-based HR processes can use it too.

## Key properties

- **Low-code workflow designer** — HR designs request types without coding.
- **BPMN 2.0 engine** ([Flowable](https://flowable.com), Apache 2.0) under
  the hood; HR-friendly UI compiles to standard BPMN.
- **Multi-tenant from day one** — Phase 1 ships with BIT as the first
  tenant; additional agencies can onboard either as further tenants in the
  same instance or as their own independent instance.
- **Multi-language** — DE / FR / IT / EN for UI and HR-defined content.
- **SAP SuccessFactors integration** — read / event / write-back /
  internal, configurable per request type.
- **Cloud-agnostic** — PostgreSQL, S3-compatible storage, OIDC. Runs on
  any Kubernetes cluster.
- **OIDC SSO** — generic; SAML available later as a plugin.
- **Standards-based audit + SIEM** — structured JSON logs, dedicated
  audit table, syslog forwarder.

## Status

**Pre-alpha.** Scaffolding plus the first frontend and backend slices are in
place:

- Apache 2.0 license + NOTICE
- Maven aggregator parent POM (Java 21)
- Docker Compose dev stack (Postgres, MinIO, Mailpit, app, backend)
- CI workflow (validate + backend `verify` + frontend build/test)
- Repo-level documentation (`CLAUDE.md`, `AI_CONTEXT.md`)
- **Frontend skeleton** — Angular 21 + Oblique 15.3 app shell (DE/FR/IT/EN,
  runtime config, OIDC config, eCH-0059 stub), served via nginx at `:8080`
- **Backend module `core/tenant`** — Spring Boot 3.4 application module:
  tenant onboarding REST API (`/api/v1/tenant`), PostgreSQL + Liquibase,
  jsonb i18n display names, UUIDv7 ids, OAuth2 resource-server security
  (OIDC), Testcontainers integration test. Tenant isolation follows
  [ADR-008](https://github.com/manormachine2207/HR-Suite-notes/blob/main/Entscheidungen/ADR-008-Tenant-Isolation-RLS.md)
  (Postgres RLS); `tenant` itself is the system root and stays outside RLS.
- **core/antragstyp**: versioned request-type definitions (major/minor per
  ADR-009) with a server-side compatibility classifier (breaking change -> new
  major), lifecycle API (`/api/v1/antragstyp/*`, RBAC per role), and the
  **first tenant-scoped tables under active PostgreSQL Row-Level Security**
  (ADR-008) — isolation enforced via a `tenant_id` GUC set per transaction

Application code is being added module-by-module. Track progress via
[HR-Suite-notes Roadmap](https://github.com/manormachine2207/HR-Suite-notes/blob/main/13-Roadmap.md).

## Quick start (developers)

Prerequisites:

- Docker + Docker Compose v2 (mandatory)
- Git
- Java 21 + Maven 3.9+ are optional — the build runs in a container if you
  don't have them installed locally (see ADR-006 in
  [HR-Suite-notes](https://github.com/manormachine2207/HR-Suite-notes/blob/main/Entscheidungen/ADR-006-Container-Build-Migration-Image-Tags.md)).

Clone and validate the build skeleton:

```bash
git clone git@github.com:manormachine2207/HR-Suite-code.git
cd HR-Suite-code

# If you have Maven locally:
mvn -B validate

# If not (container-only):
docker run --rm -v "$PWD":/work -w /work \
  maven:3.9-eclipse-temurin-21 mvn -B validate

# Validate the dev compose stack:
docker compose config -q
```

Start the local development stack:

```bash
docker compose up -d
docker compose ps
```

Endpoints (local):

- **App (Angular + Oblique):** `http://localhost:8080`
- **Healthcheck:** `http://localhost:8080/healthz`
- **Backend (Spring Boot):** `http://localhost:8081`
- **Backend health:** `http://localhost:8081/actuator/health`
- **Request-type admin API:** `http://localhost:8081/api/v1/antragstyp` (RBAC: hr-designer / tenant-admin / hr-reviewer / applicant)
- Postgres: `localhost:5432` (db=`hrsuite`, admin user=`hrsuite`, password=`dev` — override via `.env`). The backend connects as the restricted, non-superuser role `hrsuite_app` (provisioned by `docker/postgres-init/`) so PostgreSQL RLS is enforced (ADR-008).
- MinIO API: `http://localhost:9000` (user=`hrsuite`, password=`devdevdev` — override via `.env`)
- MinIO Console: `http://localhost:9001`
- Mailpit SMTP: `localhost:1025`
- Mailpit Web UI: `http://localhost:8025`

### Try the tenant API (dev)

In compose the backend runs the `dev` profile, which uses a **mock** JWT
decoder that accepts the literal bearer token `dev-platform-admin` as a
platform administrator. This is for local development only — production uses
real OIDC via `OIDC_ISSUER_URI`.

```bash
# Create a tenant (201 Created + Location header)
curl -sS -X POST http://localhost:8081/api/v1/tenant \
  -H 'Authorization: Bearer dev-platform-admin' \
  -H 'Content-Type: application/json' \
  -d '{"code":"BIT","subdomain":"bit","displayName":{"de":"Bundesamt für Informatik","fr":"Office fédéral de l'\''informatique"}}'

# List tenants
curl -sS http://localhost:8081/api/v1/tenant \
  -H 'Authorization: Bearer dev-platform-admin'
```

Then create a versioned request type (the dev profile mints a tenant-scoped token
`dev-<role>~<tenant-uuid>`):

```bash
# Capture a tenant id (from the create above), then act as its hr-designer
TID=$(curl -s http://localhost:8081/api/v1/tenant \
  -H 'Authorization: Bearer dev-platform-admin' \
  | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# Create a request-type definition (201)
curl -s -X POST http://localhost:8081/api/v1/antragstyp \
  -H "Authorization: Bearer dev-hr-designer~$TID" \
  -H 'Content-Type: application/json' \
  -d '{"key":"sonderurlaub","title":{"de":"Sonderurlaub"}}' -w '\n-> %{http_code}\n'
```

Stop the stack:

```bash
docker compose down
```

### Frontend (Angular)

Prerequisites: Node 22 LTS, npm 10+.

```bash
cd frontend
npm ci
npx ng serve
```

The dev server opens on `http://localhost:4200`. Tests:

```bash
npx ng test --watch=false
```

Lint (not yet wired — see TODO in `.github/workflows/build.yml`; will be added via `ng add @angular-eslint/schematics` in a follow-up cut):

```bash
# npx ng lint   # not yet available
```

Production build (artifacts under `dist/hr-suite-frontend/`):

```bash
npx ng build --configuration=production
```

## Repository layout

This is a **monorepo** (see [ADR-005](https://github.com/manormachine2207/HR-Suite-notes/blob/main/Entscheidungen/ADR-005-Monorepo-Layout.md)):

```
HR-Suite-code/
├── .github/workflows/    # CI
├── core/                 # Generic core modules (Spring Modulith) — to be populated
├── plugins/              # Plugin examples (SF connector, OIDC, SMTP, ...) — to be populated
├── frontend/             # Angular 21 + Oblique 15.3 (ng new bootstrapped)
├── docs/                 # OSS user/admin documentation — to be populated
├── examples/             # Example request types (BPMN + form JSON) — to be populated
├── tests/                # Cross-module tests — to be populated
├── pom.xml               # Maven aggregator
├── docker-compose.yml    # Dev stack
├── .env.example          # Dev secrets template (copy to .env for overrides)
├── CLAUDE.md             # Code-repo runbook for Claude agents
├── AI_CONTEXT.md         # Repo-level AI context
├── LICENSE               # Apache 2.0
└── NOTICE                # Third-party attributions
```

A separate infrastructure repository (`HR-Suite-infra`, planned) will host
Helm charts, Terraform modules, and ArgoCD apps.

## Architecture and decisions

The full architectural narrative and every accepted decision live in the
companion repository
[**HR-Suite-notes**](https://github.com/manormachine2207/HR-Suite-notes).
Highlights:

| Where | What |
|---|---|
| `01-Vision-und-Scope.md` | Mission, target users, in-/out-of-scope, phase outlook |
| `08-Design-Tenets.md` | 15 binding design tenets |
| `Entscheidungen/_Decision-Register.md` | Complete ADR / BDR / SDR list (15 entries) |
| `13-Roadmap.md` | Phase plan (Phase 0 setup → Phase 7+ Underperformance/Lohnfindung) |

## Contributing

Contributions are welcome once the first modules land. Until then, please
open an issue to discuss ideas or report scaffolding problems.

A `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, and `SECURITY.md` will be added
before the repository is opened to the public.

## License

Apache License 2.0 — see [LICENSE](./LICENSE) and [NOTICE](./NOTICE).
