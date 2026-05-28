---
title: "CLAUDE.md — HR-Suite Code-Repo Runbook"
date: 2026-05-27
status: draft
type: claude-entry
tags:
  - HR-Suite
  - Code-Repo
  - Claude
---

# CLAUDE.md — HR-Suite Code-Repo Runbook

Wenn du als Claude-Code-Agent in diesem Repository arbeitest, starte hier.

## Einstieg

1. Lies **[AI_CONTEXT.md](./AI_CONTEXT.md)** fuer Decision-Liste und
   Repo-Pflichten.
2. Schau in den Vault: `/Users/david.berier/Documents/Davids Mind/HR-Suite/`
   (Remote: `https://github.com/manormachine2207/HR-Suite-notes`).
3. Pruefe relevante Decisions: `Entscheidungen/_Decision-Register.md`.
4. Pruefe die Tenets: `08-Design-Tenets.md`.

## Verwandte CLAUDE-Kontexte

| Kontext | Pfad | Rolle |
|---|---|---|
| Code-Repo (du bist hier) | `~/Desktop/Git Repos/HR-Suite-code/CLAUDE.md` | Code-Konventionen, Build, Tests |
| Vault | `~/Documents/Davids Mind/HR-Suite/_CLAUDE.md` | Decisions, Release-Notes, Spec |
| Infra (geplant) | `~/Desktop/Git Repos/HR-Suite-infra/CLAUDE.md` | Helm, Terraform, ArgoCD |

Bei Spec-/Decision-Aenderungen: zuerst Vault. Bei Code-/Build-Aenderungen: zuerst dieses Repo.

## Repos

| Inhalt | Remote | Working-Tree |
|---|---|---|
| Code (hier) | `manormachine2207/HR-Suite-code` | `~/Desktop/Git Repos/HR-Suite-code/` |
| Notes (Vault) | `manormachine2207/HR-Suite-notes` | `~/Documents/Davids Mind/HR-Suite/` |
| Infra (geplant) | `manormachine2207/HR-Suite-infra` | `~/Desktop/Git Repos/HR-Suite-infra/` |

## Build-Quick-Reference

Aktueller Stand: Erstes Frontend-Skelett (Angular 21 + Oblique) und erstes
Backend-Modul (`application/`, Spring Boot 3.4, `core/tenant`) gelandet.
Aggregator-pom mit Modul `application`, Dev-Compose-Stack (Backing-Services +
`app` + `backend`), CI mit `validate` + Backend-`verify` + Frontend.

Container-First-Disziplin (per BDR-007): App laeuft im Container, lokales Maven ist optional.

**Run the app locally:**

```bash
docker compose up -d        # builds frontend + backend containers, starts all backing services
# App:           http://localhost:8080
# Healthcheck:   http://localhost:8080/healthz
# Backend:       http://localhost:8081
# Backend health http://localhost:8081/actuator/health
# Mailpit UI:    http://localhost:8025
# MinIO Console: http://localhost:9001
docker compose down         # stop everything
```

```bash
# --- Variante A: Maven lokal vorhanden ---
mvn -B validate

# --- Variante B: kein lokales Maven, validate via Container ---
docker run --rm -v "$PWD":/work -w /work \
  maven:3.9-eclipse-temurin-21 mvn -B validate

# Dev-Stack starten
docker compose up -d

# Dev-Stack stoppen
docker compose down

# Compose-YAML validieren
docker compose config -q

# --- Frontend (Angular 21 + Oblique 15.3) ---
cd frontend
npm ci                                # Deps installieren (1x oder bei package.json-Aenderung)
npx ng serve                          # Dev-Server localhost:4200
npx ng build                          # Production-Build nach dist/
npx ng test --watch=false             # Unit-Tests (Vitest in Angular 21 default)
# npx ng lint                         # not yet wired — follow-up cut adds @angular-eslint
```

**Backend (`application/`, Spring Boot 3.4) — ohne lokales Maven:**

```bash
# Docker-Maven-Runner (BuildKit-m2-Cache im Volume hrsuite-m2)
mvnd() { docker run --rm \
  -v "$HOME/Desktop/Git Repos/HR-Suite-code":/work -w /work \
  -v hrsuite-m2:/root/.m2 maven:3.9-eclipse-temurin-21 mvn -ntp "$@"; }

mvnd -pl application -am test     # Surefire: Unit-/Slice-Tests (kein Docker noetig)
mvnd -pl application -am verify   # + Failsafe: *IT via Testcontainers (Docker noetig)
mvnd -pl application -am package -DskipTests   # JAR bauen

# Container-Build (Multi-Stage per ADR-006, Reactor-Root = ./pom.xml):
docker build -f application/Dockerfile -t hr-suite-application:dev .
```

`*IT`-Tests (z. B. `TenantIT`) brauchen einen laufenden Docker-Daemon
(Testcontainers) und laufen unter Failsafe in `verify` bzw. in CI.

## Pflichten bei Code-Aenderungen

1. **Vault zuerst** — Decision-Register checken, ggf. Decision Draft anlegen.
2. **AI_CONTEXT-Regeln** befolgen (siehe AI_CONTEXT.md).
3. **OSS-Disziplin** — keine BIT-spezifischen Inhalte im Kern (siehe BDR-004).
4. **i18n** — kein hardcoded-Deutsch (siehe BDR-005).
5. **Mandant** — jede neue Tabelle hat tenant_id (siehe BDR-003).
6. **Audit** — jede neue Geschaeftsoperation erzeugt Audit-Eintrag (siehe SDR-002).
7. **OpenAPI-Currency** — API-Aenderung = Spec-Aenderung im selben Commit.
8. **Container-First** — App-Run via Container, Config aus Env, Migrations als Init-Container (siehe BDR-007 + ADR-006).
9. **Compose-Helm-Symmetrie** — jeder neue Compose-Service hat 1:1-Helm-Spiegel-Plan (siehe BDR-007).
10. **Conventional Commits** — `<type>(<scope>): <subject>`.
11. **Apache-2.0-Header** in jeder neuen Source-Datei.

## Conventional Commits

Pflichttypen (Subset):

| Type | Bedeutung |
|---|---|
| `feat` | neue Funktionalitaet |
| `fix` | Bugfix |
| `docs` | Dokumentation (Repo oder Vault) |
| `chore` | Wartung, Hygiene, OSS-Dateien |
| `build` | Build-Konfiguration |
| `ci` | CI/CD-Konfiguration |
| `refactor` | Code-Aenderung ohne Verhaltenswechsel |
| `test` | Test-Code |
| `perf` | Performance-Aenderung |

Footer fuer KI-erzeugte Commits:
```
Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

## Vault-Commit-Disziplin

Aenderungen am Vault werden in `HR-Suite-notes` committet — nicht hier.
Wenn du in diesem Repo arbeitest und dabei eine Vault-Aenderung noetig wird,
mache zwei Commits in zwei Repos.

## Tests

`mvnd` = Docker-Maven-Runner aus der Build-Quick-Reference oben (oder lokales
`mvn`, falls vorhanden).

```bash
# Unit-/Slice-Tests (Surefire) — kein Docker-Daemon noetig
mvnd -pl application -am test

# + Integrationstests (Failsafe, *IT via Testcontainers) — Docker noetig
mvnd -pl application -am verify
```

Test-Konventionen: `@WebMvcTest`-Slices + `@MockitoBean` (Boot 3.4, nicht
`@MockBean`), `@SpringBootTest` + Testcontainers + `@DynamicPropertySource`
fuer `*IT`. Security-Slices nutzen den `spring-security-test`
`jwt()`-Post-Processor.

## i18n-Pflicht

Jede UI-Aenderung MUSS DE/FR/IT/EN im selben Release mitziehen. Lint laeuft
in CI: `check_i18n_coverage.sh` (kommt mit Frontend-Skeleton).

## OpenAPI-Pflicht

Jede API-Aenderung MUSS die OpenAPI-Spec mitaktualisieren. Living-Spec unter
`docs/openapi/` (kommt mit ersten Modulen).

## Aktuelle Out-of-Scope-Punkte

Aktuell absichtlich NICHT enthalten:

- **RLS-Policies + TenantContext-AOP-Aspekt** (`SET app.tenant_id`) — kommen
  mit der ersten mandantenbezogenen Geschaeftstabelle; `tenant` selbst ist
  System-Root und bleibt ausserhalb RLS (siehe ADR-008).
- **Spring Modulith** — kommt mit dem zweiten Backend-Modul.
- CONTRIBUTING.md / CODE_OF_CONDUCT.md / SECURITY.md (vor Public-Switch)
- `ng lint` / Pre-Push-Lints (Follow-up-Cut, `@angular-eslint`)
- Dex/OIDC-Stub im Compose (kommt mit identity-sp; dev nutzt Mock-Decoder)
- Helm-Charts (kommen in `HR-Suite-infra`)

Siehe Vault `13-Roadmap.md` fuer Phasen-Plan.

## Querverweise

- [AI_CONTEXT.md](./AI_CONTEXT.md)
- [README.md](./README.md)
- Vault `_CLAUDE.md` (kanonisch fuer Decisions)
- Vault `00-Onboarding-Read.md`
