# Plan — ADR-012 SP1: Graph→BPMN-Compiler + Voll-Validierung (2026-06-12)

> Kontrakt: ADR-012 + Vault-Spec `ADR-012-spec-sp2-flow-canvas-editor` (Graph-Modell).
> Branch `fix/critical-review-hardening` (gemeinsam mit der Review-Härtung).

## Backend (dieser Teil)

- `antragstyp/graph/`: typisiertes Modell (`GraphDefinition.from(JsonNode)`,
  `GraphNode`/`GraphEdge`/`GraphNodeData`, `GraphNodeType`) — SP2-JSON bleibt
  opak gespeichert, geparst wird beim Publish.
- `GraphValidator` (pure): genau 1 START, ≥1 END, Kanten-Integrität, Erreichbarkeit,
  keine Sackgassen, Key-Pflicht/-Pattern/-Eindeutigkeit, ACTION braucht `ref`,
  XOR-Fan-out: max. 1 unkonditionierte Kante (= Default), **Bedingungen nur in der
  geschlossenen Syntax `var == 'wert'` / `!=` — kein freies JUEL** (Injection),
  AND nicht gleichzeitig Split+Join.
- `GraphBpmnCompiler` (pure): XOR→`exclusiveGateway` (+`default`), AND→
  `parallelGateway`, FORM/APPROVAL→`userTask` (candidateGroups), ACTION→
  `serviceTask` (`n8nActionDelegate`, `ref`, `inputMappingJson`); Knoten-Key =
  BPMN-ID; START→`start`, ENDs→`end_N`; Kanten→`sequenceFlow` (+`conditionExpression`).
- `publish()`-Präzedenz: **Graph > FlowDefinition (Cut B) > Platzhalter**;
  Validator-Fehler → 422 `Invalid` (TX-Rollback, nichts demotet/deployt).
- Tests: `GraphValidatorTest` (14), `GraphBpmnCompilerTest` (7), Service-Tests,
  `GraphPublishRoundtripIT` (Publish→Deploy→Start→XOR-Default-Routing; 422-Pfad).

## Frontend (folgt nach FE-Hygiene-Block)

- Publish im Builder freischalten, wenn ein Graph existiert (Sperre „Compiler = SP1“
  entfällt); 422-Validator-Meldungen sichtbar machen.
- Kanten-Inspector: `label` + `condition` (geschlossene Syntax) für XOR-Ausgänge
  editieren; Kanten löschbar.

## Bewusst nicht in SP1

- SP3 (Timer/Sub-Flow, Custom-Node-Visuals, Auto-Layout), Editor-seitige
  Voll-Validierungs-Anzeige über Warnings hinaus.
