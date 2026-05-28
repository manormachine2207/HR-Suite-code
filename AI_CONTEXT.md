---
title: "AI_CONTEXT for HR-Suite-code"
date: 2026-05-27
status: draft
type: ai-context
tags:
  - HR-Suite
  - AI-Coding
  - Repo-Onboarding
---

# AI_CONTEXT for HR-Suite-code

## Zweck

Diese Datei beschreibt, wie KI-gestuetzte Tools in diesem Repository
arbeiten sollen. Sie ist die repo-lokale Ergaenzung zum Vault, der die
verbindlichen Architektur- und Decision-Inhalte fuehrt.

## Baseline

```yaml
baseline_vault: https://github.com/manormachine2207/HR-Suite-notes
baseline_local: /Users/david.berier/Documents/Davids Mind/HR-Suite/
baseline_version: 2026.Q2
```

## Relevante Decisions

Im Vault unter `Entscheidungen/`:

| Typ | Datei | Relevanz |
|---|---|---|
| BDR | BDR-001-Scope-Antrags-Plattform | Was ist im Scope, was nicht; Phase 1 vs. Phase 2 |
| BDR | BDR-002-Low-Code-Workflow-Designer | Workflow-Designer ist generische Low-Code-Plattform |
| BDR | BDR-003-Mandanten-sind-Bundesaemter | BIT erster Pilot-Mandant; Multi-Tenant fuer Marktleistung |
| BDR | BDR-004-Open-Source-by-Default | BIT-Spezifisches NIE im Kern |
| BDR | BDR-005-Mehrsprachigkeit-DE-FR-IT-EN | UI und Inhalte 4-sprachig |
| BDR | BDR-006-OSS-Lizenz-Apache-2 | Apache License 2.0 |
| BDR | BDR-007-Container-First-Compose-Helm-Symmetrie | Container-First, 12-Factor, Compose=Helm |
| BDR | BDR-008-UI-folgt-CI-Bund-und-Oblique | CD Bund + Oblique 15.3 als verbindlicher UI-Standard |
| ADR | ADR-001-Modular-Monolith-mit-BPMN-Engine | Spring Modulith + Flowable + bpmn-io (Frontend-Anteil partial-superseded) |
| ADR | ADR-002-SF-Integration-pro-Antragstyp-konfigurierbar | SF-Aktionen als Workflow-Bausteine |
| ADR | ADR-003-Cloud-Agnostik | Postgres, S3, OIDC; keine Hyperscaler-Bindung |
| ADR | ADR-004-Workflow-Engine-Flowable | Flowable 7.x embedded |
| ADR | ADR-005-Monorepo-Layout | Code in einem Repo, Infra separat |
| ADR | ADR-006-Container-Build-Migration-Image-Tags | Multi-Stage-Dockerfile, Liquibase per Profil, SHA+Semver-Tags |
| ADR | ADR-007-Frontend-Stack-Switch-Angular-Oblique | Angular 21 + Oblique 15.3 + ngx-translate + angular-oauth2-oidc |
| ADR | ADR-008-Tenant-Isolation-RLS | RLS mit FORCE pro Geschaeftstabelle; `tenant`-Root ausserhalb RLS |
| SDR | SDR-001-OIDC-SAML-Generisch | OIDC Phase 1, SAML als Plugin Phase 2 |
| SDR | SDR-002-Audit-SIEM-Standard | Structured Logs, Audit-Tabelle, Syslog-Forwarder |
| SDR | SDR-003-Accessibility-WCAG-eCH-0059 | WCAG 2.1 AA + eCH-0059 verbindlich, axe-core + Lighthouse |

## Architekturkontext (Stand 2026-05-27)

- **Phase 1 Deployment**: BIT-eigene Belegschaft als erster und initial
  einziger Mandant; Mandantenfaehigkeit im Kern voll ausgebaut fuer spaetere
  Marktleistung an weitere Bundesaemter (siehe Vault BDR-001 + BDR-003).
- **Hauptmodule**: tenant, identity-sp, authorization, form-designer,
  workflow-engine-bridge, workflow-designer, antrag, audit, notification,
  connector-spi, branding, i18n, arq (siehe Vault `07-Modul-Katalog`).
- **Datenmodell**: PostgreSQL, jsonb fuer i18n-Felder, Audit-Tabelle
  (Vault `03-Domain-Model`).
- **APIs**: REST + OpenAPI 3.1 (Vault `06-API-Patterns`).
- **Workflow**: Flowable 7 embedded, BPMN 2.0; HR-Designer generiert BPMN.
- **Deployment**: Cloud-agnostisch, K8s-Helm (folgt in `HR-Suite-infra`).
- **Phase 2 Outlook**: Underperformance Management und Lohnfindung
  (Lohnschluessel mit variablen Mechanismen fuer Rollen/Lohnklassen). Beide
  in spaeteren Plan-Cuts mit eigenen BDR/ADRs (siehe Vault `13-Roadmap`).
- **Implementierungsstand (2026-05-29)**: Frontend-Skelett (Angular 21 +
  Oblique, App-Shell, i18n, OIDC-Config) und erstes Backend-Modul
  `application/` (`hr-suite-application`, Spring Boot 3.4, `core/tenant`)
  gelandet. core/tenant liefert das Tenant-Onboarding-REST-API
  (`POST/GET /api/v1/tenant`), Liquibase-Schema mit jsonb-i18n-`display_name`,
  UUIDv7-IDs und OAuth2-Resource-Server-Security. Der `tenant`-Root ist
  System-Root und bleibt ausserhalb RLS; die RLS-Policies + der
  `SET app.tenant_id`-AOP-Aspekt kommen mit der ersten mandantenbezogenen
  Geschaeftstabelle (ADR-008). Spring Modulith folgt mit dem zweiten Modul.

## Security-Kontext

- **Schutzbedarf**: Standard (Personalakten-Niveau, nicht erhoeht).
- **Authentisierung**: OIDC in Phase 1. SAML kommt als Plugin spaeter.
- **Autorisierung**: RBAC mit kontextuellen Genehmiger-Beziehungen
  (Vault `04-Authorization-Model`).
- **Mandantenfaehigkeit**: jeder Mandant = ein Bundesamt; tenant_id ist
  Pflichtfeld in allen Tabellen; Postgres-RLS geplant.
- **Audit/Logging**: strukturierte JSON-Logs + Audit-Tabelle + Syslog ans
  SIEM (Vault SDR-002).
- **Secrets**: keine Secrets im Repo. ConfigMap-/Vault-Verweise via Property-Ref.
- **Datenschutz**: PII nicht in Logs; Audit-Bodies enthalten nur IDs.

## Lokale Patterns

Verwenden:

- Spring Modulith fuer Modulgrenzen
- Spring Security OAuth2 Client + Resource Server (OIDC)
- Liquibase als Init-Container (Prod) oder Spring-Boot-Auto-Run (Dev), per Spring-Profil
- 12-Factor-Konfiguration: alle externen URLs/Hostnamen/Secrets aus Env, nichts hartkodiert
- Multi-Stage-Dockerfile pro deploybarem Modul (siehe ADR-006)
- Conventional Commits
- jsonb fuer i18n-Felder
- UUID v7 fuer IDs
- Angular Standalone-Components (kein NgModule fuer neue Features)
- Oblique-Tokens als Single-Source-of-Truth fuer Farben/Spacing/Typo
- `@ngx-translate/core` mit `assets/i18n/{de,fr,it,en}.json`
- `angular-oauth2-oidc` fuer Frontend-OIDC (PKCE-Flow)
- `APP_INITIALIZER` fuer Runtime-Config-Loading (kein Build-Zeit-Hardcoding)

Vermeiden:

- BIT-spezifische URLs, Hostnames, Logos, Mandantennamen im Kern
- Hardcoded-Deutsch in der UI
- SAML-Logik im Kern (SAML kommt nur als Plugin)
- Hyperscaler-spezifische Services im Kern
- App-Run via `mvn spring-boot:run` als Run-Standard (Container ist Standard, mvn ist Entwickler-Convenience)
- Hartkodierte Hostnamen/Ports/Pfade in application.properties (alles via Env)
- defensive Programmierung in internen Pfaden (validieren an Systemgrenzen)
- GPL/AGPL-Dependencies im Kern (siehe LICENSE-Whitelist in NOTICE)
- NgModule fuer neue Features (Standalone ist Default per Angular-21-Konvention)
- Build-Zeit-Hardcoding von API-URLs oder OIDC-Issuern (alles in runtime.json)
- Custom-Form-Komponenten wenn Oblique-/Material-Aequivalent existiert
- Karma/Jasmine als Test-Stack (Angular 21 nutzt Vitest als Default; Migration zu Jest ist Q-016)

## Teststrategie

- **Unit Tests**: JUnit 5 + AssertJ, pro Modul
- **Modulgrenzen-Tests**: Spring Modulith Verifier
- **Integration Tests**: Testcontainers (Postgres, MinIO, Mock-IDP)
- **E2E Tests**: Playwright gegen das deployed Stack
- **Plugin-Contract-Tests**: SPI-Compliance pro Plugin
- **i18n-Coverage-Lint**: alle UI-Strings in DE/FR/IT/EN

## AI-Coding-Regeln fuer dieses Repo

1. Bestehende Patterns bevorzugen — Vault zuerst lesen.
2. Keine neuen Dependencies ohne Begruendung + Lizenz-Pruefung.
3. Keine Architekturentscheidungen ohne ADR.
4. Keine Security-Entscheidungen ohne SDR.
5. Mandantenfaehigkeit immer pruefen — jede neue Tabelle hat tenant_id.
6. Jede neue Geschaeftsoperation MUSS einen Audit-Eintrag erzeugen.
7. Jede UI-Aenderung MUSS DE/FR/IT/EN mitziehen — kein hardcoded-Deutsch.
8. Jede API-Aenderung MUSS die OpenAPI-Spec mitaktualisieren.
9. Fehlende Entscheidung: Decision Draft im Vault vorschlagen, nicht
   stillschweigend implementieren.

## Verwandte Files in diesem Repo

- `CLAUDE.md` — Code-Repo-Runbook fuer Claude (Build, Tests, Konventionen)
- `README.md` — OSS-Landing-Page
- `LICENSE` — Apache 2.0
- `NOTICE` — Drittsoftware-Hinweise
- `pom.xml` — Maven Aggregator

## Verwandte Files im Vault (HR-Suite-notes)

- `_CLAUDE.md` — Vault-Kompass fuer Claude
- `00-Onboarding-Read.md` — 30-Min-Einstieg
- `08-Design-Tenets.md` — 15 verbindliche Tenets
- `Entscheidungen/_Decision-Register.md` — alle Decisions im Ueberblick
- `Entscheidungen/Decision-Governance.md` — wann ADR/BDR/SDR
