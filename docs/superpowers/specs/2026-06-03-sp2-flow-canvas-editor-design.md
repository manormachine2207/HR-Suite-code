# SP2 — Visueller Flow-Canvas-Editor — Design

> Status: approved (Brainstorming 2026-06-03) · ADR-012 · Scope: **Editor first, publish deferred to SP1**

## Ziel

Ein **visueller Freiform-Node-Graph-Editor** (n8n-artig) für das Authoring von Antragstyp-Flows:
HR zieht Knoten (Start / FORM / APPROVAL / ACTION / XOR / AND / End) auf einen Canvas, verbindet
sie mit Kanten, konfiguriert sie — und **speichert** den Graphen. Das ersetzt den linearen
List-Editor aus Cut C (ADR-011).

**Scope-Grenze (SP2):** Bauen + Speichern. Die **Kompilierung Graph→BPMN + Voll-Validierung +
Publish ist SP1** und hier bewusst ausgeklammert (Publish im UI gesperrt). Dieser Pivot ist Teil
eines Mehr-Sub-Projekt-Plans (SP1 Compiler-Backend, SP2 Editor, SP3 erweiterte Knoten).

## Verhältnis zu Cut C / ADR-011

Wiederverwendet (bleibt): Backend `GET /api/v1/action/refs`, `/antragstypen/neu`-Create-Page,
`AntragsTypService`-Grundgerüst, Builder-Gerüst (Sektionen), Publish/Deploy-Pfad.
**Ersetzt:** das strukturierte `FlowDefinition`-List-Editor-Modell → Freiform-Graph; die
`FlowStepEditorComponent` (List) → `FlowCanvasEditorComponent` (Canvas).
**ADR-012 löst die List-Editor-Entscheidung aus ADR-011 ab** (ADR-011-Fundament bleibt gültig).
SP2 baut auf Branch `feat/cut-c-flow-editor` auf; **PR #5 (List-Editor) wird durch den SP2-PR
abgelöst**, damit kein wegwerfbarer List-Editor in `main` landet.

## Architektur

Frontend-Schwerpunkt + ein minimaler, **opaker** Backend-Ablageort. Der Graph wird NICHT
kompiliert (SP1).

### Vorläufiges Graph-Modell (Frontend + gespeichertes JSON; SP1 finalisiert es für die Kompilierung)

```ts
export type NodeType = 'START' | 'FORM' | 'APPROVAL' | 'ACTION' | 'XOR' | 'AND' | 'END';

export interface GraphNode {
  id: string;
  type: NodeType;
  position: { x: number; y: number };
  data: GraphNodeData;            // typ-spezifisch (s.u.)
}
export interface GraphEdge {
  id: string;
  source: string;                // node id
  target: string;                // node id
  sourceHandle?: string;         // für XOR/AND mit mehreren Ausgängen
  label?: string;                // z.B. "true"/"false"
  condition?: string;            // JUEL-artiger Ausdruck, nur für XOR-Ausgänge
}
export interface GraphDefinition {
  nodes: GraphNode[];
  edges: GraphEdge[];
}
```

Knoten-`data` je Typ:
- `START` `{}` (Antrags-Einreichung; genau 1) · `END` `{}` (terminal; ≥1)
- `FORM` `{ key, title }` · `APPROVAL` `{ key, title, assigneeRole }`
- `ACTION` `{ key, title, ref, inputMapping }`
- `XOR` `{ key, title }` (ausgehende Kanten tragen `condition`+`label`)
- `AND` `{ key, title }` (Parallel-Split/Join)

`key` weiterhin gegen `^[A-Za-z][A-Za-z0-9_]*$` validiert (wird in SP1 BPMN-ID/JUEL-Var).
`position` wird gespeichert (Freiform-Layout).

### Persistenz (opak)

- **Migration 009:** `ALTER TABLE antragstyp_version ADD COLUMN graph_definition jsonb` (nullable).
- **Entity `AntragsTypVersion`:** Feld `graphDefinition` als `com.fasterxml.jackson.databind.JsonNode`
  mit `@JdbcTypeCode(SqlTypes.JSON)` → beliebige Graph-Form round-trippt opak (kein typisierter Record).
- **DTOs/Service:** `graphDefinition` (`JsonNode`, opak) in `CreateVersionRequest`, `editDraft`-Pfad
  und `AntragsTypVersionResponse`; `createDraftMajor`/`editDraft` reichen durch.
- **`publish()` unverändert** — Graph wird nicht kompiliert. UI sperrt Publish bei vorhandenem Graph
  mit Hinweis „Compiler folgt (SP1)".

## Frontend-Komponenten (fokussierte Units)

1. **`FlowCanvasEditorComponent`** (Container) — hält `nodes`/`edges` als Signals, rendert `<vflow>`
   (ngx-vflow), koordiniert Palette + Seitenpanel. Delegiert (De)Serialisierung/Validierung an (4).
2. **Palette** — ziehbare Node-Typen; Drop legt Knoten an der Drop-Position an (Start einmalig).
3. **Custom-Node-Templates** (ngx-vflow custom nodes) — Icon + Label + `key`-Chip; Input-Handle
   links, Output-Handle(s) rechts; XOR mehrere benannte Outputs, AND Split/Join-Optik.
4. **`flow-graph.model.ts` + reine Logik** — `toGraphDefinition()`/`loadGraph()` ((de)serialisieren
   inkl. Positionen), `validateGraph()` (nicht-blockierende Hinweise). **Frei von vflow-Rendering**,
   damit unit-testbar ohne Canvas.
5. **Node-Seitenpanel** — Klick auf Knoten → Reactive-Forms-Felder: `key`+`title`(de/fr/it/en);
   APPROVAL `assigneeRole`-Dropdown; ACTION `ref`-Dropdown (`/action/refs`) + `inputMapping`-Zeilen.
6. **Kanten-Editor** — Kante ziehen Output→Input legt `edge` an; bei XOR-Ausgängen Label + `condition`.

**Client-Validierung (leicht, nicht-blockierend, SP2):** kein START / kein END, unverbundene Knoten,
XOR ohne konditionierte Ausgänge, doppelte/ungültige `key`. Voll-Validierung = SP1.

**Builder-Integration:** `<app-flow-canvas-editor>` ersetzt `<app-flow-step-editor>` in der
Flow-Section; List-Editor-Komponente + Spec werden entfernt. Save persistiert `graphDefinition`
via `editDraft`/`createDraftVersion`. Publish-Button gesperrt solange Graph vorhanden.

## Dependency / Build

- `ngx-vflow` zu `frontend/package.json` (+ lockfile). Kein lokales Node → Install/Build im
  `hrsuite-fe-dev`-Container (`npm install`) bzw. Image-Rebuild. **Angular-21-Peer-Kompatibilität
  wird beim Umsetzen verifiziert**; Fallback (alternative Lib / Custom SVG+CDK) falls inkompatibel.

## Tests

- **Backend:** Round-Trip — Version mit beliebigem Graph-JSON speichern → identisch zurücklesen (opak);
  RLS bleibt gewahrt (kein neuer ungescopter Pfad).
- **Frontend:** die reine Logik aus (4) — `toGraphDefinition`/`loadGraph`-Round-Trip (inkl. Positionen),
  addNode, connect-edge, key-Validierung, XOR-Kantenbedingung, `validateGraph`-Hinweise. Vom
  vflow-Rendering entkoppelt.
- **Verifikation:** Browser-Smoke — Graph wie der n8n-Screenshot zeichnen (Start→Create→XOR→2 Pfade
  + paralleler AND-Pfad) → speichern → neu laden (round-trip), Publish sichtbar gesperrt.

## Fehlerbehandlung

- Save-Fehler: 422/409 wie im bestehenden Builder gemappt.
- `/action/refs` leer → ACTION-Knoten ohne Ref deaktiviert/Hinweis.
- Publish bei vorhandenem Graph: Button gesperrt + Tooltip „Compiler folgt (SP1)".

## Bewusst verschoben

- **SP1:** Graph→BPMN-Compiler (XOR→exclusiveGateway, AND→parallelGateway split/join, Kanten→
  sequenceFlows mit conditionExpression), Voll-Validierung (genau 1 Start, Erreichbarkeit, saubere
  Splits/Joins, keine Sackgassen), Publish freischalten.
- **SP3:** weitere Knoten (Timer, Sub-Flow), Auto-Layout-Assist.
- **Cut D:** Runtime / Antragsteller-Auto-UI + Task-Abschluss.

## Betroffene Artefakte

- **Backend:** Migration 009; `AntragsTypVersion.graphDefinition` (JsonNode); `CreateVersionRequest`/
  `AntragsTypVersionResponse`/Service-Durchreichung; Round-Trip-Test.
- **Frontend:** `flow-graph.model.ts` (neu), `flow-canvas-editor.component` (+ Palette/Node/Panel/Edge
  Units) (neu), Builder-Integration (List-Editor raus), `antragstyp.service` (+`graphDefinition` in
  create/edit, Modell-Feld), `ngx-vflow`-Dependency, i18n (Canvas/Palette-Keys de/fr/it/en),
  Component/Logik-Specs.
- **Vault:** ADR-012 (löst ADR-011-Editor-Entscheidung ab), Decision-Register, _NOW.
