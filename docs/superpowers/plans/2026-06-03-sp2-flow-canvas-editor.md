# SP2 — Visueller Flow-Canvas-Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A visual free-form node-graph editor (ngx-vflow) where HR builds an Antragstyp flow (Start/FORM/APPROVAL/ACTION/XOR/AND/End) and **saves** it; the graph is stored opaquely. Publish/compile is SP1 and disabled in the UI.

**Architecture:** Correctness-critical logic (graph model, vflow↔domain (de)serialisation, validation) lives in **pure, fully-tested functions** with no ngx-vflow dependency. The `FlowCanvasEditorComponent` is a thin ngx-vflow render shell that delegates to those functions. Backend gains one opaque `graph_definition jsonb` column (no compiler). Builds on the Cut C foundation (branch `feat/cut-c-flow-editor`); replaces the list editor.

**Tech Stack:** Backend Java 21 / Spring Boot, JPA, Jackson `JsonNode`, Testcontainers. Frontend Angular 21 (zoneless, signals), standalone, **ngx-vflow**, ngx-translate, vitest.

**Branch:** `feat/sp2-flow-canvas` (already created from `feat/cut-c-flow-editor`).

**Spec:** `docs/superpowers/specs/2026-06-03-sp2-flow-canvas-editor-design.md` (Vault: `Entscheidungen/specs/ADR-012-spec-sp2-flow-canvas-editor.md`). Decision: ADR-012.

**Backend Maven runner (from repo root):**
```bash
docker run --rm -v "$PWD":/work -w /work -v hrsuite-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true \
  maven:3.9-eclipse-temurin-21 mvn -ntp -pl application -am <goals>
```

**Frontend runner** — the running dev container `hrsuite-fe-dev` (node:22-alpine, deps installed, `frontend/` bind-mounted):
```bash
docker exec hrsuite-fe-dev npx ng test --watch=false
docker exec hrsuite-fe-dev npx ng build
docker exec hrsuite-fe-dev npm install <pkg>     # adds a dependency inside the running container
```

**Commit footer (every commit):** `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

**Do NOT commit:** `application/src/main/java/io/github/manormachine2207/hrsuite/config/RuntimeDbRoleCheck.java`, `application/src/test/java/io/github/manormachine2207/hrsuite/config/`. Always `git add` explicit paths.

---

## File Structure

**Backend — modified:**
- `application/src/main/resources/db/changelog/changes/009-add-graph-definition.sql` (new)
- `.../db/changelog/db.changelog-master.yaml` — append 009 include
- `.../antragstyp/AntragsTypVersion.java` — `graphDefinition` (JsonNode) field
- `.../antragstyp/AntragsTypService.java` — `graphDefinition` param on `createDraftMajor`/`editDraft`
- `.../antragstyp/AntragsTypController.java` — pass `req.graphDefinition()`
- `.../antragstyp/dto/CreateVersionRequest.java` — add `JsonNode graphDefinition`
- `.../antragstyp/dto/AntragsTypVersionResponse.java` — add `graphDefinition` + map it
- `.../antragstyp/AntragsTypServiceTest.java` — existing callers pass `null`
- `.../antragstyp/GraphDefinitionRoundtripIT.java` (new) — opaque round-trip via REST

**Frontend — new:**
- `frontend/src/app/features/form-designer/flow-graph.model.ts` — graph types
- `frontend/src/app/features/form-designer/flow-graph.logic.ts` — pure (de)serialise + validate
- `frontend/src/app/features/form-designer/flow-graph.logic.spec.ts`
- `frontend/src/app/features/form-designer/flow-canvas-editor.component.ts` (+`.html`,`.scss`,`.spec.ts`)

**Frontend — modified:**
- `frontend/package.json` — add `ngx-vflow`
- `.../antragstyp/antragstyp-version.model.ts` — add `graphDefinition?: unknown`
- `.../antragstyp/antragstyp.service.ts` — `graphDefinition` in create/edit
- `.../form-designer/form-designer.component.ts` (+`.html`) — swap list→canvas editor
- remove `flow-step-editor.component.{ts,html,scss,spec.ts}` (replaced)
- `frontend/src/assets/i18n/{de,fr,it,en}.json` — canvas/palette keys

---

## Task 1: Backend — opaque `graph_definition` storage

**Files:**
- Create: `application/src/main/resources/db/changelog/changes/009-add-graph-definition.sql`
- Modify: `.../db/changelog/db.changelog-master.yaml`, `AntragsTypVersion.java`, `CreateVersionRequest.java`, `AntragsTypVersionResponse.java`, `AntragsTypService.java`, `AntragsTypController.java`, `AntragsTypServiceTest.java`

- [ ] **Step 1: Migration**

`009-add-graph-definition.sql`:
```sql
--liquibase formatted sql

--changeset hr-suite:009-add-graph-definition
--comment: opaque free-form flow graph (ADR-012 SP2) on antragstyp_version. Stored as-is;
-- NOT compiled (the graph->BPMN compiler is SP1). Nullable: existing versions unaffected.
ALTER TABLE antragstyp_version
    ADD COLUMN IF NOT EXISTS graph_definition jsonb;
--rollback ALTER TABLE antragstyp_version DROP COLUMN IF EXISTS graph_definition;
```
Append to `db.changelog-master.yaml` after the `008-add-flow-definition.sql` include:
```yaml
  - include:
      file: db/changelog/changes/009-add-graph-definition.sql
      relativeToChangelogFile: false
```

- [ ] **Step 2: Entity field (opaque JsonNode)**

In `AntragsTypVersion.java` add import `import com.fasterxml.jackson.databind.JsonNode;` and, after the `flowDefinition` field (~line 68), add:
```java
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "graph_definition", columnDefinition = "jsonb")
    private JsonNode graphDefinition;
```
Add getter/setter near the other accessors:
```java
    public JsonNode getGraphDefinition() { return graphDefinition; }
    public void setGraphDefinition(JsonNode graphDefinition) { this.graphDefinition = graphDefinition; }
```

- [ ] **Step 3: DTOs**

`CreateVersionRequest.java` — add import `import com.fasterxml.jackson.databind.JsonNode;` and a field:
```java
public record CreateVersionRequest(
        @NotNull FormDefinition formDefinition,
        String workflowBpmn,
        Map<String, Object> sfActionBindings,
        FlowDefinition flowDefinition,    // optional; compiled to BPMN at publish()
        JsonNode graphDefinition          // optional; opaque free-form graph (ADR-012 SP2), not compiled
) {
}
```
`AntragsTypVersionResponse.java` — add import `import com.fasterxml.jackson.databind.JsonNode;`, add `JsonNode graphDefinition,` to the record components (after `processDefinitionKey`), and pass `v.getGraphDefinition(),` in `from(...)` in the matching position.

- [ ] **Step 4: Service pass-through**

In `AntragsTypService.java`, add a `JsonNode graphDefinition` parameter to BOTH `createDraftMajor` and `editDraft` (last param), and set it on the version (`v.setGraphDefinition(graphDefinition);`) right where `setFlowDefinition` is called. Add import `import com.fasterxml.jackson.databind.JsonNode;`.

- [ ] **Step 5: Controller pass-through**

In `AntragsTypController.java`, update the two service calls:
```java
// createVersion:
service.createDraftMajor(id, req.formDefinition(), req.workflowBpmn(), req.sfActionBindings(),
        req.flowDefinition(), req.graphDefinition());
// editDraft:
service.editDraft(vid, req.formDefinition(), req.workflowBpmn(), req.sfActionBindings(),
        req.flowDefinition(), req.graphDefinition());
```

- [ ] **Step 6: Fix existing unit-test callers**

In `AntragsTypServiceTest.java`, every `createDraftMajor(...)`/`editDraft(...)` call currently ends with `null` (the flowDefinition arg). Add a second trailing `null` (graphDefinition) to each call so it compiles.

- [ ] **Step 7: Compile + existing tests green**

Run: `... mvn -ntp -pl application -am test -Dtest='AntragsTypServiceTest,AntragsTypControllerTest,ActionRefsControllerTest'`
Expected: green (signatures updated consistently).

- [ ] **Step 8: Commit**

```bash
git add application/src/main/resources/db/changelog/changes/009-add-graph-definition.sql \
        application/src/main/resources/db/changelog/db.changelog-master.yaml \
        application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTypVersion.java \
        application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/dto/CreateVersionRequest.java \
        application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/dto/AntragsTypVersionResponse.java \
        application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTypService.java \
        application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTypController.java \
        application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTypServiceTest.java
git commit -m "$(cat <<'EOF'
feat(graph): opaque graph_definition jsonb on antragstyp_version (ADR-012 SP2, migration 009)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Backend — opaque round-trip IT

Proves an arbitrary graph JSON saved via REST is returned byte-equivalent (opaque storage; RLS-scoped as `hrsuite_app`).

**Files:**
- Create: `application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/GraphDefinitionRoundtripIT.java`

- [ ] **Step 1: Inspect an existing antragstyp IT** for the boot/RLS pattern (e.g. `AntragsTypRlsIT` or `ActionRefsIT`): `@SpringBootTest` config, `db/rls-it-init.sql`, datasource as `hrsuite_app`, dev token `dev-hr-designer~<tenantId>`, tenant-create flow. Mirror it.

- [ ] **Step 2: Write the IT**

`GraphDefinitionRoundtripIT.java`:
```java
package io.github.manormachine2207.hrsuite.antragstyp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.manormachine2207.hrsuite.HrSuiteApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = HrSuiteApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
class GraphDefinitionRoundtripIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("db/rls-it-init.sql");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "hrsuite_app");
        r.add("spring.datasource.password", () -> "dev");
    }

    @Autowired TestRestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpHeaders h(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return h;
    }

    @Test
    void graphDefinitionRoundTripsOpaquely() throws Exception {
        // tenant
        String tenantBody = rest.exchange("/api/v1/tenant", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"GRAPH\",\"subdomain\":\"graph\",\"displayName\":{\"de\":\"Graph\"}}",
                        h("dev-platform-admin")), String.class).getBody();
        String tenantId = mapper.readTree(tenantBody).get("id").asText();
        HttpHeaders des = h("dev-hr-designer~" + tenantId);

        // antragstyp
        String atId = mapper.readTree(rest.exchange("/api/v1/antragstyp", HttpMethod.POST,
                new HttpEntity<>("{\"key\":\"graph-smoke\",\"title\":{\"de\":\"Graph\"}}", des), String.class)
                .getBody()).get("id").asText();

        // version with an arbitrary graph
        String graph = "{\"nodes\":[{\"id\":\"n1\",\"type\":\"START\",\"position\":{\"x\":10,\"y\":20},\"data\":{}},"
                + "{\"id\":\"n2\",\"type\":\"ACTION\",\"position\":{\"x\":200,\"y\":20},"
                + "\"data\":{\"key\":\"prov\",\"title\":{\"de\":\"P\"},\"ref\":\"r\",\"inputMapping\":{}}}],"
                + "\"edges\":[{\"id\":\"e1\",\"source\":\"n1\",\"target\":\"n2\"}]}";
        String body = "{\"formDefinition\":{\"fields\":[]},\"sfActionBindings\":{},\"graphDefinition\":" + graph + "}";
        String vid = mapper.readTree(rest.exchange("/api/v1/antragstyp/" + atId + "/versions", HttpMethod.POST,
                new HttpEntity<>(body, des), String.class).getBody()).get("id").asText();

        // read back -> graphDefinition equals what we sent
        String listed = rest.exchange("/api/v1/antragstyp/" + atId + "/versions", HttpMethod.GET,
                new HttpEntity<>(des), String.class).getBody();
        var stored = mapper.readTree(listed).get(0).get("graphDefinition");
        assertThat(stored).isEqualTo(mapper.readTree(graph));
        assertThat(vid).isNotBlank();
    }
}
```
> Adjust boot annotations/tenant-create JSON to match the existing IT you read in Step 1 if they differ.

- [ ] **Step 3: Run (green)**

Run: `... mvn -ntp -pl application -am verify -Dit.test=GraphDefinitionRoundtripIT -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: `Tests run: 1, Failures: 0`.

- [ ] **Step 4: Commit**
```bash
git add application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/GraphDefinitionRoundtripIT.java
git commit -m "$(cat <<'EOF'
test(graph): opaque graph_definition round-trips via REST (RLS-scoped)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Frontend — graph model (types)

**Files:**
- Create: `frontend/src/app/features/form-designer/flow-graph.model.ts`
- Modify: `frontend/src/app/features/antragstyp/antragstyp-version.model.ts`

- [ ] **Step 1: Create the model**

`flow-graph.model.ts`:
```ts
import { LocaleMap } from './form-definition.model';

export type NodeType = 'START' | 'FORM' | 'APPROVAL' | 'ACTION' | 'XOR' | 'AND' | 'END';
export const NODE_TYPES: readonly NodeType[] = ['START', 'FORM', 'APPROVAL', 'ACTION', 'XOR', 'AND', 'END'];

/** Node key/title carriers (START/END carry no data). */
export interface NodeData {
  key?: string;
  title?: LocaleMap;
  assigneeRole?: string;                 // APPROVAL
  ref?: string;                          // ACTION
  inputMapping?: Record<string, string>; // ACTION
}

export interface GraphNode {
  id: string;
  type: NodeType;
  position: { x: number; y: number };
  data: NodeData;
}
export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string;
  label?: string;
  condition?: string;     // XOR outgoing edges only
}
export interface GraphDefinition {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

/** Mirrors backend BpmnCompiler key constraint (becomes BPMN id + JUEL var in SP1). */
export const NODE_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_]*$/;
export const ASSIGNEE_ROLES: readonly string[] = ['hr-reviewer', 'tenant-admin'];

export interface GraphWarning { code: string; nodeId?: string; message: string; }
```

- [ ] **Step 2: Version model field**

In `antragstyp-version.model.ts` add `graphDefinition?: GraphDefinition | null;` to the interface and import it:
```ts
import { GraphDefinition } from '../form-designer/flow-graph.model';
```

- [ ] **Step 3: Build**

Run: `docker exec hrsuite-fe-dev npx ng build`  → succeeds.

- [ ] **Step 4: Commit**
```bash
git add frontend/src/app/features/form-designer/flow-graph.model.ts \
        frontend/src/app/features/antragstyp/antragstyp-version.model.ts
git commit -m "$(cat <<'EOF'
feat(graph-ui): frontend GraphDefinition model + version model field

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Frontend — pure graph logic + spec (TDD, the correctness core)

No ngx-vflow dependency. These functions are what the component delegates to.

**Files:**
- Create: `frontend/src/app/features/form-designer/flow-graph.logic.ts`
- Test: `frontend/src/app/features/form-designer/flow-graph.logic.spec.ts`

- [ ] **Step 1: Write the failing spec**

`flow-graph.logic.spec.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { GraphDefinition } from './flow-graph.model';
import { emptyGraph, addNode, connect, validateGraph, cloneGraph } from './flow-graph.logic';

describe('flow-graph.logic', () => {
  it('emptyGraph has no nodes/edges', () => {
    expect(emptyGraph()).toEqual({ nodes: [], edges: [] });
  });

  it('addNode appends a typed node with an id and position', () => {
    let g = emptyGraph();
    g = addNode(g, 'START', { x: 0, y: 0 });
    g = addNode(g, 'ACTION', { x: 100, y: 0 });
    expect(g.nodes).toHaveLength(2);
    expect(g.nodes[0].type).toBe('START');
    expect(g.nodes[1].type).toBe('ACTION');
    expect(new Set(g.nodes.map(n => n.id)).size).toBe(2);   // unique ids
    expect(g.nodes[1].position).toEqual({ x: 100, y: 0 });
  });

  it('connect adds an edge between two existing nodes', () => {
    let g = emptyGraph();
    g = addNode(g, 'START', { x: 0, y: 0 });
    g = addNode(g, 'END', { x: 100, y: 0 });
    const [a, b] = g.nodes;
    g = connect(g, a.id, b.id);
    expect(g.edges).toHaveLength(1);
    expect(g.edges[0]).toMatchObject({ source: a.id, target: b.id });
  });

  it('cloneGraph is a deep copy (mutating clone leaves original intact)', () => {
    let g = addNode(emptyGraph(), 'FORM', { x: 0, y: 0 });
    const c = cloneGraph(g);
    c.nodes[0].data.key = 'changed';
    expect(g.nodes[0].data.key).toBeUndefined();
  });

  it('validateGraph flags missing START, missing END, invalid key, duplicate key, disconnected node, XOR without conditions', () => {
    const g: GraphDefinition = {
      nodes: [
        { id: 'a', type: 'FORM', position: { x: 0, y: 0 }, data: { key: 'bad-key' } },     // invalid key (hyphen)
        { id: 'b', type: 'FORM', position: { x: 0, y: 0 }, data: { key: 'bad-key' } },     // duplicate key
        { id: 'x', type: 'XOR', position: { x: 0, y: 0 }, data: { key: 'gw' } },           // XOR
        { id: 'd', type: 'ACTION', position: { x: 0, y: 0 }, data: { key: 'd', ref: 'r' } }, // disconnected
      ],
      edges: [
        { id: 'e1', source: 'a', target: 'b' },
        { id: 'e2', source: 'b', target: 'x' },
        { id: 'e3', source: 'x', target: 'a' },   // XOR outgoing WITHOUT condition
      ],
    };
    const codes = validateGraph(g).map(w => w.code);
    expect(codes).toContain('NO_START');
    expect(codes).toContain('NO_END');
    expect(codes).toContain('INVALID_KEY');
    expect(codes).toContain('DUPLICATE_KEY');
    expect(codes).toContain('DISCONNECTED');
    expect(codes).toContain('XOR_UNCONDITIONED');
  });

  it('validateGraph returns empty for a valid linear START->ACTION->END', () => {
    let g = emptyGraph();
    g = addNode(g, 'START', { x: 0, y: 0 });
    g = addNode(g, 'ACTION', { x: 100, y: 0 });
    g = addNode(g, 'END', { x: 200, y: 0 });
    const [s, a, e] = g.nodes;
    a.data = { key: 'prov', ref: 'r', inputMapping: {} };
    g = connect(g, s.id, a.id);
    g = connect(g, a.id, e.id);
    expect(validateGraph(g)).toEqual([]);
  });
});
```

- [ ] **Step 2: Run red**

Run: `docker exec hrsuite-fe-dev npx ng test --watch=false`  → FAIL (module not found).

- [ ] **Step 3: Implement the logic**

`flow-graph.logic.ts`:
```ts
import {
  GraphDefinition, GraphNode, GraphWarning, NodeType, NODE_KEY_PATTERN,
} from './flow-graph.model';

let _seq = 0;
function uid(prefix: string): string {
  _seq += 1;
  return `${prefix}_${Date.now().toString(36)}_${_seq}`;
}

export function emptyGraph(): GraphDefinition {
  return { nodes: [], edges: [] };
}

export function cloneGraph(g: GraphDefinition): GraphDefinition {
  return JSON.parse(JSON.stringify(g)) as GraphDefinition;
}

export function addNode(g: GraphDefinition, type: NodeType, position: { x: number; y: number }): GraphDefinition {
  const node: GraphNode = { id: uid('n'), type, position, data: {} };
  return { ...g, nodes: [...g.nodes, node] };
}

export function removeNode(g: GraphDefinition, nodeId: string): GraphDefinition {
  return {
    nodes: g.nodes.filter(n => n.id !== nodeId),
    edges: g.edges.filter(e => e.source !== nodeId && e.target !== nodeId),
  };
}

export function connect(g: GraphDefinition, source: string, target: string, sourceHandle?: string): GraphDefinition {
  const edge = { id: uid('e'), source, target, sourceHandle };
  return { ...g, edges: [...g.edges, edge] };
}

const KEY_TYPES: NodeType[] = ['FORM', 'APPROVAL', 'ACTION', 'XOR', 'AND'];

export function validateGraph(g: GraphDefinition): GraphWarning[] {
  const w: GraphWarning[] = [];
  const byType = (t: NodeType) => g.nodes.filter(n => n.type === t);

  if (byType('START').length === 0) w.push({ code: 'NO_START', message: 'Kein START-Knoten.' });
  if (byType('END').length === 0) w.push({ code: 'NO_END', message: 'Kein END-Knoten.' });

  // keys: required + pattern + unique (only for key-bearing types)
  const keyed = g.nodes.filter(n => KEY_TYPES.includes(n.type));
  const seen = new Map<string, number>();
  for (const n of keyed) {
    const key = n.data.key ?? '';
    if (!NODE_KEY_PATTERN.test(key)) {
      w.push({ code: 'INVALID_KEY', nodeId: n.id, message: `Ungültiger key "${key}".` });
    }
    seen.set(key, (seen.get(key) ?? 0) + 1);
  }
  for (const [key, count] of seen) {
    if (key && count > 1) w.push({ code: 'DUPLICATE_KEY', message: `Doppelter key "${key}".` });
  }

  // disconnected: a non-START node with no incoming AND no outgoing edge
  for (const n of g.nodes) {
    const touched = g.edges.some(e => e.source === n.id || e.target === n.id);
    if (!touched && n.type !== 'START') {
      w.push({ code: 'DISCONNECTED', nodeId: n.id, message: 'Knoten ist nicht verbunden.' });
    }
  }

  // XOR outgoing edges must all carry a condition
  for (const x of byType('XOR')) {
    const out = g.edges.filter(e => e.source === x.id);
    if (out.length === 0 || out.some(e => !e.condition || !e.condition.trim())) {
      w.push({ code: 'XOR_UNCONDITIONED', nodeId: x.id, message: 'XOR-Ausgang ohne Bedingung.' });
    }
  }

  return w;
}
```

- [ ] **Step 4: Run green**

Run: `docker exec hrsuite-fe-dev npx ng test --watch=false`  → the `flow-graph.logic` specs pass.

- [ ] **Step 5: Commit**
```bash
git add frontend/src/app/features/form-designer/flow-graph.logic.ts \
        frontend/src/app/features/form-designer/flow-graph.logic.spec.ts
git commit -m "$(cat <<'EOF'
feat(graph-ui): pure graph logic (add/remove/connect/validate) + spec

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Frontend — install ngx-vflow + confirm its API (spike)

ngx-vflow's exact API is version-specific and must be confirmed against the installed build before wiring the component. This task installs it and records the confirmed surface used by Task 6.

**Files:** `frontend/package.json` (+ lockfile)

- [ ] **Step 1: Install + check Angular-21 peer compatibility**

```bash
docker exec hrsuite-fe-dev npm install ngx-vflow
docker exec hrsuite-fe-dev npx ng build
```
Expected: install succeeds and `ng build` stays green. If npm reports an Angular peer-dependency conflict with Angular 21, STOP and report BLOCKED with the version error — the fallback (per ADR-012) is an alternative lib (`@foblex/flow`) or a custom SVG+CDK editor; that is a plan change, not a guess.

- [ ] **Step 2: Confirm the API surface used by Task 6**

From `node_modules/ngx-vflow` (README / `public-api`/`.d.ts`), confirm and write down (in the commit message body) the exact:
- import module/standalone component (e.g. `Vflow` / `VflowComponent`, selector `vflow`),
- node input shape (id, point/position `{x,y}`, `type`, `data`) and how custom node templates are declared (e.g. `<ng-template let-ctx [vflowNode]="'form'">`),
- edge input shape (id, source, target, optional source handle/label),
- the connection output event name + payload (e.g. `(connect)` → `{ source, target, sourceHandle?, targetHandle? }`),
- how to two-way/observe node position changes (so saved positions stay in sync).

Run: `docker exec hrsuite-fe-dev sh -lc 'ls node_modules/ngx-vflow && sed -n "1,80p" node_modules/ngx-vflow/README.md 2>/dev/null'`

- [ ] **Step 3: Commit the dependency**
```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "$(cat <<'EOF'
build(graph-ui): add ngx-vflow dependency for the flow canvas editor

Confirmed API (vs installed version): <fill from Step 2 — component, node/edge inputs, connect event>

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Frontend — FlowCanvasEditorComponent (render shell)

A thin component over ngx-vflow that holds the graph as a signal, renders nodes/edges, exposes a palette (add node), a node side-panel (edit data), and an edge-condition editor — delegating all graph mutations to Task 4's pure functions. Wire it against the API confirmed in Task 5.

**Files:**
- Create: `flow-canvas-editor.component.ts`, `.html`, `.scss`, `.spec.ts`

- [ ] **Step 1: Write the failing spec (logic-facing, no rendering assertions)**

`flow-canvas-editor.component.spec.ts`:
```ts
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { describe, it, expect, beforeEach } from 'vitest';
import { FlowCanvasEditorComponent } from './flow-canvas-editor.component';

describe('FlowCanvasEditorComponent', () => {
  let cmp: FlowCanvasEditorComponent;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FlowCanvasEditorComponent, TranslateModule.forRoot()],
    }).compileComponents();
    const f = TestBed.createComponent(FlowCanvasEditorComponent);
    cmp = f.componentInstance;
    f.componentRef.setInput('availableRefs', ['provision-ad-account']);
  });

  it('addNode adds to the graph; toGraphDefinition round-trips via loadGraph', () => {
    cmp.addNode('START');
    cmp.addNode('ACTION');
    expect(cmp.graph().nodes.length).toBe(2);
    const def = cmp.toGraphDefinition();
    const cmp2 = TestBed.createComponent(FlowCanvasEditorComponent).componentInstance;
    cmp2.loadGraph(def);
    expect(cmp2.graph().nodes.length).toBe(2);
    expect(cmp2.graph().nodes.map(n => n.type)).toEqual(['START', 'ACTION']);
  });

  it('toGraphDefinition returns null when the graph is empty', () => {
    expect(cmp.toGraphDefinition()).toBeNull();
  });

  it('exposes validation warnings from the pure logic', () => {
    cmp.addNode('XOR');   // no START/END, XOR unconditioned, missing key
    expect(cmp.warnings().map(w => w.code)).toContain('NO_START');
  });
});
```

- [ ] **Step 2: Run red** → FAIL (component missing).

- [ ] **Step 3: Implement the component**

`flow-canvas-editor.component.ts` — holds the graph signal and delegates to the pure logic. Render via ngx-vflow using the API confirmed in Task 5 (the `<vflow>` markup below is the documented shape; adjust attribute/event names to the confirmed version):
```ts
import { Component, computed, inject, input, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Vflow } from 'ngx-vflow';                       // confirm exact export in Task 5

import {
  GraphDefinition, GraphNode, GraphWarning, NodeType, NODE_TYPES, ASSIGNEE_ROLES,
} from './flow-graph.model';
import { addNode, connect, emptyGraph, removeNode, validateGraph, cloneGraph } from './flow-graph.logic';

@Component({
  selector: 'app-flow-canvas-editor',
  standalone: true,
  imports: [Vflow, ReactiveFormsModule, TranslateModule],   // adjust Vflow import to confirmed symbol
  templateUrl: './flow-canvas-editor.component.html',
  styleUrl: './flow-canvas-editor.component.scss',
})
export class FlowCanvasEditorComponent {
  readonly availableRefs = input<string[]>([]);
  readonly nodeTypes = NODE_TYPES.filter(t => t !== 'START' || true); // palette shows all; Start guarded in addNode
  readonly assigneeRoles = ASSIGNEE_ROLES;

  readonly graph = signal<GraphDefinition>(emptyGraph());
  readonly selectedNodeId = signal<string | null>(null);
  readonly warnings = computed<GraphWarning[]>(() => validateGraph(this.graph()));

  selectedNode = computed<GraphNode | null>(() => {
    const id = this.selectedNodeId();
    return id ? this.graph().nodes.find(n => n.id === id) ?? null : null;
  });

  addNode(type: NodeType): void {
    if (type === 'START' && this.graph().nodes.some(n => n.type === 'START')) return; // single start
    const offset = this.graph().nodes.length * 40;
    this.graph.set(addNode(this.graph(), type, { x: 80 + offset, y: 80 + offset }));
  }

  removeNode(id: string): void {
    this.graph.set(removeNode(this.graph(), id));
    if (this.selectedNodeId() === id) this.selectedNodeId.set(null);
  }

  /** ngx-vflow (connect) handler — wire to the confirmed event in Step 1 of Task 5. */
  onConnect(ev: { source: string; target: string; sourceHandle?: string }): void {
    this.graph.set(connect(this.graph(), ev.source, ev.target, ev.sourceHandle));
  }

  select(id: string): void { this.selectedNodeId.set(id); }

  /** Mutates the selected node's data immutably. */
  patchSelected(patch: Partial<GraphNode['data']>): void {
    const id = this.selectedNodeId();
    if (!id) return;
    const g = cloneGraph(this.graph());
    const n = g.nodes.find(x => x.id === id);
    if (n) { n.data = { ...n.data, ...patch }; this.graph.set(g); }
  }

  /** Persist position updates coming from ngx-vflow drag (wire in template per confirmed API). */
  updatePosition(id: string, position: { x: number; y: number }): void {
    const g = cloneGraph(this.graph());
    const n = g.nodes.find(x => x.id === id);
    if (n) { n.position = position; this.graph.set(g); }
  }

  toGraphDefinition(): GraphDefinition | null {
    const g = this.graph();
    return g.nodes.length === 0 ? null : cloneGraph(g);
  }

  loadGraph(def: GraphDefinition | null | undefined): void {
    this.graph.set(def ? cloneGraph(def) : emptyGraph());
    this.selectedNodeId.set(null);
  }
}
```

`flow-canvas-editor.component.html` — palette + `<vflow>` canvas + side panel. Use the confirmed ngx-vflow markup; this is the documented shape:
```html
<div class="canvas-wrap">
  <aside class="palette">
    <span>{{ 'flow.canvas.palette' | translate }}</span>
    @for (t of nodeTypes; track t) {
      <button type="button" (click)="addNode(t)">+ {{ t }}</button>
    }
    @if (availableRefs().length === 0) { <p class="hint">{{ 'flow.canvas.noRefs' | translate }}</p> }
  </aside>

  <!-- ngx-vflow canvas: bind nodes/edges, listen for connect + node selection/drag.
       Replace attribute/event names with those confirmed in Task 5. -->
  <vflow class="canvas"
         [nodes]="graph().nodes"
         [edges]="graph().edges"
         (connect)="onConnect($event)">
    <!-- custom node templates per type go here (confirmed ngx-vflow template API) -->
  </vflow>

  <aside class="inspector">
    @if (selectedNode(); as n) {
      <h4>{{ n.type }}</h4>
      @if (n.type !== 'START' && n.type !== 'END') {
        <label>{{ 'flow.canvas.key' | translate }}
          <input [value]="n.data.key ?? ''" (input)="patchSelected({ key: $any($event.target).value })" />
        </label>
      }
      @if (n.type === 'APPROVAL') {
        <label>{{ 'flow.canvas.assigneeRole' | translate }}
          <select [value]="n.data.assigneeRole ?? 'hr-reviewer'"
                  (change)="patchSelected({ assigneeRole: $any($event.target).value })">
            @for (r of assigneeRoles; track r) { <option [value]="r">{{ r }}</option> }
          </select>
        </label>
      }
      @if (n.type === 'ACTION') {
        <label>{{ 'flow.canvas.ref' | translate }}
          <select [value]="n.data.ref ?? ''" (change)="patchSelected({ ref: $any($event.target).value })">
            <option value="" disabled>—</option>
            @for (r of availableRefs(); track r) { <option [value]="r">{{ r }}</option> }
          </select>
        </label>
      }
      <button type="button" (click)="removeNode(n.id)">{{ 'flow.canvas.delete' | translate }}</button>
    } @else {
      <p class="hint">{{ 'flow.canvas.selectHint' | translate }}</p>
    }

    @if (warnings().length) {
      <ul class="warnings">
        @for (w of warnings(); track w.message) { <li>⚠ {{ w.message }}</li> }
      </ul>
    }
  </aside>
</div>
```

`flow-canvas-editor.component.scss`:
```scss
.canvas-wrap { display: grid; grid-template-columns: 10rem 1fr 16rem; gap: .5rem; height: 32rem; }
.palette { display: flex; flex-direction: column; gap: .4rem; }
.canvas { border: 1px solid #ccc; border-radius: 6px; min-height: 32rem; }
.inspector { border-left: 1px solid #eee; padding-left: .5rem; display: flex; flex-direction: column; gap: .5rem; }
.inspector label { display: block; }
.inspector input, .inspector select { width: 100%; }
.warnings { color: #8a6d00; font-size: .85rem; padding-left: 1rem; }
.hint { color: #777; font-size: .85rem; }
```

> The graph-mutation methods (`addNode`/`removeNode`/`connect`/validation/`toGraphDefinition`/`loadGraph`) are fully covered by Task 4 + the component spec. The ngx-vflow markup (node templates, drag-position binding, edge-condition editing) is wired against the Task-5-confirmed API; if the confirmed API differs, adjust the template/event handlers — the component's public methods and the pure logic stay unchanged.

- [ ] **Step 4: Run green + build**

```bash
docker exec hrsuite-fe-dev npx ng test --watch=false
docker exec hrsuite-fe-dev npx ng build
```
Expected: component specs pass; build clean.

- [ ] **Step 5: Commit**
```bash
git add frontend/src/app/features/form-designer/flow-canvas-editor.component.ts \
        frontend/src/app/features/form-designer/flow-canvas-editor.component.html \
        frontend/src/app/features/form-designer/flow-canvas-editor.component.scss \
        frontend/src/app/features/form-designer/flow-canvas-editor.component.spec.ts
git commit -m "$(cat <<'EOF'
feat(graph-ui): FlowCanvasEditorComponent (ngx-vflow shell over pure graph logic)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Frontend — service + builder integration (swap list→canvas)

**Files:**
- Modify: `antragstyp.service.ts`, `form-designer.component.ts`, `form-designer.component.html`
- Delete: `flow-step-editor.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Service — send graphDefinition**

In `antragstyp.service.ts`, extend `createDraftVersion` and `editDraft` to accept a 4th arg `graphDefinition: unknown | null` and include it in the body only when non-null:
```ts
  createDraftVersion(id: string, formDefinition: FormDefinition,
                     flowDefinition: FlowDefinition | null,
                     graphDefinition: unknown | null = null): Observable<AntragsTypVersion> {
    const body: Record<string, unknown> = { formDefinition, workflowBpmn: '<bpmn/>', sfActionBindings: {} };
    if (flowDefinition) body['flowDefinition'] = flowDefinition;
    if (graphDefinition) body['graphDefinition'] = graphDefinition;
    return this.http.post<AntragsTypVersion>(`${this.base}/antragstyp/${id}/versions`, body);
  }

  editDraft(versionId: string, formDefinition: FormDefinition,
            flowDefinition: FlowDefinition | null,
            graphDefinition: unknown | null = null): Observable<AntragsTypVersion> {
    const body: Record<string, unknown> = { formDefinition, workflowBpmn: '<bpmn/>', sfActionBindings: {} };
    if (flowDefinition) body['flowDefinition'] = flowDefinition;
    if (graphDefinition) body['graphDefinition'] = graphDefinition;
    return this.http.put<AntragsTypVersion>(`${this.base}/antragstyp/versions/${versionId}/draft`, body);
  }
```
Update `antragstyp.service.spec.ts`: the existing `createDraftVersion`/`editDraft` calls still pass (4th arg defaults to null); add one assertion that `graphDefinition` is included when passed and omitted when null (mirror the existing flow tests).

- [ ] **Step 2: Builder — swap editor + save graph + publish gate**

In `form-designer.component.ts`:
- Replace the `FlowStepEditorComponent` import/usage with `FlowCanvasEditorComponent`; `@ViewChild(FlowCanvasEditorComponent) flowCanvas?`.
- In the load `forkJoin` callback, after computing `source`, set `this.pendingGraph = source?.graphDefinition ?? null;` and seed the canvas (same kept-mounted + one-shot `seedFlowEditor()` pattern already used — rename to `seedCanvas()` calling `this.flowCanvas?.loadGraph(this.pendingGraph)`).
- `save()`: `const graph = this.flowCanvas?.toGraphDefinition() ?? null;` then `createDraftVersion(id, formDefinition, null, graph)` / `editDraft(draftId, formDefinition, null, graph)` (flowDefinition stays null in SP2 — graph is the authoring model now).
- `canPublish` getter: `return !!this.draftVersionId && !this.flowCanvas?.toGraphDefinition();` — i.e. **publish disabled whenever a graph exists** (compiler is SP1). Bind the Publish button `[disabled]="publishing || !canPublish"` and show the tooltip/hint key `builder.publishDeferred` when a graph is present.

In `form-designer.component.html`: replace `<app-flow-step-editor ... />` with
```html
<app-flow-canvas-editor [availableRefs]="availableRefs" [hidden]="section !== 'flow'" />
```
and in the Publish section add, when a graph exists:
```html
@if (flowCanvas?.toGraphDefinition()) { <p class="hint">{{ 'builder.publishDeferred' | translate }}</p> }
```

- [ ] **Step 3: Delete the list editor**

```bash
git rm frontend/src/app/features/form-designer/flow-step-editor.component.ts \
       frontend/src/app/features/form-designer/flow-step-editor.component.html \
       frontend/src/app/features/form-designer/flow-step-editor.component.scss \
       frontend/src/app/features/form-designer/flow-step-editor.component.spec.ts
```
Remove any remaining references (imports) to `FlowStepEditorComponent`.

- [ ] **Step 4: Build + test green**

```bash
docker exec hrsuite-fe-dev npx ng build
docker exec hrsuite-fe-dev npx ng test --watch=false
```
Expected: build clean; all specs pass (service, canvas, logic, create page, builder).

- [ ] **Step 5: Commit**
```bash
git add frontend/src/app/features/antragstyp/antragstyp.service.ts \
        frontend/src/app/features/antragstyp/antragstyp.service.spec.ts \
        frontend/src/app/features/form-designer/form-designer.component.ts \
        frontend/src/app/features/form-designer/form-designer.component.html
git add -A frontend/src/app/features/form-designer/   # picks up the git rm deletions
git commit -m "$(cat <<'EOF'
feat(graph-ui): builder uses flow canvas editor; save graph opaque; publish gated to SP1

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Frontend — i18n keys (de/fr/it/en)

**Files:** `frontend/src/assets/i18n/{de,fr,it,en}.json`

- [ ] **Step 1: Add keys to every locale** (merge into the existing root, don't clobber). German values:
```json
{
  "flow": {
    "canvas": {
      "palette": "Knoten",
      "key": "Schlüssel",
      "assigneeRole": "Zuständige Rolle",
      "ref": "Aktion (n8n-ref)",
      "delete": "Knoten löschen",
      "selectHint": "Knoten auswählen, um ihn zu bearbeiten.",
      "noRefs": "Keine n8n-Refs für diesen Tenant — ACTION-Knoten ohne Ref."
    }
  },
  "builder": {
    "publishDeferred": "Veröffentlichen für Graph-Flows folgt (Compiler = SP1)."
  }
}
```
Provide idiomatic FR/IT and the EN equivalents (canvas.palette "Nodes", key "Key", assigneeRole "Assignee role", ref "Action (n8n ref)", delete "Delete node", selectHint "Select a node to edit it.", noRefs "No n8n refs for this tenant — ACTION nodes have no ref."; builder.publishDeferred "Publishing graph flows is coming (compiler = SP1)."). Merge `flow.canvas` into the existing `flow` object; add `builder.publishDeferred` into the existing `builder` object.

- [ ] **Step 2: Build (validates JSON)**

Run: `docker exec hrsuite-fe-dev npx ng build`  → succeeds.

- [ ] **Step 3: Commit**
```bash
git add frontend/src/assets/i18n/de.json frontend/src/assets/i18n/fr.json \
        frontend/src/assets/i18n/it.json frontend/src/assets/i18n/en.json
git commit -m "$(cat <<'EOF'
feat(graph-ui): i18n keys for the flow canvas editor (de/fr/it/en)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Full verify gate + browser smoke

- [ ] **Step 1: Backend verify**

Run: `... mvn -ntp -pl application -am verify`
Expected: BUILD SUCCESS — incl. `GraphDefinitionRoundtripIT`, all prior ITs, `ModularityTests`.

- [ ] **Step 2: Frontend test + build**

```bash
docker exec hrsuite-fe-dev npx ng test --watch=false
docker exec hrsuite-fe-dev npx ng build
```
Expected: all specs pass; build clean.

- [ ] **Step 3: Rebuild + restart stack**

```bash
docker compose build backend app && docker compose up -d backend app
docker inspect -f '{{.State.Health.Status}}' hrsuite-backend
```

- [ ] **Step 4: Browser smoke (http://localhost:4200, hr-designer tenant with seeded n8n config)**

Run `bash scripts/dev-seed.sh` first if needed (seeds the fixed tenant + an action ref). Then:
1. `/antragstypen` → **+ Neuer Antragstyp** → create.
2. Builder → **Flow** tab → add `START`, `ACTION` (pick a ref), `END`; connect Start→Action→End; set the ACTION key.
3. **Veröffentlichen** → Save draft → confirm the Publish button is **disabled** with the "compiler = SP1" hint.
4. Reload the page → reopen the Flow tab → the graph (nodes/edges/positions) is **restored** (opaque round-trip).
5. DB check: `docker exec -e PGPASSWORD=dev hrsuite-postgres psql -U hrsuite -d hrsuite -c "select graph_definition is not null as has_graph from antragstyp_version order by created_at desc limit 1;"` → `t`.

Document the result (screenshot/notes). Fix before completing if anything fails.

- [ ] **Step 5: Commit any fixups**
```bash
git add -- application/ frontend/
git commit -m "$(cat <<'EOF'
test(graph-ui): full verify green for SP2 (flow canvas editor + opaque graph storage)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review (completed by plan author)

**Spec coverage:**
- Opaque `graph_definition` storage (migration 009, JsonNode, DTO/service/controller, publish unchanged) ✓ Task 1; round-trip IT ✓ Task 2
- Preliminary graph model (nodes/edges/positions, NodeType, key pattern) ✓ Task 3
- Pure (de)serialise + validate logic (the correctness core, no vflow dep) ✓ Task 4
- ngx-vflow dependency + API confirmation (de-risk) ✓ Task 5
- FlowCanvasEditorComponent: palette (Start/FORM/APPROVAL/ACTION/XOR/AND/End), node side-panel (key/title/assigneeRole/ref), connect, validation warnings, to/loadGraph ✓ Task 6
- Service graphDefinition + builder swap (list→canvas), save graph, publish gated, list editor removed ✓ Task 7
- i18n de/fr/it/en ✓ Task 8
- Verify gate + browser round-trip smoke (publish disabled) ✓ Task 9
- Single START guard, XOR-condition validation, disconnected/duplicate-key warnings ✓ Tasks 4/6

**Placeholder scan:** The only intentional verification point is Task 5 (confirm ngx-vflow's exact API) — a real spike, not a placeholder; Task 6's vflow markup is explicitly "adjust to confirmed API", with all correctness logic in tested pure functions (Task 4) + component methods (Task 6 spec). No `TBD`/`TODO`.

**Type consistency:** `GraphDefinition`/`GraphNode`/`GraphEdge`/`NodeType`/`NodeData` (Task 3) are used by the logic (Task 4), the component (Task 6), and the version model. `toGraphDefinition(): GraphDefinition | null`, `loadGraph(GraphDefinition|null)`, service `createDraftVersion(id, FormDefinition, FlowDefinition|null, unknown|null)` / `editDraft(...)` are consistent across Tasks 4/6/7. Backend `graphDefinition` is `JsonNode` (opaque) in entity/DTO/service/controller (Task 1), matched by FE `graphDefinition?: GraphDefinition` (out) / `unknown` (in).

**Known follow-ups (out of scope, per ADR-012):** SP1 (graph→BPMN compiler: XOR→exclusiveGateway, AND→parallelGateway split/join, edges→sequenceFlows w/ conditions; full validation; enable publish), SP3 (timer/sub-flow nodes, auto-layout), edge-condition inline editor polish, Cut D runtime.
