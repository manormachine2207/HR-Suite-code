# Cut A — n8n Action Connector (L2) — Design Spec

**Datum:** 2026-05-30
**Bezug:** DRAFT-ADR-010 (Low-Code-Flow-Orchestrierung; L1 Flowable / L2 n8n extern / L3 Auto-UI),
ADR-002 (SF/Connector-SPI), ADR-004 (Flowable), ADR-008 (RLS), SDR-002 (Audit)
**Status:** proposed — wartet auf Owner-Review vor writing-plans.

## Ziel

Eine **ACTION** in einem Antrags-Workflow (BPMN-`serviceTask`) ruft license-sauber eine
**externe n8n-Workflow** über deren Webhook auf — mit Tenant-Konfiguration, HMAC-Signatur,
Retry/Dead-Letter und Audit. Dieser Cut beweist die n8n-These **end-to-end** gegen eine
**lokale n8n-Instanz** und realisiert damit die bestehende SMTP/AD/SAP-Connectivity, **bevor**
Authoring-UI (Cut B/C) oder Auto-UI (Cut D) darauf gebaut werden.

## In-Scope

1. **Lokale n8n-Instanz** im `docker-compose` (Community Edition) zur Konzept-Verifikation.
2. **Connector-SPI** `ActionConnector` (austauschbar) + Implementierung `N8nActionConnector`.
3. **Flowable-Anbindung**: ein `serviceTask`-Delegate, das eine ACTION-Bindung
   (`ref` + `inputMapping`) ausführt; aufrufbar aus deployten BPMN.
4. **Tenant-Config** `tenant_n8n_config` (Basis-URL, HMAC-Secret, erlaubte `ref`-Allowlist),
   RLS-scharf (ADR-008), pro Mandant; nie im Code.
5. **Sicherheit**: HMAC-SHA256-Signatur des Payloads; Tenant-`ref`-Allowlist; nur explizit
   gemappte Felder verlassen den Kern.
6. **Resilienz**: Retry-Policy (begrenzt, Backoff) + **Dead-Letter** (`action_execution`-Tabelle
   mit Status `PENDING/RUNNING/SUCCEEDED/FAILED/DEAD`); idempotenter Aufruf (Idempotency-Key =
   `process_instance_id + step_key`).
7. **Audit**: jeder Versuch (Start, Erfolg, Fehler, DLQ) als Audit-Eintrag (SDR-002).
8. **Verifikation**: IT gegen Mock-n8n (deterministisch) **und** Smoke gegen die lokale n8n
   (manuell dokumentiert).

## Out-of-Scope (spätere Cuts)

- Flow-Definition-Modell + BPMN-Compiler (**Cut B**) — hier wird die ACTION noch über ein
  **handgeschriebenes Test-BPMN** bzw. eine minimale Erweiterung von `DefaultProcessBpmn`
  ausgelöst, nicht aus einem Low-Code-Editor.
- Low-Code-Flow-Editor (**Cut C**), Antragsteller-Auto-UI/Status-Timeline (**Cut D**).
- Async-Callback-Provisionierung (langlaufende SAP/AD-Jobs mit Rückruf) — Cut A macht
  **request/reply mit Timeout**; Async-Callback ist ein Folge-Cut.

## Architektur

```
Flowable serviceTask (BPMN)                 core (Apache-2.0)            n8n (extern, CE)
  flowable:delegateExpression
        │ "${n8nActionDelegate}"
        ▼
  N8nActionDelegate ──▶ ActionConnector (SPI) ──▶ N8nActionConnector ──▶ POST /webhook/{ref}
        │                                              │  HMAC, inputMapping     (SMTP/AD/SAP flow)
        │                                              ▼
        │                                   tenant_n8n_config (RLS)
        ▼
  action_execution (status, retries, DLQ)  +  Audit (SDR-002)
```

- **`ActionConnector` (SPI)** — `ActionResult execute(ActionRequest req)`; `ActionRequest` =
  `{tenantId, processInstanceId, stepKey, ref, input(Map)}`. n8n-agnostisch → austauschbar.
- **`N8nActionConnector`** — lädt `tenant_n8n_config`, prüft `ref` gegen Allowlist, baut
  signierten Payload, `POST {baseUrl}/webhook/{ref}`, Timeout, mapped Response → `ActionResult`.
- **`N8nActionDelegate`** (Flowable `JavaDelegate`/`@Component`) — liest ACTION-Variablen aus
  dem Execution-Kontext (`ref`, `inputMapping` als Prozess-Variable), ruft den Connector,
  schreibt `action_execution` + Audit, wirft bei finalem Fehler `BpmnError` (→ BPMN-Fehlerpfad)
  oder markiert DLQ je nach Policy.

## Datenmodell (Liquibase 006/007)

- `tenant_n8n_config(tenant_id PK/FK, base_url, hmac_secret, allowed_refs text[], created_at,
  updated_at)` — RLS FORCE + tenant_isolation.
- `action_execution(id, tenant_id, process_instance_id, step_key, ref, status, attempts,
  last_error, request_hash, created_at, updated_at)` — RLS FORCE; UNIQUE(process_instance_id,
  step_key) für Idempotenz.
- Audit-Einträge über das bestehende/künftige Audit-Modul (SDR-002) — bis dahin strukturiertes
  Log-Event + Tabelle `action_execution` als Mindest-Nachweis.

## Sicherheit (Trust-Boundary)

- **HMAC-SHA256** über `{tenantId|ref|idempotencyKey|body}` mit `tenant_n8n_config.hmac_secret`;
  Header `X-HRSuite-Signature`. (n8n-Webhook validiert via Function-Node/Header-Check.)
- **Allowlist**: nur in `allowed_refs` eingetragene `ref` dürfen aufgerufen werden → kein
  beliebiger Webhook-Aufruf, selbst wenn eine Definition kompromittiert ist.
- **Minimal-Exposure**: nur die in `inputMapping` benannten Felder gehen raus; keine Secrets,
  keine ganze Payload automatisch.
- **Tenant-Scope**: `tenant_id` aus `TenantContext`/GUC; Config-Read RLS-gebunden.

## Resilienz

- Retry: max. N (Default 3) mit Backoff bei 5xx/Timeout; 4xx = sofort fehlschlagen (kein Retry).
- Nach erschöpften Retries → `action_execution.status = DEAD` (DLQ-Sicht); BPMN entscheidet via
  Fehler-Boundary, ob der Antrag in einen Fehlerzustand geht.
- **Idempotenz**: UNIQUE(process_instance_id, step_key) + Idempotency-Key im n8n-Payload, damit
  ein Retry keinen Doppel-Seiteneffekt erzeugt (best effort; n8n-seitige Idempotenz dokumentiert).

## docker-compose: lokale n8n

- Service `n8n` (Image `n8nio/n8n`, CE), Port `5678`, eigener Postgres-Schema/Volume, Basic-Auth
  im Dev. Healthcheck. Backend erhält Dev-Default `tenant_n8n_config` (Seed) auf
  `http://n8n:5678/webhook/...`.
- Ein **Demo-n8n-Workflow** „echo/provision-mock" (Webhook-Trigger → Function → Respond) wird als
  exportiertes JSON im Repo abgelegt und beim Dev-Setup importiert (dokumentiert), damit der
  Smoke-Test reproduzierbar ist.

## Verifikation / Tests

1. **Connector-Unit**: inputMapping, HMAC, Allowlist-Reject, 4xx-vs-5xx-Verhalten (Mock-HTTP).
2. **Flowable-IT (Testcontainers)**: Deploy eines Test-BPMN mit `serviceTask` →
   `n8nActionDelegate`; Start einer Instanz; Assert: Mock-n8n-Webhook genau einmal mit
   signiertem, gemapptem Payload aufgerufen; `action_execution = SUCCEEDED`; Audit vorhanden.
3. **Resilienz-IT**: Mock-n8n liefert 500 → Retry bis DEAD; `action_execution = DEAD`, kein
   Doppel-Seiteneffekt; Tenant-fremder Read blockiert (RLS).
4. **Smoke gegen lokale n8n** (manuell, dokumentiert): echter Webhook-Roundtrip über `:5678`.

## Erfolgskriterien

- Eine ACTION in einem deployten BPMN ruft eine **erlaubte** n8n-`ref` mit signiertem, exakt
  gemapptem Payload auf; Ergebnis landet in `action_execution` + Audit; Fehler → Retry → DLQ
  ohne Doppelwirkung; alles tenant-isoliert (RLS).
- Lokale n8n im Compose-Stack erreichbar; Demo-Workflow round-trips; `mvn verify` grün.
- **Kein n8n-Quellcode im Repo** (BDR-006); n8n nur über Webhook genutzt.

## Offene Punkte für den Plan

- Genaues Format, wie eine ACTION-Bindung als Prozess-Variable ins BPMN kommt (bis Cut B den
  Compiler bringt): Vorschlag = Extension-Elemente / Prozess-Variablen `actionRef`, `actionInput`
  am `serviceTask`, gesetzt beim Deploy/Start.
- Audit-Anbindung: eigenständiges Audit-Modul existiert noch nicht (Follow-up im _NOW); Cut A
  nutzt `action_execution` + strukturiertes Event als Minimum.
