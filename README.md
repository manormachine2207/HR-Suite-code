# HR-Suite

> **Status:** Skeleton (pre-alpha, no application code yet)
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

This repository currently contains the **scaffolding only**:

- Apache 2.0 license + NOTICE
- Maven aggregator parent POM (Java 21)
- Docker Compose dev stack (Postgres, MinIO, Mailpit)
- Minimal CI workflow
- Repo-level documentation (`CLAUDE.md`, `AI_CONTEXT.md`)

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

- Postgres: `localhost:5432` (db=`hrsuite`, user=`hrsuite`, password=`dev` — override via `.env`)
- MinIO API: `http://localhost:9000` (user=`hrsuite`, password=`devdevdev` — override via `.env`)
- MinIO Console: `http://localhost:9001`
- Mailpit SMTP: `localhost:1025`
- Mailpit Web UI: `http://localhost:8025`

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

Lint:

```bash
npx ng lint
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
