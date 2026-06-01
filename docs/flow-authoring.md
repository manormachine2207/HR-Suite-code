# Wie erstelle ich in HR-Suite einen eigenen Workflow (Antragstyp-Flow)?

> Stand: 2026-06-01 · Cut A + Cut B geliefert · Es gibt **noch keine UI** für Flows (Cut C).
> Dieser Guide ist der **headless REST-Pfad**. Der §3-Pfad (Single-`ACTION`) ist live verifiziert
> (`SUBMITTED` + reale Flowable-Instanz + `action_execution = SUCCEEDED, attempts=1`).

## 1. Mentales Modell

Ein Workflow ist eine **`FlowDefinition`** — eine geordnete Liste von Schritten (`steps`) — die auf einer **`AntragsTypVersion`** liegt (gespeichert als `flow_definition` jsonb, Migration 008).

Der Lebenszyklus folgt drei Schichten (ADR-010):

| Schicht | Verantwortung | Technik |
|---|---|---|
| **L1 — Orchestrierung** | Reihenfolge der Schritte | Flowable (FlowDefinition → BPMN kompiliert) |
| **L2 — Aktionen** | Seiteneffekte | **n8n, EXTERN** über Webhook (kein n8n-Code im Apache-2.0-Kern) |
| **L3 — Auto-UI** | Antragsteller-Oberfläche | HR-Suite eigen (noch nicht da → Cut D) |

Beim **`publish()`** passiert (in **einer** Transaktion, atomar):
1. `processKey` wird gebildet: `at_<antragstypId-ohne-Bindestriche>_v<major>` (z.B. `at_0190abcd…ef_v1`).
2. Ist eine `flowDefinition` gesetzt → `BpmnCompiler.compile(processKey, at.key(), flowDefinition)` erzeugt BPMN-XML (überschreibt `workflowBpmn`). Ohne `flowDefinition` wird der gespeicherte `workflowBpmn` als Fallback deployt.
3. `WorkflowEngine.deploy(...)` deployt das BPMN nach Flowable.
4. Der bisherige `PUBLISHED`-Major wird auf `DEPRECATED` demotet (unter `pg_advisory_xact_lock`).

**ACTION**-Schritte werden zu Flowable-`serviceTask`s, die zur Laufzeit den `n8nActionDelegate` aufrufen → POST an einen **externen n8n-Webhook**.

## 2. Die 4 Schritt-Typen (Referenz)

Jeder Schritt ist ein JSON-Objekt mit dem Diskriminator-Feld **`"kind"`** (exakt so, **nicht** `type`; Werte **GROSSGESCHRIEBEN**). Gemeinsames Pflichtfeld: `key`.

| `kind` | Felder | Erzeugt im BPMN | Hinweise |
|---|---|---|---|
| **`FORM`** | `key`, `title` (i18n-Map de/fr/it/en) | `<userTask>` mit `<documentation>FORM</documentation>` | Muss abgeschlossen werden, damit es weitergeht (siehe Grenzen) |
| **`APPROVAL`** | `key`, `title`, `assigneeRole`, `outcomes` (List) | `<userTask candidateGroups=…>` + `<exclusiveGateway>` mit `${key_outcome == '…'}` | `outcomes[0]` = Continue-Pfad; weitere → je ein terminales `endEvent`. Defaults: `assigneeRole`=`hr-reviewer`, `outcomes`=`["approve","reject"]` |
| **`ACTION`** | `key`, `title`, `ref`, `inputMapping` (Map<String,String>) | `<serviceTask delegateExpression="${n8nActionDelegate}">` mit `ref`- und (falls gesetzt) `inputMappingJson`-Field | `ref` = n8n-Webhook-Pfad; `inputMapping` ist statisch (Compile-Zeit) |
| **`BRANCH`** | `key`, `title`, `conditionVariable`, `approveValue`, `thenSteps`, `elseSteps` | **— (Stub)** | Modell vollständig + round-trip-fähig, aber Compiler wirft `UnsupportedOperationException` → **nicht publizierbar** (Cut C) |

### key-Validierung (gilt für `processKey` UND jeden `step.key()`)

```
KEY_PATTERN = ^[A-Za-z][A-Za-z0-9_]*$
```

Der `key` ist gleichzeitig **BPMN-Element-ID** und **JUEL-Variablenname**. Deshalb: keine **Bindestriche**, keine Leerzeichen, keine Symbole. Verstoß → `IllegalArgumentException` (Beispiel: `provision-ad` ist **ungültig** als Step-key; als n8n-`ref` ist `provision-ad-account` dagegen erlaubt — `ref` ist nicht der key).

### Beispiel-JSON

```jsonc
// FORM
{ "kind": "FORM", "key": "antrag", "title": { "de": "Antrag stellen", "en": "Submit request" } }

// APPROVAL
{ "kind": "APPROVAL", "key": "review", "title": { "de": "Freigabe" },
  "assigneeRole": "hr-reviewer", "outcomes": ["approve", "reject"] }

// ACTION
{ "kind": "ACTION", "key": "provision", "title": { "de": "Konto anlegen" },
  "ref": "provision-ad-account", "inputMapping": { "upn": "john@example.com" } }
```

## 3. Schritt für Schritt — heute via REST (ohne UI)

### Dev-Tokens

Kein Signatur-Check im dev-Profil. Zwei Formen (Separator ist **`~`**, nicht `:`):

- `dev-platform-admin` → Rolle `platform-admin`, **ohne** `tenant_id`
- `dev-<role>~<tenant-uuid>` → Rolle `<role>`, `tenant_id=<uuid>`, mit `<role> ∈ {tenant-admin, hr-designer, hr-reviewer, applicant}`

**Alle inhaltlichen Schritte brauchen denselben Tenant** (RLS / ADR-008 filtert über `app.tenant_id`).

```bash
TENANT=019e754d-371c-70e0-b199-88ab785bef6e   # der per dev-seed.sh angelegte Tenant inkl. n8n-Config
BASE=http://localhost:8081                     # Backend direkt; alternativ http://localhost:8080 via FE-nginx-Proxy (/api/v1 -> backend)
DESIGNER="dev-hr-designer~$TENANT"             # create + version + publish
ADMIN="dev-tenant-admin~$TENANT"               # publish (alternativ)
APPLICANT="dev-applicant~$TENANT"              # antrag create/submit
```

### Schritt 1 — Antragstyp anlegen (Rolle `hr-designer`)

`key`-Regex hier ist **anders/laxer**: `^[a-z0-9_-]+$` (Kleinbuchstaben, Ziffern, `_`, `-`), max 128.

```bash
curl -sS -X POST "$BASE/api/v1/antragstyp" \
  -H "Authorization: Bearer $DESIGNER" -H 'Content-Type: application/json' \
  -d '{
    "key": "urlaubsantrag",
    "title": {"de":"Urlaubsantrag","fr":"Demande de congé","it":"Richiesta ferie","en":"Leave request"},
    "description": {"de":"Antrag auf bezahlten Urlaub","en":"Paid leave request"}
  }'
# 201 Created, Location: /api/v1/antragstyp/{id}  -> merke AT_ID
```

### Schritt 2 — Draft-Major-Version mit `formDefinition` + `flowDefinition` (Rolle `hr-designer`)

```bash
AT_ID=<aus Schritt 1>
curl -sS -X POST "$BASE/api/v1/antragstyp/$AT_ID/versions" \
  -H "Authorization: Bearer $DESIGNER" -H 'Content-Type: application/json' \
  -d '{
    "formDefinition": {
      "fields": [
        {"key":"von","type":"DATE","required":true,"label":{"de":"Von","en":"From"}},
        {"key":"bis","type":"DATE","required":true,"label":{"de":"Bis","en":"To"}}
      ]
    },
    "sfActionBindings": {},
    "flowDefinition": {
      "steps": [
        {"kind":"FORM","key":"erfassen","title":{"de":"Erfassen","en":"Fill in"}},
        {"kind":"ACTION","key":"benachrichtigen","title":{"de":"Melden"},
         "ref":"provision-ad-account","inputMapping":{"upn":"test@example.com"}}
      ]
    }
  }'
# 201 Created, Location: /api/v1/antragstyp/versions/{vid}  -> merke VID. Status=DRAFT, major=1.
```

> Draft editieren: `PUT /api/v1/antragstyp/versions/{vid}/draft`. **Achtung:** `flowDefinition` ist **nicht** Teil von `replaceDraftContent` — bei jedem Draft-Edit **mitsenden**, sonst geht sie verloren (wird zu `null`).

### Schritt 3 — Publish (Rolle `tenant-admin` ODER `hr-designer`)

```bash
VID=<aus Schritt 2>
curl -sS -X POST "$BASE/api/v1/antragstyp/versions/$VID/publish" \
  -H "Authorization: Bearer $DESIGNER"
# Server: processKey=at_<AT_ID-ohne-Bindestriche>_v1 -> BpmnCompiler.compile -> workflowBpmn
#         -> WorkflowEngine.deploy (Flowable); alter PUBLISHED-Major -> DEPRECATED.
# Response (AntragsTypVersionResponse): status=PUBLISHED, processDefinitionKey, workflowBpmn, flowDefinition, publishedAt
```

### Schritt 4 — Antrag anlegen + absenden (Rolle `applicant`)

```bash
# 4a — Draft anlegen
curl -sS -X POST "$BASE/api/v1/antrag" \
  -H "Authorization: Bearer $APPLICANT" -H 'Content-Type: application/json' \
  -d '{ "antragstypId": "'$AT_ID'", "payload": {"von":"2026-07-01","bis":"2026-07-14"} }'
# 201 Created, Location: /api/v1/antrag/{id}  -> merke ANTRAG_ID (antragsteller = JWT-sub)

# 4b — absenden -> pinnt den aktuell PUBLISHED-Major und startet die Flowable-Instanz
ANTRAG_ID=<aus 4a>
curl -sS -X POST "$BASE/api/v1/antrag/$ANTRAG_ID/submit" \
  -H "Authorization: Bearer $APPLICANT"
# 200 mit AntragResponse (status=SUBMITTED, workflowProcessId)
```

### Was beim Submit durchläuft

- **Reines Single-`ACTION`** (`serviceTask`) → feuert **automatisch** und läuft durch. (Live verifiziert.)
- **Linearer `FORM`→`ACTION`**: läuft, **aber** `FORM` ist ein `userTask` — auch der muss abgeschlossen werden, und es gibt heute **keinen** REST-Endpoint dafür (siehe Grenzen). Ein Flow, dessen erster wartender Schritt ein `userTask` ist (`FORM`/`APPROVAL`), bleibt am Task stehen.

## 4. Eigene ACTION (n8n) anlegen

Eine ACTION ist ein **n8n-Workflow mit Webhook-Node**. Der Connector baut die URL als `base_url + "/webhook/" + ref` und POSTet einen kanonischen, HMAC-SHA256-signierten Body (Header `X-HRSuite-Signature`). **n8n 2.x:** Webhook-Node `typeVersion 2`, kein Basic-Auth, Webhook ist public — Schutz läuft **nur** über `allowed_refs`-Allowlist + HMAC.

### a) n8n-Workflow anlegen — Webhook-`path` = neue `ref`

```jsonc
{
  "name": "sync-payroll",
  "nodes": [
    { "parameters": { "httpMethod": "POST", "path": "sync-payroll", "responseMode": "responseNode" },
      "name": "Webhook", "type": "n8n-nodes-base.webhook", "typeVersion": 2, "webhookId": "sync-payroll" },
    { "parameters": { "respondWith": "json", "responseBody": "={\"ok\": true, \"echo\": $json.body }" },
      "name": "Respond to Webhook", "type": "n8n-nodes-base.respondToWebhook", "typeVersion": 1 }
  ],
  "connections": { "Webhook": { "main": [[{ "node": "Respond to Webhook", "type": "main", "index": 0 }]] } },
  "active": true
}
```

> Der Connector verschachtelt die Nutzdaten unter `input` → n8n liest sie als **`$json.body.input`** (z.B. `$json.body.input.employeeId`), **nicht** `$json.body`.
> Import auf n8n 2.x: das JSON braucht eine `id` und `settings` (z.B. `n8n import:workflow --input=...` mit gesetzter `id`), danach aktivieren (`n8n update:workflow --active=true --id=...`) und n8n neu starten, damit der Produktiv-Webhook registriert wird.

### b) `ref` für den Tenant freischalten — `tenant_n8n_config`

Pro Tenant: `base_url`, `hmac_secret`, `allowed_refs` (jsonb-Liste). Eine `ref`, die **nicht** in `allowed_refs` steht → terminal abgelehnt (`ref not allowlisted`), ebenso ein fehlender Tenant-Eintrag. `allowed_refs` muss die `ref` **zeichengenau** enthalten (= URL-Segment nach `/webhook/`). RLS-`FORCED`-Tabelle → Seeds via direktem `psql`.

```sql
-- Variante A: kompletter Upsert
INSERT INTO tenant_n8n_config (tenant_id, base_url, hmac_secret, allowed_refs, created_at, updated_at)
VALUES ('019e754d-371c-70e0-b199-88ab785bef6e', 'http://n8n:5678', 'dev-n8n-secret',
        '["provision-ad-account","sync-payroll"]'::jsonb, now(), now())
ON CONFLICT (tenant_id) DO UPDATE
  SET base_url=EXCLUDED.base_url, hmac_secret=EXCLUDED.hmac_secret,
      allowed_refs=EXCLUDED.allowed_refs, updated_at=now();

-- Variante B: nur die neue ref ohne Duplikat anhängen
UPDATE tenant_n8n_config
SET allowed_refs = (SELECT jsonb_agg(DISTINCT r)
     FROM jsonb_array_elements_text(allowed_refs || '["sync-payroll"]'::jsonb) AS r),
    updated_at = now()
WHERE tenant_id = '019e754d-371c-70e0-b199-88ab785bef6e';
```

### c) Resultierende Webhook-URL

```
base_url + "/webhook/" + ref
=> http://n8n:5678/webhook/sync-payroll        # aus dem Compose-Netz (Docker-Service-DNS)
=> http://localhost:5678/webhook/sync-payroll  # vom Host aus
```

`base_url` darf **kein** `/webhook`-Suffix tragen (hängt der Connector selbst an; trailing slash wird getrimmt).

### d) Fehlerverhalten

Der Connector klassifiziert das HTTP-Ergebnis; eine Zeile pro `(process_instance_id, step_key)` (`action_execution`, unique constraint):

| Ergebnis | Klassifikation | Folge |
|---|---|---|
| 2xx | ok | `SUCCEEDED`, short-circuit |
| 4xx / `ref not allowlisted` / kein Tenant-Eintrag | **terminal** (nicht retryable) | `FAILED` |
| 5xx / Timeout / IO | **transient** (retryable) | Retry bis `max-attempts` (Default 3), **ohne Backoff**; danach `DEAD` |

Bei `FAILED`/`DEAD` schreibt der `DeadLetterWriter` (`@Transactional REQUIRES_NEW`) das terminale Outcome eigenständig committet, danach wirft der Delegate **`BpmnError("ACTION_FAILED")`** — so kann der Prozess einen Error-Pfad routen (und die aufrufende TX wird zurückgerollt). Auch ein malformed `inputMappingJson` löst denselben `BpmnError` aus.

> Timeouts: connect 3000 ms, read 10000 ms (default). Idempotenz ist **best effort** — nur `SUCCEEDED` short-circuited; n8n sollte selbst auf dem `idempotencyKey` (`processInstanceId:stepKey`) deduplizieren. Produktiv muss der n8n-Workflow die `X-HRSuite-Signature` **selbst** verifizieren — der Demo-Workflow echot nur.

## 5. Wichtige Grenzen HEUTE

- **Keine Flow-/Step-Editor-UI.** Das Frontend hat nur den **Form-Designer** (Feld-Definitionen, Route `antragstypen/:id/designer`). Flow-Erstellung geht **ausschließlich via REST**. → kommt mit **Cut C**.
- **`APPROVAL` ist via reiner API nicht abschließbar.** Der Compiler erzeugt `userTask` + Gateway, aber `WorkflowEngine` kennt nur `deploy()` und `startInstance()` — **kein** `approve`/`review`/`complete`-Endpoint, kein `TaskService.complete()` in `src/main` (nur im Roundtrip-IT). Ein Flow mit `APPROVAL` **bleibt am userTask hängen** und ist nur in-Process / im IT abschließbar.
- **Gleiches gilt für `FORM`:** ist ebenfalls ein `userTask` ohne Complete-Endpoint. Nur ein reiner `ACTION`-`serviceTask` feuert automatisch.
- **`BRANCH` nicht kompilierbar.** `UnsupportedOperationException` → `publish()` einer FlowDefinition mit `BranchStep` rollt die TX zurück. → **Cut C**.

**Roadmap:**
- **Cut C** (nächster aktiver Cut): Low-Code-Flow-Editor in der HR-UI (Schritt-Editor + „Neuer Antragstyp"-Einstieg), HR-editierbare `outcome`-Strings (mit dann greifender Outcome-Validierung), **`BRANCH`-Kompilierung** (`compileBranch`).
- **Cut D:** Antragsteller-Auto-UI (Multi-Step-Wizard + Status/Timeline unter `/antraege/:id`), L3 aus derselben FlowDefinition gerendert.

## 6. Gotchas (Kurzliste)

- **key-Regex:** `^[A-Za-z][A-Za-z0-9_]*$` für `processKey` **und** jeden `step.key()` — **keine Bindestriche** (key = BPMN-ID + JUEL-Variable). Der Antragstyp-`key` (Schritt 1) hat dagegen die laxere Regex `^[a-z0-9_-]+$`.
- **Diskriminator heißt `kind`** (nicht `type`); Werte **GROSS**: `FORM`/`APPROVAL`/`ACTION`/`BRANCH`.
- **Exactly-one-published-Major:** Nach `publish()` existiert pro Antragstyp genau **ein** `PUBLISHED`-Major; der alte wird automatisch `DEPRECATED`. Nur `DRAFT` ist publizierbar.
- **RLS / Tenant-Scope:** Für alle inhaltlichen Schritte **denselben** tenant-gescopten Token nutzen. Ohne sichtbaren `PUBLISHED`-Major im aktuellen Tenant gibt es keinen Antrag.
- **Token-Separator `~`**, nicht `:` (`dev-hr-designer:UUID` → 401, malformed Bearer, schon vor dem Decoder).
- **`processKey`:** wird serverseitig als `at_<id-ohne-Bindestriche>_v<major>` gebildet — nicht selbst setzen.
- **`flowDefinition` bei Draft-Edits immer mitsenden** (sonst `null`).
- **ACTION-`ref` ≠ Step-`key`:** die `ref` darf Bindestriche tragen (`provision-ad-account`), der Step-`key` nicht.
- **`outcome`-Escaping-Follow-up:** Heute sind nur die Default-Outcomes `approve`/`reject` sicher; HR-editierbare Outcome-Strings werden erst mit Cut C vor JUEL/XML validiert/escaped (Cut-B-Review-Follow-up, bewusst verschoben).

## Verwandte Dokumente

- `docs/n8n-smoke.md` — Cut-A-Smoke (n8n-Connector-Pfad)
- ADR-009 (Antragstyp-Versionierung), ADR-010 (Low-Code-Flow, n8n extern) im Vault-Decision-Register
