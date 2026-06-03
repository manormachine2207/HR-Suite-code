# Cut C — Low-Code-Flow-Editor (HR-UI) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR can create an Antragstyp and author its Flow (FORM/APPROVAL/ACTION steps) and publish it — entirely in the HR-UI, as a 1:1 surface over the existing Cut B REST path.

**Architecture:** Frontend-heavy. One new backend read-endpoint `GET /api/v1/action/refs` (tenant `allowed_refs`). The existing designer route becomes a sectioned "Antragstyp-Builder" (Formular | Flow | Veröffentlichen); a new `/antragstypen/neu` create page; a new flow-step-editor component (reactive forms + signals). Cut B already provides the compiler, `publish()`, and DTOs carrying `flowDefinition`/`processDefinitionKey`.

**Tech Stack:** Backend Java 21 / Spring Boot 3.4.x, Spring Security method security (`@PreAuthorize`), JPA, Testcontainers. Frontend Angular 21 (zoneless), standalone components, reactive forms, signals, ngx-translate, Oblique, vitest.

**Branch:** `feat/cut-c-flow-editor` (already created from `main`).

**Spec:** `docs/superpowers/specs/2026-06-02-cut-c-flow-editor-design.md`

**Backend Maven runner (from repo root `/Users/david.berier/Desktop/Git Repos/HR-Suite-code`):**
```bash
docker run --rm -v "$PWD":/work -w /work -v hrsuite-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true \
  maven:3.9-eclipse-temurin-21 mvn -ntp -pl application -am <goals>
```

**Frontend runner** — the dev container `hrsuite-fe-dev` (node:22-alpine) is running with deps installed and the repo `frontend/` bind-mounted, so run FE commands inside it:
```bash
docker exec hrsuite-fe-dev npx ng test --watch=false        # vitest
docker exec hrsuite-fe-dev npx ng build                      # prod build
```
(If `hrsuite-fe-dev` is not running: `docker compose up -d` first, or use `docker run --rm -v "$PWD/frontend":/work -w /work node:22-alpine sh -c "npm ci && npx ng test --watch=false"`.)

**Commit footer (every commit):** `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

**Do NOT commit:** `application/src/main/java/io/github/manormachine2207/hrsuite/config/RuntimeDbRoleCheck.java`, `application/src/test/java/io/github/manormachine2207/hrsuite/config/` (foreign untracked files). Always `git add` explicit paths, never `-A`/`.`.

---

## File Structure

**Backend — new:**
- `application/src/main/java/io/github/manormachine2207/hrsuite/action/ActionRefsController.java` — `GET /api/v1/action/refs`
- `application/src/test/java/io/github/manormachine2207/hrsuite/action/ActionRefsControllerTest.java` — unit test
- `application/src/test/java/io/github/manormachine2207/hrsuite/action/ActionRefsIT.java` — RLS isolation IT

**Frontend — new:**
- `frontend/src/app/features/form-designer/flow-definition.model.ts` — flow step types
- `frontend/src/app/features/antragstyp/antragstyp-create.component.ts` (+`.html`,`.scss`) — `/antragstypen/neu`
- `frontend/src/app/features/form-designer/flow-step-editor.component.ts` (+`.html`,`.scss`) — flow section
- `frontend/src/app/features/form-designer/flow-step-editor.component.spec.ts`
- `frontend/src/app/features/antragstyp/antragstyp.service.spec.ts`
- `frontend/src/app/features/antragstyp/antragstyp-create.component.spec.ts`

**Frontend — modified:**
- `frontend/src/app/features/antragstyp/antragstyp-version.model.ts` — add `flowDefinition`, `processDefinitionKey`
- `frontend/src/app/features/antragstyp/antragstyp.service.ts` — +4 methods, extend `createDraftVersion`
- `frontend/src/app/features/form-designer/form-designer.component.ts` (+`.html`) — sectioned builder + publish
- `frontend/src/app/features/antragstyp/antragstyp-list.component.html` — "+ Neuer Antragstyp" link
- `frontend/src/app/app.routes.ts` — `/antragstypen/neu` route
- `frontend/src/assets/i18n/{de,fr,it,en}.json` — new keys

---

## Task 1: Backend — `GET /api/v1/action/refs` controller + unit test

**Files:**
- Create: `application/src/main/java/io/github/manormachine2207/hrsuite/action/ActionRefsController.java`
- Test: `application/src/test/java/io/github/manormachine2207/hrsuite/action/ActionRefsControllerTest.java`

- [ ] **Step 1: Write the failing unit test**

`application/src/test/java/io/github/manormachine2207/hrsuite/action/ActionRefsControllerTest.java`:
```java
package io.github.manormachine2207.hrsuite.action;

import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActionRefsControllerTest {

    private final TenantN8nConfigRepository repo = mock(TenantN8nConfigRepository.class);
    private final ActionRefsController controller = new ActionRefsController(repo);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void returnsAllowedRefsForCurrentTenant() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        when(repo.findById(tenant)).thenReturn(Optional.of(
                new TenantN8nConfig(tenant, "http://n8n:5678", "secret",
                        List.of("provision-ad-account", "sync-payroll"))));

        assertThat(controller.refs()).containsExactly("provision-ad-account", "sync-payroll");
    }

    @Test
    void returnsEmptyListWhenNoConfigForTenant() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        when(repo.findById(tenant)).thenReturn(Optional.empty());

        assertThat(controller.refs()).isEmpty();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `... mvn -ntp -pl application -am test -Dtest=ActionRefsControllerTest`
Expected: FAIL — `ActionRefsController` does not exist.

- [ ] **Step 3: Implement the controller**

`application/src/main/java/io/github/manormachine2207/hrsuite/action/ActionRefsController.java`:
```java
package io.github.manormachine2207.hrsuite.action;

import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only listing of the n8n action references allow-listed for the current tenant
 * ({@code tenant_n8n_config.allowed_refs}). Used by the Cut C flow editor to populate the
 * ACTION step's ref dropdown. Authoring-only: restricted to hr-designer. RLS (ADR-008)
 * already scopes the row by {@code app.tenant_id}; we additionally look up by the
 * TenantContext id so an empty/absent config yields an empty list (never another tenant's).
 */
@RestController
@RequestMapping("/api/v1/action")
public class ActionRefsController {

    private final TenantN8nConfigRepository configRepo;

    public ActionRefsController(TenantN8nConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    @GetMapping("/refs")
    @PreAuthorize("hasRole('hr-designer')")
    public List<String> refs() {
        return configRepo.findById(TenantContext.require())
                .map(TenantN8nConfig::getAllowedRefs)
                .orElseGet(List::of);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `... mvn -ntp -pl application -am test -Dtest=ActionRefsControllerTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/io/github/manormachine2207/hrsuite/action/ActionRefsController.java \
        application/src/test/java/io/github/manormachine2207/hrsuite/action/ActionRefsControllerTest.java
git commit -m "$(cat <<'EOF'
feat(action): GET /api/v1/action/refs — tenant allowed_refs for the flow editor (Cut C)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Backend — RLS integration test for `/action/refs`

Proves the endpoint is tenant-isolated end-to-end (as the NOSUPERUSER `hrsuite_app` role): tenant A sees only its own refs; a tenant with no config gets `[]`.

**Files:**
- Create: `application/src/test/java/io/github/manormachine2207/hrsuite/action/ActionRefsIT.java`

- [ ] **Step 1: Inspect the existing Cut-A IT harness**

Read `application/src/test/java/io/github/manormachine2207/hrsuite/action/N8nActionConnectorIT.java` and the `ActionItHarness` it uses. Confirm:
- the `@SpringBootTest` boot config (classes, `@ActiveProfiles("dev")`, `@Testcontainers`, `PostgreSQLContainer` with `withInitScript("db/rls-it-init.sql")`, `@DynamicPropertySource` datasource as `hrsuite_app`),
- `ActionItHarness.seedConfig(UUID tenantId, String baseUrl, String hmacSecret, List<String> allowedRefs)` exact signature,
- how a tenant is created (REST `POST /api/v1/tenant` with `dev-platform-admin`) and the dev token format (`dev-hr-designer~<tenantId>`).

Use the SAME patterns below; adjust import/símbol names to match what you read.

- [ ] **Step 2: Write the IT**

`application/src/test/java/io/github/manormachine2207/hrsuite/action/ActionRefsIT.java`:
```java
package io.github.manormachine2207.hrsuite.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.manormachine2207.hrsuite.HrSuiteApplication;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = HrSuiteApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
@Import(ActionItHarness.class)
class ActionRefsIT {

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
    @Autowired ActionItHarness harness;
    private final ObjectMapper mapper = new ObjectMapper();

    private String createTenant(String code) throws Exception {
        HttpHeaders admin = new HttpHeaders();
        admin.setContentType(MediaType.APPLICATION_JSON);
        admin.setBearerAuth("dev-platform-admin");
        String body = rest.exchange("/api/v1/tenant", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"" + code + "\",\"subdomain\":\"" + code.toLowerCase()
                        + "\",\"displayName\":{\"de\":\"" + code + "\"}}", admin), String.class).getBody();
        return mapper.readTree(body).get("id").asText();
    }

    private HttpHeaders designer(String tenantId) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth("dev-hr-designer~" + tenantId);
        return h;
    }

    @Test
    void refsAreTenantScoped() throws Exception {
        String tenantA = createTenant("REFSA");
        String tenantB = createTenant("REFSB");

        TenantContext.set(UUID.fromString(tenantA));
        harness.seedConfig(UUID.fromString(tenantA), "http://n8n:5678", "secret",
                List.of("provision-ad-account", "sync-payroll"));
        TenantContext.clear();

        // Tenant A sees its two refs
        String[] a = rest.exchange("/api/v1/action/refs", HttpMethod.GET,
                new HttpEntity<>(designer(tenantA)), String[].class).getBody();
        assertThat(a).containsExactly("provision-ad-account", "sync-payroll");

        // Tenant B has no config -> empty list (never A's)
        String[] b = rest.exchange("/api/v1/action/refs", HttpMethod.GET,
                new HttpEntity<>(designer(tenantB)), String[].class).getBody();
        assertThat(b).isEmpty();
    }
}
```

> If `ActionItHarness.seedConfig` differs (e.g. needs to run inside `inTenant(...)` rather than a manual `TenantContext.set`), mirror exactly how `N8nActionConnectorIT` calls it.

- [ ] **Step 3: Run the IT (green)**

Run: `... mvn -ntp -pl application -am verify -Dit.test=ActionRefsIT -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: `Tests run: 1, Failures: 0`.

- [ ] **Step 4: Commit**

```bash
git add application/src/test/java/io/github/manormachine2207/hrsuite/action/ActionRefsIT.java
git commit -m "$(cat <<'EOF'
test(action): RLS IT for GET /api/v1/action/refs (tenant isolation)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Frontend — flow model + version model update

Type-only files (no unit test; verified by build + later component tests).

**Files:**
- Create: `frontend/src/app/features/form-designer/flow-definition.model.ts`
- Modify: `frontend/src/app/features/antragstyp/antragstyp-version.model.ts`

- [ ] **Step 1: Create the flow model**

`frontend/src/app/features/form-designer/flow-definition.model.ts`:
```ts
import { LocaleMap } from './form-definition.model';

/** Step kinds the Cut C editor supports (BRANCH is intentionally excluded — not yet compilable). */
export type StepKind = 'FORM' | 'APPROVAL' | 'ACTION';
export const STEP_KINDS: readonly StepKind[] = ['FORM', 'APPROVAL', 'ACTION'];

export interface FormStepDef {
  kind: 'FORM';
  key: string;
  title: LocaleMap;
}

export interface ApprovalStepDef {
  kind: 'APPROVAL';
  key: string;
  title: LocaleMap;
  assigneeRole: string;
  outcomes: ['approve', 'reject'];
}

export interface ActionStepDef {
  kind: 'ACTION';
  key: string;
  title: LocaleMap;
  ref: string;
  inputMapping: Record<string, string>;
}

export type FlowStepDef = FormStepDef | ApprovalStepDef | ActionStepDef;

export interface FlowDefinition {
  steps: FlowStepDef[];
}

/** Step key must be a BPMN id + JUEL variable name (mirrors backend BpmnCompiler.KEY_PATTERN). */
export const STEP_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_]*$/;

/** Assignee roles offerable for APPROVAL steps. */
export const ASSIGNEE_ROLES: readonly string[] = ['hr-reviewer', 'tenant-admin'];
```

- [ ] **Step 2: Update the version model**

In `frontend/src/app/features/antragstyp/antragstyp-version.model.ts`, add the import and two fields:
```ts
import { FlowDefinition } from '../form-designer/flow-definition.model';
```
Add inside the `AntragsTypVersion` interface (after `workflowBpmn`):
```ts
  flowDefinition?: FlowDefinition | null;
  processDefinitionKey?: string | null;
```

- [ ] **Step 3: Verify it compiles**

Run: `docker exec hrsuite-fe-dev npx ng build`
Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/form-designer/flow-definition.model.ts \
        frontend/src/app/features/antragstyp/antragstyp-version.model.ts
git commit -m "$(cat <<'EOF'
feat(flow-ui): frontend FlowDefinition model + version model flowDefinition/processDefinitionKey

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Frontend — service methods + spec (TDD)

**Files:**
- Modify: `frontend/src/app/features/antragstyp/antragstyp.service.ts`
- Test: `frontend/src/app/features/antragstyp/antragstyp.service.spec.ts`

- [ ] **Step 1: Write the failing service spec**

`frontend/src/app/features/antragstyp/antragstyp.service.spec.ts`:
```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';

import { AntragsTypService } from './antragstyp.service';
import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';
import { FormDefinition } from '../form-designer/form-definition.model';
import { FlowDefinition } from '../form-designer/flow-definition.model';

const stubConfig = { get: () => ({ apiBaseUrl: '/api/v1' }) } as Partial<RuntimeConfigService>;

describe('AntragsTypService', () => {
  let service: AntragsTypService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AntragsTypService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RuntimeConfigService, useValue: stubConfig },
      ],
    });
    service = TestBed.inject(AntragsTypService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('createAntragstyp POSTs key + title', () => {
    const title = { de: 'Urlaub' };
    service.createAntragstyp('urlaubsantrag', title).subscribe();
    const req = http.expectOne('/api/v1/antragstyp');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ key: 'urlaubsantrag', title });
    req.flush({ id: 'at1' });
  });

  it('createDraftVersion sends form + flow when flow provided', () => {
    const form: FormDefinition = { fields: [] };
    const flow: FlowDefinition = { steps: [{ kind: 'FORM', key: 'a', title: { de: 'A' } }] };
    service.createDraftVersion('at1', form, flow).subscribe();
    const req = http.expectOne('/api/v1/antragstyp/at1/versions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.formDefinition).toEqual(form);
    expect(req.request.body.flowDefinition).toEqual(flow);
    req.flush({ id: 'v1' });
  });

  it('createDraftVersion omits flowDefinition when flow is null', () => {
    service.createDraftVersion('at1', { fields: [] }, null).subscribe();
    const req = http.expectOne('/api/v1/antragstyp/at1/versions');
    expect('flowDefinition' in req.request.body).toBe(false);
    req.flush({ id: 'v1' });
  });

  it('editDraft PUTs form + flow', () => {
    const flow: FlowDefinition = { steps: [] };
    service.editDraft('v1', { fields: [] }, flow).subscribe();
    const req = http.expectOne('/api/v1/antragstyp/versions/v1/draft');
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 'v1' });
  });

  it('publish POSTs to the publish endpoint', () => {
    service.publish('v1').subscribe();
    const req = http.expectOne('/api/v1/antragstyp/versions/v1/publish');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'v1', status: 'PUBLISHED' });
  });

  it('listActionRefs GETs /action/refs', () => {
    let result: string[] | undefined;
    service.listActionRefs().subscribe(r => (result = r));
    const req = http.expectOne('/api/v1/action/refs');
    expect(req.request.method).toBe('GET');
    req.flush(['provision-ad-account']);
    expect(result).toEqual(['provision-ad-account']);
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `docker exec hrsuite-fe-dev npx ng test --watch=false`
Expected: FAIL — `createAntragstyp`/`editDraft`/`publish`/`listActionRefs` not functions; flow not sent.

- [ ] **Step 3: Implement the service methods**

In `frontend/src/app/features/antragstyp/antragstyp.service.ts`:
- add import: `import { FlowDefinition } from '../form-designer/flow-definition.model';`
- add import: `import { LocaleMap } from '../form-designer/form-definition.model';`
- replace the existing `createDraftVersion` method and append the new methods:
```ts
  createAntragstyp(key: string, title: LocaleMap): Observable<AntragsTypSummary> {
    return this.http.post<AntragsTypSummary>(`${this.base}/antragstyp`, { key, title });
  }

  /** Creates a new DRAFT major carrying the form and (optional) flow definition. */
  createDraftVersion(id: string, formDefinition: FormDefinition,
                     flowDefinition: FlowDefinition | null): Observable<AntragsTypVersion> {
    const body: Record<string, unknown> = { formDefinition, workflowBpmn: '<bpmn/>', sfActionBindings: {} };
    if (flowDefinition) {
      body['flowDefinition'] = flowDefinition;
    }
    return this.http.post<AntragsTypVersion>(`${this.base}/antragstyp/${id}/versions`, body);
  }

  editDraft(versionId: string, formDefinition: FormDefinition,
            flowDefinition: FlowDefinition | null): Observable<AntragsTypVersion> {
    const body: Record<string, unknown> = { formDefinition, workflowBpmn: '<bpmn/>', sfActionBindings: {} };
    if (flowDefinition) {
      body['flowDefinition'] = flowDefinition;
    }
    return this.http.put<AntragsTypVersion>(`${this.base}/antragstyp/versions/${versionId}/draft`, body);
  }

  publish(versionId: string): Observable<AntragsTypVersion> {
    return this.http.post<AntragsTypVersion>(`${this.base}/antragstyp/versions/${versionId}/publish`, {});
  }

  listActionRefs(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/action/refs`);
  }
```

> Note: the existing caller `form-designer.component.ts` calls `createDraftVersion(id, formDefinition)` (2 args). Task 7 updates that caller. To keep this task's build green in isolation, Task 7 follows immediately; if running this task standalone and the build breaks on that caller, update the call to pass `null` as the third arg (it will be properly wired in Task 7).

- [ ] **Step 4: Run the spec to verify it passes**

Run: `docker exec hrsuite-fe-dev npx ng test --watch=false`
Expected: the 6 `AntragsTypService` specs pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/antragstyp/antragstyp.service.ts \
        frontend/src/app/features/antragstyp/antragstyp.service.spec.ts
git commit -m "$(cat <<'EOF'
feat(flow-ui): antragstyp service — createAntragstyp, editDraft, publish, listActionRefs, flow in version

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Frontend — "Neuer Antragstyp" create page + route + spec

**Files:**
- Create: `frontend/src/app/features/antragstyp/antragstyp-create.component.ts`, `.html`, `.scss`
- Create: `frontend/src/app/features/antragstyp/antragstyp-create.component.spec.ts`
- Modify: `frontend/src/app/app.routes.ts`

- [ ] **Step 1: Write the failing component spec**

`frontend/src/app/features/antragstyp/antragstyp-create.component.spec.ts`:
```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { AntragstypCreateComponent } from './antragstyp-create.component';
import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';

const stubConfig = { get: () => ({ apiBaseUrl: '/api/v1' }) } as Partial<RuntimeConfigService>;

describe('AntragstypCreateComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AntragstypCreateComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: RuntimeConfigService, useValue: stubConfig },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  it('is invalid with an empty key and does not POST', () => {
    const fixture = TestBed.createComponent(AntragstypCreateComponent);
    const cmp = fixture.componentInstance;
    cmp.submit();
    http.expectNone('/api/v1/antragstyp');
    expect(cmp.form.invalid).toBe(true);
  });

  it('POSTs and navigates to the builder on success', () => {
    const fixture = TestBed.createComponent(AntragstypCreateComponent);
    const cmp = fixture.componentInstance;
    const router = TestBed.inject(Router);
    const nav = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    cmp.form.controls.key.setValue('urlaubsantrag');
    cmp.titleControl('de').setValue('Urlaub');
    cmp.submit();

    const req = http.expectOne('/api/v1/antragstyp');
    expect(req.request.body).toEqual({ key: 'urlaubsantrag', title: { de: 'Urlaub' } });
    req.flush({ id: 'at-new' });

    expect(nav).toHaveBeenCalledWith(['/antragstypen', 'at-new', 'designer']);
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `docker exec hrsuite-fe-dev npx ng test --watch=false`
Expected: FAIL — component does not exist.

- [ ] **Step 3: Implement the component**

`frontend/src/app/features/antragstyp/antragstyp-create.component.ts`:
```ts
import { Component, inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { AntragsTypService } from './antragstyp.service';
import { LANGS, Lang, LocaleMap } from '../form-designer/form-definition.model';

@Component({
  selector: 'app-antragstyp-create',
  standalone: true,
  imports: [ReactiveFormsModule, TranslateModule, RouterLink],
  templateUrl: './antragstyp-create.component.html',
  styleUrl: './antragstyp-create.component.scss',
})
export class AntragstypCreateComponent {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(AntragsTypService);
  private readonly router = inject(Router);

  readonly langs = LANGS;
  saving = false;
  errorMsg = '';

  readonly form: FormGroup = this.fb.group({
    key: ['', [Validators.required, Validators.pattern(/^[a-z0-9_-]+$/)]],
    title: this.fb.group(Object.fromEntries(LANGS.map(l => [l, ['']]))),
  });

  titleControl(lang: Lang) {
    return (this.form.get('title') as FormGroup).get(lang)!;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    this.errorMsg = '';
    const key = this.form.controls['key'].value as string;
    const rawTitle = this.form.controls['title'].value as Record<string, string>;
    const title: LocaleMap = Object.fromEntries(
      Object.entries(rawTitle).filter(([, v]) => v && v.trim().length > 0));

    this.service.createAntragstyp(key, title).subscribe({
      next: (created) => this.router.navigate(['/antragstypen', created.id, 'designer']),
      error: (e) => { this.saving = false; this.errorMsg = e?.error?.message ?? 'Fehler'; },
    });
  }
}
```

`frontend/src/app/features/antragstyp/antragstyp-create.component.html`:
```html
<section class="create">
  <a routerLink="/antragstypen">&larr; {{ 'antragstyp.list.title' | translate }}</a>
  <h1>{{ 'antragstyp.create.title' | translate }}</h1>

  @if (errorMsg) { <p class="error" role="alert">{{ errorMsg }}</p> }

  <form [formGroup]="form" (ngSubmit)="submit()">
    <label>
      {{ 'antragstyp.create.key' | translate }}
      <input formControlName="key" autocomplete="off" />
    </label>
    @if (form.controls['key'].touched && form.controls['key'].invalid) {
      <p class="hint">{{ 'antragstyp.create.keyHint' | translate }}</p>
    }

    <fieldset formGroupName="title">
      <legend>{{ 'antragstyp.create.title' | translate }}</legend>
      @for (lang of langs; track lang) {
        <label>{{ lang | uppercase }}<input [formControlName]="lang" /></label>
      }
    </fieldset>

    <button type="submit" [disabled]="saving">{{ 'antragstyp.create.submit' | translate }}</button>
  </form>
</section>
```
(Add `import { UpperCasePipe } from '@angular/common';` to the component `imports` array and include `UpperCasePipe`.)

`frontend/src/app/features/antragstyp/antragstyp-create.component.scss`:
```scss
.create { max-width: 40rem; margin: 1rem auto; display: flex; flex-direction: column; gap: 1rem; }
.create label { display: block; margin: .25rem 0; }
.create input { width: 100%; padding: .4rem; }
.error { color: #b00020; }
.hint { color: #8a6d00; font-size: .85rem; }
```

- [ ] **Step 4: Add the route**

In `frontend/src/app/app.routes.ts`, add **before** the `antragstypen/:id/designer` route:
```ts
  {
    path: 'antragstypen/neu',
    loadComponent: () =>
      import('./features/antragstyp/antragstyp-create.component').then(m => m.AntragstypCreateComponent),
  },
```

- [ ] **Step 5: Run the spec to verify it passes**

Run: `docker exec hrsuite-fe-dev npx ng test --watch=false`
Expected: the 2 create-component specs pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/antragstyp/antragstyp-create.component.ts \
        frontend/src/app/features/antragstyp/antragstyp-create.component.html \
        frontend/src/app/features/antragstyp/antragstyp-create.component.scss \
        frontend/src/app/features/antragstyp/antragstyp-create.component.spec.ts \
        frontend/src/app/app.routes.ts
git commit -m "$(cat <<'EOF'
feat(flow-ui): /antragstypen/neu — create Antragstyp page

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Frontend — flow-step-editor component + spec (TDD)

A self-contained child component: owns a `FormArray` of step groups, exposes add/remove/move, validates keys, and converts to/from `FlowDefinition`. Takes the available action refs as an input.

**Files:**
- Create: `frontend/src/app/features/form-designer/flow-step-editor.component.ts`, `.html`, `.scss`
- Test: `frontend/src/app/features/form-designer/flow-step-editor.component.spec.ts`

- [ ] **Step 1: Write the failing spec**

`frontend/src/app/features/form-designer/flow-step-editor.component.spec.ts`:
```ts
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { FlowStepEditorComponent } from './flow-step-editor.component';
import { FlowDefinition } from './flow-definition.model';

describe('FlowStepEditorComponent', () => {
  let cmp: FlowStepEditorComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FlowStepEditorComponent, TranslateModule.forRoot()],
    }).compileComponents();
    const fixture = TestBed.createComponent(FlowStepEditorComponent);
    cmp = fixture.componentInstance;
    fixture.componentRef.setInput('availableRefs', ['provision-ad-account']);
    fixture.detectChanges();
  });

  it('starts empty and addStep appends a typed step', () => {
    expect(cmp.steps.length).toBe(0);
    cmp.addStep('FORM');
    cmp.addStep('ACTION');
    expect(cmp.steps.length).toBe(2);
    expect(cmp.steps.at(0).controls.kind.value).toBe('FORM');
    expect(cmp.steps.at(1).controls.kind.value).toBe('ACTION');
  });

  it('flags an invalid (hyphenated) step key', () => {
    cmp.addStep('FORM');
    cmp.steps.at(0).controls.key.setValue('bad-key');
    expect(cmp.steps.at(0).controls.key.invalid).toBe(true);
    cmp.steps.at(0).controls.key.setValue('good_key');
    expect(cmp.steps.at(0).controls.key.valid).toBe(true);
  });

  it('moveUp swaps order', () => {
    cmp.addStep('FORM'); cmp.steps.at(0).controls.key.setValue('first');
    cmp.addStep('ACTION'); cmp.steps.at(1).controls.key.setValue('second');
    cmp.moveUp(1);
    expect(cmp.steps.at(0).controls.key.value).toBe('second');
  });

  it('toFlowDefinition emits null when empty, omits empty title locales, keeps ACTION ref/inputMapping', () => {
    expect(cmp.toFlowDefinition()).toBeNull();

    cmp.addStep('ACTION');
    const g = cmp.steps.at(0);
    g.controls.key.setValue('provision');
    (g.controls.title as any).controls.de.setValue('Konto');
    g.controls.ref!.setValue('provision-ad-account');
    cmp.addInputMappingRow(0);
    const rows = cmp.inputMapping(0);
    rows.at(0).controls.k.setValue('upn');
    rows.at(0).controls.v.setValue('a@b.ch');

    const def = cmp.toFlowDefinition() as FlowDefinition;
    expect(def.steps).toHaveLength(1);
    expect(def.steps[0]).toEqual({
      kind: 'ACTION', key: 'provision', title: { de: 'Konto' },
      ref: 'provision-ad-account', inputMapping: { upn: 'a@b.ch' },
    });
  });

  it('loadFlow rehydrates an APPROVAL step', () => {
    cmp.loadFlow({ steps: [
      { kind: 'APPROVAL', key: 'review', title: { de: 'Freigabe' }, assigneeRole: 'hr-reviewer', outcomes: ['approve','reject'] },
    ]});
    expect(cmp.steps.length).toBe(1);
    expect(cmp.steps.at(0).controls.kind.value).toBe('APPROVAL');
    expect(cmp.steps.at(0).controls.assigneeRole!.value).toBe('hr-reviewer');
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `docker exec hrsuite-fe-dev npx ng test --watch=false`
Expected: FAIL — component does not exist.

- [ ] **Step 3: Implement the component**

`frontend/src/app/features/form-designer/flow-step-editor.component.ts`:
```ts
import { Component, inject, input } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { UpperCasePipe } from '@angular/common';

import { LANGS, Lang, LocaleMap } from './form-definition.model';
import {
  ASSIGNEE_ROLES, ActionStepDef, ApprovalStepDef, FlowDefinition, FlowStepDef,
  FormStepDef, STEP_KEY_PATTERN, StepKind,
} from './flow-definition.model';

@Component({
  selector: 'app-flow-step-editor',
  standalone: true,
  imports: [ReactiveFormsModule, TranslateModule, UpperCasePipe],
  templateUrl: './flow-step-editor.component.html',
  styleUrl: './flow-step-editor.component.scss',
})
export class FlowStepEditorComponent {
  private readonly fb = inject(FormBuilder);

  /** Action refs allow-listed for the tenant (from GET /action/refs). */
  readonly availableRefs = input<string[]>([]);
  readonly langs = LANGS;
  readonly assigneeRoles = ASSIGNEE_ROLES;

  readonly steps = this.fb.array<FormGroup>([]);

  // ---- structure --------------------------------------------------------
  addStep(kind: StepKind): void {
    this.steps.push(this.buildStep(kind));
  }

  removeStep(i: number): void {
    this.steps.removeAt(i);
  }

  moveUp(i: number): void {
    if (i <= 0) return;
    const g = this.steps.at(i);
    this.steps.removeAt(i);
    this.steps.insert(i - 1, g);
  }

  moveDown(i: number): void {
    if (i >= this.steps.length - 1) return;
    const g = this.steps.at(i);
    this.steps.removeAt(i);
    this.steps.insert(i + 1, g);
  }

  // ---- ACTION inputMapping rows ----------------------------------------
  inputMapping(i: number): FormArray<FormGroup> {
    return this.steps.at(i).get('inputMapping') as FormArray<FormGroup>;
  }

  addInputMappingRow(i: number): void {
    this.inputMapping(i).push(this.fb.group({ k: this.fb.control(''), v: this.fb.control('') }));
  }

  removeInputMappingRow(i: number, r: number): void {
    this.inputMapping(i).removeAt(r);
  }

  // ---- accessors used by the template ----------------------------------
  titleGroup(g: FormGroup): FormGroup { return g.get('title') as FormGroup; }
  kindOf(g: FormGroup): StepKind { return g.controls['kind'].value as StepKind; }

  // ---- (de)serialisation ------------------------------------------------
  /** Returns null when there are no steps (form-only antragstyp -> omit flowDefinition). */
  toFlowDefinition(): FlowDefinition | null {
    if (this.steps.length === 0) return null;
    const steps: FlowStepDef[] = this.steps.controls.map((g) => this.toStep(g as FormGroup));
    return { steps };
  }

  loadFlow(flow: FlowDefinition | null | undefined): void {
    this.steps.clear();
    for (const s of flow?.steps ?? []) {
      if (s.kind === 'FORM' || s.kind === 'APPROVAL' || s.kind === 'ACTION') {
        this.steps.push(this.buildStep(s.kind, s));
      }
      // unknown kinds (e.g. BRANCH) are dropped — not editable in Cut C
    }
  }

  /** True if every step key matches the pattern and is unique. */
  isValid(): boolean {
    const keys = this.steps.controls.map(g => (g as FormGroup).controls['key'].value);
    const unique = new Set(keys).size === keys.length;
    return unique && this.steps.controls.every(g => (g as FormGroup).controls['key'].valid
      && (this.kindOf(g as FormGroup) !== 'ACTION' || !!(g as FormGroup).controls['ref']!.value));
  }

  // ---- internals --------------------------------------------------------
  private buildStep(kind: StepKind, existing?: FlowStepDef): FormGroup {
    const group: Record<string, unknown> = {
      kind: this.fb.control(kind),
      key: this.fb.control(existing?.key ?? '',
        [Validators.required, Validators.pattern(STEP_KEY_PATTERN)]),
      title: this.fb.group(Object.fromEntries(
        LANGS.map(l => [l, this.fb.control((existing?.title as LocaleMap | undefined)?.[l] ?? '')]))),
    };
    if (kind === 'APPROVAL') {
      const a = existing as ApprovalStepDef | undefined;
      group['assigneeRole'] = this.fb.control(a?.assigneeRole ?? 'hr-reviewer', Validators.required);
    }
    if (kind === 'ACTION') {
      const a = existing as ActionStepDef | undefined;
      group['ref'] = this.fb.control(a?.ref ?? '', Validators.required);
      const rows = (a?.inputMapping ? Object.entries(a.inputMapping) : [])
        .map(([k, v]) => this.fb.group({ k: this.fb.control(k), v: this.fb.control(v) }));
      group['inputMapping'] = this.fb.array(rows);
    }
    return this.fb.group(group);
  }

  private toStep(g: FormGroup): FlowStepDef {
    const kind = this.kindOf(g);
    const key = g.controls['key'].value as string;
    const title = this.compactTitle(g.get('title') as FormGroup);
    if (kind === 'FORM') {
      return { kind, key, title } as FormStepDef;
    }
    if (kind === 'APPROVAL') {
      return { kind, key, title, assigneeRole: g.controls['assigneeRole'].value as string,
               outcomes: ['approve', 'reject'] } as ApprovalStepDef;
    }
    const inputMapping: Record<string, string> = {};
    for (const row of (g.get('inputMapping') as FormArray<FormGroup>).controls) {
      const k = (row.controls['k'] as FormControl).value as string;
      const v = (row.controls['v'] as FormControl).value as string;
      if (k && k.trim()) inputMapping[k] = v ?? '';
    }
    return { kind, key, title, ref: g.controls['ref'].value as string, inputMapping } as ActionStepDef;
  }

  private compactTitle(group: FormGroup): LocaleMap {
    const out: LocaleMap = {};
    for (const l of LANGS) {
      const v = (group.get(l) as FormControl).value as string;
      if (v && v.trim()) out[l as Lang] = v;
    }
    return out;
  }
}
```

`frontend/src/app/features/form-designer/flow-step-editor.component.html`:
```html
<div class="flow-editor">
  <div class="toolbar">
    <span>{{ 'flow.editor.steps' | translate }}</span>
    <button type="button" (click)="addStep('FORM')">+ FORM</button>
    <button type="button" (click)="addStep('APPROVAL')">+ APPROVAL</button>
    <button type="button" (click)="addStep('ACTION')" [disabled]="availableRefs().length === 0">+ ACTION</button>
  </div>
  @if (availableRefs().length === 0) {
    <p class="hint">{{ 'flow.editor.noRefs' | translate }}</p>
  }

  @for (g of steps.controls; track g; let i = $index) {
    <div class="step" [formGroup]="$any(g)">
      <header>
        <span class="badge">{{ i + 1 }} · {{ kindOf($any(g)) }}</span>
        <span class="spacer"></span>
        <button type="button" (click)="moveUp(i)" [disabled]="i === 0">▲</button>
        <button type="button" (click)="moveDown(i)" [disabled]="i === steps.length - 1">▼</button>
        <button type="button" (click)="removeStep(i)" aria-label="delete">🗑</button>
      </header>

      <label>{{ 'flow.editor.key' | translate }}<input formControlName="key" /></label>
      @if ($any(g).controls.key.touched && $any(g).controls.key.invalid) {
        <p class="hint">{{ 'flow.editor.keyHint' | translate }}</p>
      }

      <fieldset formGroupName="title">
        <legend>{{ 'flow.editor.title' | translate }}</legend>
        @for (lang of langs; track lang) {
          <label>{{ lang | uppercase }}<input [formControlName]="lang" /></label>
        }
      </fieldset>

      @if (kindOf($any(g)) === 'APPROVAL') {
        <label>{{ 'flow.editor.assigneeRole' | translate }}
          <select formControlName="assigneeRole">
            @for (r of assigneeRoles; track r) { <option [value]="r">{{ r }}</option> }
          </select>
        </label>
        <p class="hint">{{ 'flow.editor.outcomesFixed' | translate }}</p>
      }

      @if (kindOf($any(g)) === 'ACTION') {
        <label>{{ 'flow.editor.ref' | translate }}
          <select formControlName="ref">
            <option value="" disabled>—</option>
            @for (r of availableRefs(); track r) { <option [value]="r">{{ r }}</option> }
          </select>
        </label>
        <div class="mapping">
          <span>{{ 'flow.editor.inputMapping' | translate }}</span>
          @for (row of inputMapping(i).controls; track row; let r = $index) {
            <div class="row" [formGroup]="$any(row)">
              <input formControlName="k" placeholder="key" />
              <input formControlName="v" placeholder="value" />
              <button type="button" (click)="removeInputMappingRow(i, r)">✕</button>
            </div>
          }
          <button type="button" (click)="addInputMappingRow(i)">+ {{ 'flow.editor.addRow' | translate }}</button>
        </div>
      }
    </div>
  }
</div>
```

`frontend/src/app/features/form-designer/flow-step-editor.component.scss`:
```scss
.flow-editor { display: flex; flex-direction: column; gap: 1rem; }
.toolbar { display: flex; gap: .5rem; align-items: center; }
.step { border: 1px solid #ccc; border-radius: 6px; padding: .75rem; display: flex; flex-direction: column; gap: .5rem; }
.step header { display: flex; align-items: center; gap: .25rem; }
.step .spacer { flex: 1; }
.badge { font-weight: 600; }
.mapping .row { display: flex; gap: .5rem; }
.hint { color: #8a6d00; font-size: .85rem; }
```

- [ ] **Step 4: Run the spec to verify it passes**

Run: `docker exec hrsuite-fe-dev npx ng test --watch=false`
Expected: the 5 flow-step-editor specs pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/form-designer/flow-step-editor.component.ts \
        frontend/src/app/features/form-designer/flow-step-editor.component.html \
        frontend/src/app/features/form-designer/flow-step-editor.component.scss \
        frontend/src/app/features/form-designer/flow-step-editor.component.spec.ts
git commit -m "$(cat <<'EOF'
feat(flow-ui): flow-step-editor component (FORM/APPROVAL/ACTION, reorder, inputMapping)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Frontend — wire the builder (sections + save + publish)

Turn the existing designer page into a sectioned Antragstyp-Builder: keep the Form section as-is, embed `<app-flow-step-editor>` in the Flow section, add a Publish section. Load the existing draft (or seed from latest), save form+flow, publish.

**Files:**
- Modify: `frontend/src/app/features/form-designer/form-designer.component.ts`
- Modify: `frontend/src/app/features/form-designer/form-designer.component.html`

- [ ] **Step 1: Read the current component**

Read `frontend/src/app/features/form-designer/form-designer.component.ts` and `.html` fully. Note how it loads `versions[0]`, builds the form `FormArray`, and the existing "save as draft" call (`createDraftVersion(id, formDefinition)`), and how it surfaces `errorMsg`/`savedMajor`.

- [ ] **Step 2: Extend the component (.ts)**

Apply these changes to `form-designer.component.ts`:

1. Imports — add:
```ts
import { ViewChild } from '@angular/core';
import { FlowStepEditorComponent } from './flow-step-editor.component';
import { AntragsTypVersion } from '../antragstyp/antragstyp-version.model';
```
Add `FlowStepEditorComponent` to the component `imports` array.

2. Fields — add:
```ts
  @ViewChild(FlowStepEditorComponent) flowEditor?: FlowStepEditorComponent;
  section: 'form' | 'flow' | 'publish' = 'form';
  availableRefs: string[] = [];
  draftVersionId: string | null = null;
  publishedKey: string | null = null;
  publishing = false;
```

3. In `ngOnInit`, extend the `forkJoin` to also fetch action refs, and capture the editable draft version + seed the flow editor. Replace the existing `forkJoin({...}).subscribe(...)` block with:
```ts
    forkJoin({
      antragsTyp: this.service.getById(this.antragstypId).pipe(catchError(() => of(undefined))),
      versions: this.service.listVersions(this.antragstypId).pipe(catchError(() => of([] as AntragsTypVersion[]))),
      refs: this.service.listActionRefs().pipe(catchError(() => of([] as string[]))),
    }).subscribe(({ antragsTyp, versions, refs }) => {
      this.antragsTyp = antragsTyp;
      this.antragsTypLabel = this.resolveLabel(antragsTyp);
      this.availableRefs = refs;

      const draft = versions.find(v => v.status === 'DRAFT');
      const source = draft ?? versions[0];
      this.draftVersionId = draft?.id ?? null;

      const defs = source?.formDefinition?.fields ?? [];
      for (const f of (defs.length ? defs : [null])) {
        this.fields.push(this.buildFieldGroup(f));
      }
      // seed flow editor once the view (and child) exist
      this.pendingFlow = source?.flowDefinition ?? null;
      this.loading = false;
      this.cdr.markForCheck();
    });
```
Add a field `private pendingFlow: import('./flow-definition.model').FlowDefinition | null = null;` and implement `ngAfterViewInit()`:
```ts
  ngAfterViewInit(): void {
    if (this.flowEditor && this.pendingFlow) {
      this.flowEditor.loadFlow(this.pendingFlow);
      this.cdr.markForCheck();
    }
  }
```
(Implement `AfterViewInit` on the class.)

4. Replace the existing save method to send form + flow via editDraft/createDraftVersion. Find the current save handler (it calls `this.service.createDraftVersion(this.antragstypId, formDefinition)`) and replace its body with:
```ts
    this.saving = true;
    this.errorMsg = '';
    const formDefinition = this.collectFormDefinition();        // existing helper that builds FormDefinition
    const flow = this.flowEditor?.toFlowDefinition() ?? null;

    const save$ = this.draftVersionId
      ? this.service.editDraft(this.draftVersionId, formDefinition, flow)
      : this.service.createDraftVersion(this.antragstypId, formDefinition, flow);

    save$.subscribe({
      next: (v) => { this.saving = false; this.savedMajor = v.major; this.draftVersionId = v.id; this.cdr.markForCheck(); },
      error: (e) => { this.saving = false; this.errorMsg = this.httpMessage(e); this.cdr.markForCheck(); },
    });
```
> Use the component's existing form-definition collector. If the current code inlines the `FormDefinition` assembly rather than a `collectFormDefinition()` helper, extract that assembly into a private `collectFormDefinition(): FormDefinition` method first (pure refactor, no behaviour change) and call it here.

5. Add publish + an error formatter:
```ts
  publish(): void {
    if (!this.draftVersionId) { this.errorMsg = 'Bitte zuerst als Entwurf speichern.'; return; }
    this.publishing = true;
    this.errorMsg = '';
    this.service.publish(this.draftVersionId).subscribe({
      next: (v) => { this.publishing = false; this.publishedKey = v.processDefinitionKey ?? null; this.cdr.markForCheck(); },
      error: (e) => { this.publishing = false; this.errorMsg = this.httpMessage(e); this.cdr.markForCheck(); },
    });
  }

  private httpMessage(e: any): string {
    if (e?.status === 422) return 'Inkompatible Änderung — eine neue Major-Version ist nötig.';
    if (e?.status === 409) return 'Gerade veröffentlicht — bitte erneut versuchen.';
    return e?.error?.message ?? 'Aktion fehlgeschlagen.';
  }
```

- [ ] **Step 3: Extend the template (.html)**

At the top of `form-designer.component.html`, add a section switcher and wrap the existing form markup in a `@if (section === 'form')` block (do not delete the existing form fields markup — wrap it). Then add the Flow and Publish sections:
```html
<nav class="builder-tabs">
  <button type="button" [class.active]="section === 'form'" (click)="section = 'form'">{{ 'builder.form' | translate }}</button>
  <button type="button" [class.active]="section === 'flow'" (click)="section = 'flow'">{{ 'builder.flow' | translate }}</button>
  <button type="button" [class.active]="section === 'publish'" (click)="section = 'publish'">{{ 'builder.publish' | translate }}</button>
</nav>

@if (section === 'form') {
  <!-- existing form-designer field markup goes here, unchanged -->
}

@if (section === 'flow') {
  <app-flow-step-editor [availableRefs]="availableRefs" />
}

@if (section === 'publish') {
  <section class="publish">
    <button type="button" (click)="save()" [disabled]="saving">{{ 'builder.saveDraft' | translate }}</button>
    <button type="button" (click)="publish()" [disabled]="publishing || !draftVersionId">{{ 'builder.publish' | translate }}</button>
    @if (savedMajor !== null) { <p class="ok">{{ 'builder.savedMajor' | translate }} {{ savedMajor }}</p> }
    @if (publishedKey) { <p class="ok">{{ 'builder.published' | translate }} {{ publishedKey }}</p> }
    @if (errorMsg) { <p class="error" role="alert">{{ errorMsg }}</p> }
  </section>
}
```
> Rename the existing save button's handler to `save()` if it differs, and ensure `save()` is the method modified in Step 2. Keep the existing per-section save affordance working.

- [ ] **Step 4: Build to verify it compiles**

Run: `docker exec hrsuite-fe-dev npx ng build`
Expected: build succeeds (this also confirms the Task 4 service-signature change is consumed correctly).

- [ ] **Step 5: Run the full FE test suite**

Run: `docker exec hrsuite-fe-dev npx ng test --watch=false`
Expected: all specs pass (service, create, flow-editor).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/form-designer/form-designer.component.ts \
        frontend/src/app/features/form-designer/form-designer.component.html
git commit -m "$(cat <<'EOF'
feat(flow-ui): sectioned Antragstyp-Builder — Form + Flow + Publish

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Frontend — "+ Neuer Antragstyp" entry on the list

**Files:**
- Modify: `frontend/src/app/features/antragstyp/antragstyp-list.component.html`

- [ ] **Step 1: Add the link**

Read `antragstyp-list.component.html`; near the page heading add:
```html
<a class="new-antragstyp" routerLink="/antragstypen/neu">+ {{ 'antragstyp.create.title' | translate }}</a>
```
Ensure `RouterLink` is imported in `antragstyp-list.component.ts` (`imports` array). If not present, add `import { RouterLink } from '@angular/router';` and include `RouterLink`.

- [ ] **Step 2: Build to verify**

Run: `docker exec hrsuite-fe-dev npx ng build`
Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/antragstyp/antragstyp-list.component.html \
        frontend/src/app/features/antragstyp/antragstyp-list.component.ts
git commit -m "$(cat <<'EOF'
feat(flow-ui): + Neuer Antragstyp link on the antragstyp list

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Frontend — i18n keys (de/fr/it/en)

**Files:**
- Modify: `frontend/src/assets/i18n/de.json`, `fr.json`, `it.json`, `en.json`

- [ ] **Step 1: Locate the i18n files and confirm structure**

Run: `ls frontend/src/assets/i18n/` and read `de.json` to confirm the JSON shape (nested objects). (If the path differs, find it: `find frontend/src -name 'de.json'`.)

- [ ] **Step 2: Add the keys to each locale**

Add these keys (translated per locale) under the existing root object in each file. German (`de.json`):
```json
{
  "antragstyp": {
    "create": {
      "title": "Neuer Antragstyp",
      "key": "Schlüssel",
      "keyHint": "Nur Kleinbuchstaben, Ziffern, _ und -",
      "submit": "Anlegen"
    }
  },
  "builder": {
    "form": "Formular",
    "flow": "Flow",
    "publish": "Veröffentlichen",
    "saveDraft": "Als Entwurf speichern",
    "savedMajor": "Entwurf gespeichert, Major",
    "published": "Veröffentlicht, Prozess-Key:"
  },
  "flow": {
    "editor": {
      "steps": "Flow-Schritte",
      "key": "Schlüssel",
      "keyHint": "Muss [A-Za-z][A-Za-z0-9_]* sein (keine Bindestriche)",
      "title": "Titel",
      "assigneeRole": "Zuständige Rolle",
      "outcomesFixed": "Outcomes: approve / reject (fest)",
      "ref": "Aktion (n8n-ref)",
      "inputMapping": "Input-Mapping",
      "addRow": "Zeile",
      "noRefs": "Keine n8n-Refs für diesen Tenant konfiguriert — ACTION-Schritte nicht verfügbar."
    }
  }
}
```
For `fr.json`, `it.json`, `en.json`: add the same key paths with the appropriate translations (FR/IT/EN). Merge into existing root objects — do not overwrite existing keys; if `antragstyp` already exists, add the `create` sub-object into it.

> English values (`en.json`) for reference: create.title "New Antragstyp", key "Key", submit "Create"; builder.form "Form", flow "Flow", publish "Publish", saveDraft "Save draft"; flow.editor.steps "Flow steps", ref "Action (n8n ref)", noRefs "No n8n refs configured for this tenant — ACTION steps unavailable." Provide natural FR/IT equivalents.

- [ ] **Step 3: Build to verify JSON is valid**

Run: `docker exec hrsuite-fe-dev npx ng build`
Expected: build succeeds (invalid JSON would fail the build).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/assets/i18n/de.json frontend/src/assets/i18n/fr.json \
        frontend/src/assets/i18n/it.json frontend/src/assets/i18n/en.json
git commit -m "$(cat <<'EOF'
feat(flow-ui): i18n keys for builder + flow editor + create (de/fr/it/en)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Full verify gate + browser smoke

**Files:** none new — gate.

- [ ] **Step 1: Backend full verify**

Run:
```bash
docker run --rm -v "$PWD":/work -w /work -v hrsuite-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true \
  maven:3.9-eclipse-temurin-21 mvn -ntp -pl application -am verify
```
Expected: BUILD SUCCESS — all unit + ITs green incl. `ActionRefsControllerTest`, `ActionRefsIT`, and the pre-existing suite; `ModularityTests` green.

- [ ] **Step 2: Frontend test + build**

Run:
```bash
docker exec hrsuite-fe-dev npx ng test --watch=false
docker exec hrsuite-fe-dev npx ng build
```
Expected: all specs pass; production build succeeds.

- [ ] **Step 3: Rebuild + restart the running stack**

Run (from repo root):
```bash
docker compose build backend app && docker compose up -d backend app
```
Wait for `backend` healthy: `docker inspect -f '{{.State.Health.Status}}' hrsuite-backend`.

- [ ] **Step 4: Browser smoke (uses superpowers:verification-before-completion mindset)**

In the browser (FE dev `http://localhost:4200` or prod `http://localhost:8080`), as an `hr-designer` dev token tenant that has an n8n config seeded (`bash scripts/dev-seed.sh` seeds the fixed tenant `019e754d-…`):
1. `/antragstypen` → click **+ Neuer Antragstyp** → create one (key `smoke-ui`, title de).
2. In the builder: add a **Form** field; in **Flow** add `FORM` (key `erfassen`) and `ACTION` (key `provision`, ref from dropdown).
3. **Veröffentlichen** → Save draft → Publish → confirm a `processDefinitionKey` is shown.
4. Verify the DB: `docker exec -e PGPASSWORD=dev hrsuite-postgres psql -U hrsuite -d hrsuite -c "select status, process_definition_key from antragstyp_version order by created_at desc limit 1;"` shows `PUBLISHED` + a key.

Document the result (screenshot/notes). If anything fails, fix before completing.

- [ ] **Step 5: Commit any fixups**

```bash
git add -- application/ frontend/        # explicit dirs; never the forbidden config/ files
git commit -m "$(cat <<'EOF'
test(flow-ui): full verify green for Cut C (flow editor + action/refs)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review (completed by plan author)

**Spec coverage:**
- `GET /action/refs` endpoint (tenant allowed_refs, hr-designer, RLS) ✓ Task 1 + Task 2
- Frontend FlowDefinition model + version model fields ✓ Task 3
- Service: createAntragstyp, createDraftVersion(+flow), editDraft, publish, listActionRefs ✓ Task 4
- `/antragstypen/neu` create page (dedicated page) ✓ Task 5
- Flow step editor: FORM/APPROVAL/ACTION, fixed approve/reject, ref dropdown, inputMapping, reorder, key validation, BRANCH excluded, empty→omit flowDefinition ✓ Task 6
- Combined builder (Form + Flow + Publish), load draft/seed, save via editDraft/createDraftVersion, publish, error mapping (422/409) ✓ Task 7
- "+ Neuer Antragstyp" entry ✓ Task 8
- i18n de/fr/it/en ✓ Task 9
- Tests (backend unit+IT, FE component+service) + browser smoke ✓ Tasks 1,2,4,5,6,10
- Zoneless: new components use signals input() (flow editor) ✓ Task 6; existing designer keeps markForCheck ✓ Task 7

**Placeholder scan:** none — every code step contains complete code. The only "read the existing file" steps (Task 2 Step 1, Task 7 Step 1) are inspection prerequisites, with the concrete code following.

**Type consistency:** `toFlowDefinition(): FlowDefinition | null`, `loadFlow(FlowDefinition|null)`, `availableRefs = input<string[]>([])`, service `createDraftVersion(id, FormDefinition, FlowDefinition|null)` / `editDraft(...)` / `publish(): AntragsTypVersion` / `listActionRefs(): string[]` are used consistently across Tasks 3–7. `AntragsTypVersion.flowDefinition?`/`processDefinitionKey?` (Task 3) are read in Task 7. Backend `ActionRefsController(TenantN8nConfigRepository)` matches the repo's `findById` + `TenantN8nConfig.getAllowedRefs()` confirmed in the codebase.

**Known follow-ups (out of scope, per spec):** reviewer/runtime task-completion (Cut D), BRANCH editor + compileBranch, editable outcomes + validation, drag&drop reorder.
