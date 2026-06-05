# Cut C — Low-Code-Flow-Editor (HR-UI) — Design

> Status: approved (Brainstorming 2026-06-02) · ADR-010 L3-Authoring · Scope: **Authoring-only**

## Ziel

HR baut in der HR-UI einen Antragstyp samt **Flow** (Schrittliste FORM/APPROVAL/ACTION) und
**veröffentlicht** ihn — die UI ist ein 1:1-Aufsatz auf den bereits existierenden REST-Pfad
(Cut B). Damit ist „HR kann selbst Anträge/Workflows erstellen" ohne curl erfüllt.

**Nicht in Cut C** (bewusst, ADR-010): Runtime/Antragsteller-UI und das Abschließen von
FORM/APPROVAL-`userTasks` (kein Reviewer-Endpoint) → **Cut D**. BRANCH-Schritte
(nicht kompilierbar). Editierbare APPROVAL-Outcomes (nur `approve`/`reject`).

## Architektur-Überblick

Fast alles ist **Frontend**. Backend-seitig **ein** neuer schlanker Read-Endpoint.
Schwergewicht (Compiler, `publish()`, DTOs mit `flowDefinition`/`processDefinitionKey`,
`CreateVersionRequest.flowDefinition`) existiert bereits aus Cut B.

### Routen (Angular)

```
/antragstypen                 Liste (bestehend) + Button „+ Neuer Antragstyp"
/antragstypen/neu             NEU: Anlage-Seite (key + i18n-title) → POST → redirect zu :id/designer
/antragstypen/:id/designer    Ausbau zum „Antragstyp-Builder" mit Abschnitten:
                                · Formular        (bestehender Form-Designer, unverändert)
                                · Flow            (NEU: Schrittliste-Editor)
                                · Veröffentlichen (Draft speichern + Publish)
```

### Backend — einziger neuer Endpoint

```
GET /api/v1/action/refs  → 200 ["provision-ad-account", ...]
```
- Liefert `tenant_n8n_config.allowed_refs` des aktuellen Tenants (leere Liste, wenn kein Config-Row).
- Rolle `hr-designer`, RLS-gescoped über `app.tenant_id`.
- Liegt im **`action`-Modul** (es besitzt `TenantN8nConfig`) — schlanker Read-Endpoint, kein neues
  Schreibmodell. `antragstyp` referenziert ihn nicht → ModularityTests bleiben grün.

## Komponenten & Datenfluss

### Frontend-Modelle

- **NEU** `features/form-designer/flow-definition.model.ts` (neben `form-definition.model.ts`,
  nutzt dessen `LocaleMap`/`LANGS`):
  ```ts
  export type StepKind = 'FORM' | 'APPROVAL' | 'ACTION';
  export interface FormStepDef     { kind: 'FORM';     key: string; title: LocaleMap; }
  export interface ApprovalStepDef { kind: 'APPROVAL'; key: string; title: LocaleMap;
                                     assigneeRole: string; outcomes: ['approve','reject']; }
  export interface ActionStepDef   { kind: 'ACTION';   key: string; title: LocaleMap;
                                     ref: string; inputMapping: Record<string,string>; }
  export type FlowStepDef = FormStepDef | ApprovalStepDef | ActionStepDef;
  export interface FlowDefinition { steps: FlowStepDef[]; }
  ```
  (`kind` exakt GROSS, passend zum Backend-Jackson-Diskriminator.)
- **Update** `features/antragstyp/antragstyp-version.model.ts`: ergänzt
  `flowDefinition?: FlowDefinition | null;` und `processDefinitionKey?: string | null;`
  (Backend `AntragsTypVersionResponse` liefert beides seit Cut B).

### Service (`features/antragstyp/antragstyp.service.ts`) — 5 Methoden

| Methode | Call |
|---|---|
| `createAntragstyp(key, title)` | `POST /antragstyp` |
| `createDraftVersion(id, formDefinition, flowDefinition)` | `POST /antragstyp/:id/versions` — **erweitert**, sendet beides |
| `editDraft(versionId, formDefinition, flowDefinition)` | `PUT /antragstyp/versions/:vid/draft` |
| `publish(versionId)` | `POST /antragstyp/versions/:vid/publish` |
| `listActionRefs()` | `GET /action/refs` |

`flowDefinition` wird **weggelassen** (nicht als `steps:[]`), wenn der Flow leer ist (form-only
Antragstyp → Fallback-BPMN; ein leeres `steps`-Array würde der Compiler ablehnen).

### Komponenten

- **`AntragstypCreateComponent`** (Route `/antragstypen/neu`): Formular `key` (Regex
  `^[a-z0-9_-]+$`) + i18n-`title` (de/fr/it/en) → `createAntragstyp` → redirect zu
  `/antragstypen/:id/designer`.
- **Antragstyp-Builder** (Ausbau der bestehenden Designer-Seite): Abschnitte/Tabs
  **Formular** (bestehend, unverändert) · **Flow** (neu) · **Veröffentlichen** (neu).
- **Flow-Step-Editor** (Flow-Abschnitt): geordnete Schrittliste (Reactive Forms,
  `FormArray` von Step-`FormGroup`s — Pattern wie Form-Designer):
  - Karten mit `kind`-Badge, `key` (Inline-Validierung `^[A-Za-z][A-Za-z0-9_]*$`, eindeutig),
    i18n-`title` (einklappbar).
  - **FORM**: nur key+title.
  - **APPROVAL**: + `assigneeRole`-Dropdown (Default `hr-reviewer`); Outcomes `approve`/`reject` fix (read-only angezeigt).
  - **ACTION**: + `ref`-Dropdown (aus `listActionRefs()`) + `inputMapping` key/value-Zeilen (add/remove).
  - Hinzufügen: `[+ FORM] [+ APPROVAL] [+ ACTION]`. **Kein BRANCH** (nicht kompilierbar);
    unbekannter `kind` aus Altdaten → read-only.
  - Reorder via ▲▼ (Drag&Drop verschoben), Löschen pro Karte.
- **Zoneless:** neue Komponenten **mit Signals** (idiomatischer Pfad; löst den
  `markForCheck`-Cleanup für neuen Code gleich mit ein).

## Speichern / Veröffentlichen

Der Builder arbeitet immer auf einem **Draft**:
- **Laden:** existiert ein `DRAFT`-Major → diesen editieren; sonst Editor aus der letzten
  Version seeden, erstes Speichern legt neuen Draft-Major an (ADR-009).
- **„Als Entwurf speichern":** `editDraft(draftId, …)` wenn Draft existiert, sonst
  `createDraftVersion(id, …)`.
- **„Veröffentlichen":** `publish(draftId)` → Erfolg zeigt Status `PUBLISHED` +
  `processDefinitionKey`.

## Fehlerbehandlung (Server = Source of Truth; Client validiert nur vorab)

| Fall | UI |
|---|---|
| 422 inkompatible In-place-Edit (`CompatibilityClassifier`) | „inkompatible Änderung — neue Major-Version nötig" |
| 400 ungültiger key / fehlende ACTION-ref (Compiler `IllegalArgumentException`) | Server-Meldung am Schritt/Banner |
| 409 Publish-Race (Advisory-Lock) | „gerade veröffentlicht — erneut versuchen" |
| `/action/refs` leer | ACTION-Hinzufügen deaktiviert + Hinweis „keine n8n-Refs für diesen Tenant konfiguriert" |

## Tests

- **Backend:** `GET /action/refs` — Unit (liefert `allowed_refs`, leer ohne Config) + RLS-IT
  (Tenant-Isolation: Tenant A sieht nur seine Refs).
- **Frontend:** Component-Tests (Schritt add/reorder/delete, key-Validierung, ref-Dropdown-Befüllung,
  Save/Publish-Aufrufe, leerer-Flow→flowDefinition weggelassen) + Service-Tests (HTTP inkl.
  `/action/refs`), gem. bestehendem FE-Test-Setup.
- **Verifikation (browser, laufender Stack):** „Neuer Antragstyp" → Flow bauen (FORM→APPROVAL→ACTION)
  → Draft speichern → Publish → publiziert; `processDefinitionKey`/kompiliertes BPMN sichtbar
  (analog dem Cut-B-Live-Smoke).

## i18n

Neue Builder-/Flow-/Create-Keys in **de/fr/it/en** (Tenet 6, Pflicht).

## Betroffene Artefakte

- **Backend:** `action`-Modul — neuer `ActionRefsController` (oder Endpoint in vorhandenem
  Controller) + Read auf `TenantN8nConfig`/Repository; Tests.
- **Frontend:** `flow-definition.model.ts` (neu), `antragstyp-version.model.ts` (update),
  `antragstyp.service.ts` (+4 Methoden, 1 erweitert), `AntragstypCreateComponent` (neu),
  Builder-Ausbau (Designer-Seite → Abschnitte), Flow-Step-Editor-Komponente, Routen, i18n,
  Component/Service-Tests.

## Bewusst verschoben (Roadmap)

- Reviewer-/Task-Abschluss-Endpoint + Runtime/Antragsteller-Auto-UI → **Cut D**.
- BRANCH-Editor + `compileBranch` → späterer Cut.
- Editierbare APPROVAL-Outcomes + Outcome-Validierung (Cut-B-Follow-up) → wenn benötigt.
- Drag&Drop-Reorder → optionaler Folge-Cut.
