# Antrags-Katalog mit Kategorien — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Antragsteller wählen einen neuen Antrag über einen Kachel-Katalog (`/antraege/neu`), gefiltert nach einer festen Kategorie-Liste; HR-Designer pflegen die Kategorie pro Antragstyp.

**Architecture:** Backend bekommt ein festes Enum `AntragsKategorie` + nullable Spalte `antragstyp.category` (Migration 017), `category` in Create/Response, und `PUT /antragstyp/{id}/category` (hr-designer). FE: neue `AntragKatalogComponent` nutzt das bestehende `GET /antragstyp` (Antragsteller bekommt nur LIVE), Kachel-Klick öffnet das bestehende Inline-Formular auf `/antraege` via `?neu=<id>`. Spec: `Entscheidungen/specs/ADR-021-spec-antrags-katalog.md` (Vault).

**Tech Stack:** Spring Boot 3.4 Modulith, PostgreSQL 16 + RLS, Liquibase; Angular 21 zoneless + ngx-translate (de/fr/it/en). Dockerisiertes Maven + `hrsuite-fe-dev`-Container für FE-Tests.

**Branch:** `feat/antrags-katalog` (von aktuellem `main`).

**Build-/Test-Befehle (Referenz):**
- Backend Surefire: `docker run --rm -v "$HOME/Desktop/Git Repos/HR-Suite-code":/work -w /work -v hrsuite-m2:/root/.m2 maven:3.9-eclipse-temurin-21 mvn -ntp -pl application -am test -Dtest='<Pattern>' -Dsurefire.failIfNoSpecifiedTests=false`
- Backend Verify (Testcontainers): wie oben, zusätzlich `-v /var/run/docker.sock:/var/run/docker.sock --add-host=host.docker.internal:host-gateway -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true` und Ziel `verify`.
- FE-Tests: `docker exec hrsuite-fe-dev sh -c 'cd /app && npx ng test --watch=false' >/tmp/fe.log 2>&1; echo EXIT=$?` — danach `grep -iE "unhandled|Errors |error TS|✗" /tmp/fe.log` (Exit-Code zählt, nicht nur „X passed").
- FE-Build: `docker exec hrsuite-fe-dev sh -c 'cd /app && npx ng build'`
- i18n: `bash scripts/check_i18n_coverage.sh`

---

## File Structure

**Backend (neu):**
- `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsKategorie.java` — Enum.
- `application/src/main/resources/db/changelog/changes/017-add-antragstyp-category.sql` — Migration.
- `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/dto/SetCategoryRequest.java` — PUT-Body.

**Backend (ändern):**
- `…/antragstyp/AntragsTyp.java` — Feld `category` + `recategorize(...)`.
- `…/antragstyp/dto/CreateAntragsTypRequest.java` — `category` (optional).
- `…/antragstyp/dto/AntragsTypResponse.java` — `category` im Read-Model.
- `…/antragstyp/AntragsTypService.java` — `createDefinition(...,category)` + `setCategory(id,category)`.
- `…/antragstyp/AntragsTypController.java` — `create(...)` reicht category durch + neuer `PUT /{id}/category`.
- `application/src/main/resources/db/changelog/db.changelog-master.yaml` — include 017.

**Frontend (neu):**
- `frontend/src/app/features/antragstyp/kategorie.model.ts` — Kategorie-Keys (spiegelt Enum).
- `frontend/src/app/features/antrag/antrag-katalog.component.ts` / `.html` / `.scss` / `.spec.ts` — Katalog-Seite.

**Frontend (ändern):**
- `frontend/src/app/app.routes.ts` — Route `antraege/neu`.
- `frontend/src/app/features/antragstyp/antragstyp.model.ts` — `category` im Summary.
- `frontend/src/app/features/antragstyp/antragstyp.service.ts` — `setCategory(...)`.
- `frontend/src/app/features/antrag/antrag-list.component.ts` — `?neu=`-Aufgriff; „Neuer Antrag" → `/antraege/neu`.
- `frontend/src/app/features/antragstyp/antragstyp-create.component.ts/.html` — Kategorie-Dropdown.
- `frontend/src/app/features/antragstyp/antragstyp-list.component.ts/.html` — Recategorize-Select.
- `frontend/src/app/features/home/module-catalog.ts` — Eintrag „Neuen Antrag stellen".
- `frontend/src/assets/i18n/{de,fr,it,en}.json` — Labels.
- `scripts/seed-prototyp-antragstypen.sh` — Prototyp-Kategorien.

---

## Task 1: Branch + Kategorie-Enum + Migration 017 + Entity-Feld

**Files:**
- Create: `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsKategorie.java`
- Create: `application/src/main/resources/db/changelog/changes/017-add-antragstyp-category.sql`
- Modify: `application/src/main/resources/db/changelog/db.changelog-master.yaml`
- Modify: `application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTyp.java`

- [ ] **Step 1: Branch anlegen**

```bash
cd "$HOME/Desktop/Git Repos/HR-Suite-code" && git checkout main && git pull --ff-only && git checkout -b feat/antrags-katalog
```

- [ ] **Step 2: Enum schreiben**

`AntragsKategorie.java`:

```java
package io.github.manormachine2207.hrsuite.antragstyp;

/**
 * Festes, OSS-generisches Kategorie-Vokabular für Antragstypen (ADR-021). Schlüssel
 * sprachneutral; die i18n-Labels leben im Frontend (ngx-translate, Tenet 6). Keine
 * mandantenspezifischen Kategorien (YAGNI) — bei Bedarf späterer eigener Cut.
 */
public enum AntragsKategorie {
    ABSENCE,
    FINANCE,
    EMPLOYMENT,
    DEVELOPMENT,
    OTHER
}
```

- [ ] **Step 3: Migration 017 schreiben**

`017-add-antragstyp-category.sql`:

```sql
--liquibase formatted sql

--changeset hr-suite:017-add-antragstyp-category
--comment: ADR-021 — feste Kategorie am Antragstyp (Service-Portal-Katalog). Nullable;
-- bestehende Typen bleiben leer und erscheinen im Katalog unter OTHER/"Sonstiges".
-- Werte sind das AntragsKategorie-Enum (ABSENCE/FINANCE/EMPLOYMENT/DEVELOPMENT/OTHER);
-- die Validierung erfolgt in der App (Jackson-Enum-Mapping), nicht als DB-CHECK, damit
-- neue Kategorien später ohne Migration ergänzt werden können.
ALTER TABLE antragstyp ADD COLUMN category varchar(32);
--rollback ALTER TABLE antragstyp DROP COLUMN category;
```

- [ ] **Step 4: Migration registrieren** — in `db.changelog-master.yaml` nach dem `016-…`-Block anhängen:

```yaml
  - include:
      file: db/changelog/changes/017-add-antragstyp-category.sql
      relativeToChangelogFile: false
```

- [ ] **Step 5: Entity-Feld + recategorize()** — in `AntragsTyp.java`:

Nach dem `currentVersionId`-Feld (Zeile ~54) einfügen:

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 32)
    private AntragsKategorie category;
```

Nach `setStatus(...)` (Zeile ~101) Methode + Getter einfügen:

```java
    /** Sets/changes the catalog category (ADR-021). Null = uncategorized (shown as OTHER). */
    public void recategorize(AntragsKategorie category) { this.category = category; }

    public AntragsKategorie getCategory() { return category; }
```

- [ ] **Step 6: Kompiliert?**

Run: `docker run --rm -v "$HOME/Desktop/Git Repos/HR-Suite-code":/work -w /work -v hrsuite-m2:/root/.m2 maven:3.9-eclipse-temurin-21 mvn -ntp -pl application -am test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsKategorie.java \
  application/src/main/resources/db/changelog/changes/017-add-antragstyp-category.sql \
  application/src/main/resources/db/changelog/db.changelog-master.yaml \
  application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTyp.java
git commit -m "feat(antragstyp): Kategorie-Enum + category-Spalte (ADR-021, Migration 017)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: category in Create + Response + Service (TDD)

**Files:**
- Modify: `…/antragstyp/dto/CreateAntragsTypRequest.java`
- Modify: `…/antragstyp/dto/AntragsTypResponse.java`
- Modify: `…/antragstyp/AntragsTypService.java`
- Modify: `…/antragstyp/AntragsTypController.java` (create reicht category durch)
- Test: `application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTypServiceTest.java` (existierende Klasse erweitern; falls nicht vorhanden, neu mit `@ExtendWith(MockitoExtension.class)` + gemocktem `AntragsTypRepository` analog zu bestehenden Service-Tests)

- [ ] **Step 1: Failing test — createDefinition speichert category + setCategory ändert sie**

In `AntragsTypServiceTest.java` ergänzen (Repository-Mock gibt `save` als Identity zurück; `findById` liefert den Typ):

```java
@Test
void createDefinitionPersistsCategory() {
    when(repository.existsByKey("k")).thenReturn(false);
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    AntragsTyp created = service.createDefinition("k", Map.of("de", "T"), null, AntragsKategorie.ABSENCE);

    assertThat(created.getCategory()).isEqualTo(AntragsKategorie.ABSENCE);
}

@Test
void setCategoryUpdatesExistingType() {
    AntragsTyp t = new AntragsTyp(UUID.randomUUID(), TENANT, "k", Map.of("de", "T"), null);
    when(repository.findById(t.getId())).thenReturn(Optional.of(t));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    AntragsTyp updated = service.setCategory(t.getId(), AntragsKategorie.FINANCE);

    assertThat(updated.getCategory()).isEqualTo(AntragsKategorie.FINANCE);
}
```

(`TENANT` analog zu bestehenden Tests; ggf. `private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");` und TenantContext setzen, falls der Service `currentTenant()` nutzt — bestehende Service-Tests als Vorlage.)

- [ ] **Step 2: Run — fails to compile (Methoden fehlen)**

Run: `… mvn -ntp -pl application -am test -Dtest='AntragsTypServiceTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation failure / FAIL.

- [ ] **Step 3: DTOs erweitern**

`CreateAntragsTypRequest.java` — Feld ergänzen (optional, validiert via Jackson-Enum):

```java
public record CreateAntragsTypRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[a-z0-9_-]+$") String key,
        @NotEmpty Map<String, String> title,
        Map<String, String> description,
        io.github.manormachine2207.hrsuite.antragstyp.AntragsKategorie category
) {
}
```

`AntragsTypResponse.java` — `category` ins Record + `from(...)`:

```java
public record AntragsTypResponse(
        UUID id,
        String key,
        Map<String, String> title,
        Map<String, String> description,
        AntragsTypStatus status,
        io.github.manormachine2207.hrsuite.antragstyp.AntragsKategorie category,
        UUID currentVersionId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AntragsTypResponse from(AntragsTyp a) {
        return new AntragsTypResponse(
                a.getId(), a.getKey(), a.getTitle(), a.getDescription(),
                a.getStatus(), a.getCategory(), a.getCurrentVersionId(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
}
```

- [ ] **Step 4: Service erweitern**

In `AntragsTypService.java` `createDefinition` um den category-Parameter erweitern und nach dem `save` setzen — bzw. das Feld vor dem Speichern setzen:

```java
public AntragsTyp createDefinition(String key, Map<String, String> title,
                                   Map<String, String> description, AntragsKategorie category) {
    if (antragsTypRepository.existsByKey(key)) {
        throw new AntragsTypExceptions.Conflict("antragstyp key already exists: " + key);
    }
    AntragsTyp at = new AntragsTyp(UuidCreator.getTimeOrderedEpoch(), currentTenant(), key, title, description);
    at.recategorize(category);
    return antragsTypRepository.save(at);
}

public AntragsTyp setCategory(UUID id, AntragsKategorie category) {
    AntragsTyp at = getDefinition(id);
    at.recategorize(category);
    return antragsTypRepository.save(at);
}
```

- [ ] **Step 5: Controller — create reicht category durch**

In `AntragsTypController.create(...)`:

```java
AntragsTyp created = service.createDefinition(req.key(), req.title(), req.description(), req.category());
```

- [ ] **Step 6: Run — passes**

Run: `… mvn -ntp -pl application -am test -Dtest='AntragsTypServiceTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/
git commit -m "feat(antragstyp): category in Create/Response + setCategory (ADR-021)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: PUT /antragstyp/{id}/category Endpoint (TDD)

**Files:**
- Create: `…/antragstyp/dto/SetCategoryRequest.java`
- Modify: `…/antragstyp/AntragsTypController.java`
- Test: `application/src/test/java/io/github/manormachine2207/hrsuite/antragstyp/AntragsTypControllerTest.java` (falls vorhanden erweitern; sonst `@WebMvcTest(AntragsTypController.class) @Import({SecurityConfig.class, ApiExceptionHandler.class})` analog zu `SmtpRelayControllerTest`)

- [ ] **Step 1: Failing tests — PUT category 200 (hr-designer) / 400 (ungültig) / 403 (applicant)**

```java
@Test
void putCategory_returns200_forDesigner() throws Exception {
    when(service.setCategory(any(), eq(AntragsKategorie.ABSENCE))).thenReturn(sampleType());
    mvc.perform(put("/api/v1/antragstyp/{id}/category", UUID.randomUUID())
                    .with(jwt().authorities(role("hr-designer")))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"ABSENCE\"}"))
            .andExpect(status().isOk());
}

@Test
void putCategory_returns400_onInvalidValue() throws Exception {
    mvc.perform(put("/api/v1/antragstyp/{id}/category", UUID.randomUUID())
                    .with(jwt().authorities(role("hr-designer")))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"NONSENSE\"}"))
            .andExpect(status().isBadRequest());
}

@Test
void putCategory_returns403_forApplicant() throws Exception {
    mvc.perform(put("/api/v1/antragstyp/{id}/category", UUID.randomUUID())
                    .with(jwt().authorities(role("applicant")))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"ABSENCE\"}"))
            .andExpect(status().isForbidden());
}
```

(`role(...)`, `sampleType()`-Helper analog zu bestehenden Controller-Tests; `sampleType()` baut einen `AntragsTyp` und ruft `recategorize(...)`.)

- [ ] **Step 2: Run — fails (kein Endpoint)**

Run: `… mvn -ntp -pl application -am test -Dtest='AntragsTypControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (404/Compile).

- [ ] **Step 3: SetCategoryRequest DTO**

```java
package io.github.manormachine2207.hrsuite.antragstyp.dto;

import io.github.manormachine2207.hrsuite.antragstyp.AntragsKategorie;

/** Body für PUT /api/v1/antragstyp/{id}/category (ADR-021). null = entkategorisieren. */
public record SetCategoryRequest(AntragsKategorie category) {
}
```

- [ ] **Step 4: Controller-Endpoint** — in `AntragsTypController` (Import `SetCategoryRequest`, `PutMapping`):

```java
@PutMapping("/{id}/category")
@PreAuthorize(WRITE_DRAFT)
public AntragsTypResponse setCategory(@PathVariable("id") UUID id, @RequestBody SetCategoryRequest req) {
    return AntragsTypResponse.from(service.setCategory(id, req.category()));
}
```

- [ ] **Step 5: Run — passes** (das ungültige `"NONSENSE"` wird von Jackson zu `HttpMessageNotReadableException` → `ApiExceptionHandler` → 400; falls der Handler das noch nicht abdeckt, im selben Commit ein `@ExceptionHandler(HttpMessageNotReadableException.class)` → 400 ergänzen.)

Run: `… mvn -ntp -pl application -am test -Dtest='AntragsTypControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (3 Tests).

- [ ] **Step 6: Commit**

```bash
git add application/src/main/java/io/github/manormachine2207/hrsuite/antragstyp/
git commit -m "feat(antragstyp): PUT /{id}/category (hr-designer, ADR-021)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Backend voll verifizieren + Prototyp-Seed

**Files:**
- Modify: `scripts/seed-prototyp-antragstypen.sh`

- [ ] **Step 1: Voller verify** (Migration 017 wird von den ITs gebootet, Modularity bleibt grün)

Run: `docker run --rm -v "$HOME/Desktop/Git Repos/HR-Suite-code":/work -w /work -v hrsuite-m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock --add-host=host.docker.internal:host-gateway -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true maven:3.9-eclipse-temurin-21 mvn -ntp -pl application -am verify >/tmp/v.log 2>&1; echo EXIT=$?`
Expected: `EXIT=0`, BUILD SUCCESS, ModularityTests grün.

- [ ] **Step 2: Seed um Kategorien ergänzen** — in `seed-prototyp-antragstypen.sh` im psql-Block einen `UPDATE` je Mapping ergänzen (Keys aus `seed-prototyp-antragstypen.sh` / der bestehenden 9 Ketten):

```sql
UPDATE antragstyp SET category='ABSENCE'     WHERE tenant_id='${TENANT_ID}' AND key IN ('treuepraemie_urlaub','pikett_vaz');
UPDATE antragstyp SET category='DEVELOPMENT' WHERE tenant_id='${TENANT_ID}' AND key IN ('weiterbildung','tagung');
UPDATE antragstyp SET category='FINANCE'     WHERE tenant_id='${TENANT_ID}' AND key IN ('spontanpraemie','spesenantrag');
UPDATE antragstyp SET category='EMPLOYMENT'  WHERE tenant_id='${TENANT_ID}' AND key IN ('eintritt_externe','bg_wechsel','nebenbeschaeftigung','stellenantrag');
```

(Mapping bei Bedarf an die tatsächlich geseedeten Keys anpassen; Rest bleibt NULL → OTHER.)

- [ ] **Step 3: Commit**

```bash
git add scripts/seed-prototyp-antragstypen.sh
git commit -m "chore(seed): Prototyp-Antragstypen kategorisieren (ADR-021)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: FE — Kategorie-Konstante + i18n-Labels

**Files:**
- Create: `frontend/src/app/features/antragstyp/kategorie.model.ts`
- Modify: `frontend/src/assets/i18n/{de,fr,it,en}.json`

- [ ] **Step 1: Konstante schreiben**

`kategorie.model.ts`:

```typescript
/** Feste Kategorie-Keys (spiegelt das Backend-Enum AntragsKategorie, ADR-021).
 *  Labels via i18n-Key `antragstyp.category.<KEY>`. */
export const ANTRAGS_KATEGORIEN = ['ABSENCE', 'FINANCE', 'EMPLOYMENT', 'DEVELOPMENT', 'OTHER'] as const;
export type AntragsKategorie = typeof ANTRAGS_KATEGORIEN[number];

/** Backend liefert null für unkategorisierte Typen → im UI als OTHER behandeln. */
export function kategorieOf(value: string | null | undefined): AntragsKategorie {
  return (value && (ANTRAGS_KATEGORIEN as readonly string[]).includes(value))
    ? value as AntragsKategorie : 'OTHER';
}
```

- [ ] **Step 2: i18n-Labels in alle 4 Sprachen** — unter `antragstyp` einen `category`-Block ergänzen. DE:

```json
"category": {
  "label": "Kategorie",
  "ABSENCE": "Urlaub & Abwesenheit",
  "FINANCE": "Spesen & Finanzen",
  "EMPLOYMENT": "Anstellung & Personal",
  "DEVELOPMENT": "Weiterbildung",
  "OTHER": "Sonstiges"
}
```

FR: `Congés & absences / Frais & finances / Emploi & personnel / Formation / Autres` (label „Catégorie").
IT: `Congedi e assenze / Spese e finanze / Impiego e personale / Formazione / Altro` (label „Categoria").
EN: `Leave & absence / Expenses & finance / Employment & staff / Development & training / Other` (label „Category").

Zusätzlich unter `antrag` einen `katalog`-Block (alle 4 Sprachen): `title` („Neuen Antrag stellen"), `lead` („Wählen Sie einen Antragstyp."), `filterAll` („Alle"), `empty` („Keine Antragstypen verfügbar."), `start` („Antrag starten").

- [ ] **Step 3: i18n-Coverage grün**

Run: `bash scripts/check_i18n_coverage.sh`
Expected: alle Sprachen deckungsgleich, EXIT 0.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/antragstyp/kategorie.model.ts frontend/src/assets/i18n/
git commit -m "feat(fe): Kategorie-Konstante + i18n-Labels (ADR-021)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: FE — Summary-Model + Service

**Files:**
- Modify: `frontend/src/app/features/antragstyp/antragstyp.model.ts`
- Modify: `frontend/src/app/features/antragstyp/antragstyp.service.ts`
- Test: `frontend/src/app/features/antragstyp/antragstyp.service.spec.ts`

- [ ] **Step 1: `category` ins Summary-Model** — in `antragstyp.model.ts` das `AntragsTypSummary`-Interface um `readonly category: string | null;` ergänzen.

- [ ] **Step 2: Failing test — `setCategory` PUTet** — in `antragstyp.service.spec.ts`:

```typescript
it('PUTs the category to /antragstyp/:id/category', () => {
  service.setCategory('id-1', 'ABSENCE').subscribe();
  const req = ctrl.expectOne('/api/v1/antragstyp/id-1/category');
  expect(req.request.method).toBe('PUT');
  expect(req.request.body).toEqual({ category: 'ABSENCE' });
  req.flush({});
});
```

- [ ] **Step 3: Run — fails** (Methode fehlt)

Run: `docker exec hrsuite-fe-dev sh -c 'cd /app && npx ng test --watch=false -- antragstyp.service'` (oder volle Suite)
Expected: FAIL.

- [ ] **Step 4: `setCategory` im Service** — in `antragstyp.service.ts`:

```typescript
setCategory(id: string, category: string | null): Observable<AntragsTypSummary> {
  return this.http.put<AntragsTypSummary>(`${this.base}/antragstyp/${id}/category`, { category });
}
```

- [ ] **Step 5: Run — passes**

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/antragstyp/antragstyp.model.ts frontend/src/app/features/antragstyp/antragstyp.service.ts frontend/src/app/features/antragstyp/antragstyp.service.spec.ts
git commit -m "feat(fe): category im Summary + setCategory-Service (ADR-021)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: FE — Katalog-Komponente + Route (TDD)

**Files:**
- Create: `frontend/src/app/features/antrag/antrag-katalog.component.ts` / `.html` / `.scss` / `.spec.ts`
- Modify: `frontend/src/app/app.routes.ts`

- [ ] **Step 1: Failing spec** — `antrag-katalog.component.spec.ts`: Service-Stub liefert 2 LIVE-Typen unterschiedlicher Kategorie; Test prüft (a) je eine Kachel gerendert, (b) Klick auf Kategorie-Chip filtert, (c) Kachel-Klick ruft `router.navigate(['/antraege'], { queryParams: { neu: id } })`. Muster: `notification-bell.component.spec.ts` / `sso.component.spec.ts` (TestBed + `vi.spyOn(Router.prototype...)` ODER injizierter Router-Mock; **router.navigate mocken** — sonst unhandled rejection, siehe FE-Test-Memory).

```typescript
it('renders one tile per LIVE type and navigates with ?neu on click', async () => {
  service.list.mockReturnValue(of([
    { id: 'a', key: 'k1', title: { de: 'Urlaub' }, status: 'LIVE', category: 'ABSENCE', /*…*/ },
    { id: 'b', key: 'k2', title: { de: 'Spesen' }, status: 'LIVE', category: 'FINANCE', /*…*/ },
  ]));
  const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  const fx = TestBed.createComponent(AntragKatalogComponent);
  fx.detectChanges(); await fx.whenStable(); fx.detectChanges();
  expect(fx.nativeElement.querySelectorAll('.hr-kachel').length).toBe(2);
  (fx.nativeElement.querySelector('.hr-kachel') as HTMLElement).click();
  expect(nav).toHaveBeenCalledWith(['/antraege'], { queryParams: { neu: 'a' } });
});
```

- [ ] **Step 2: Run — fails** (Komponente fehlt).

- [ ] **Step 3: Komponente implementieren** — `antrag-katalog.component.ts` (standalone, OnPush/zoneless, `AntragsTypService.list()`, Signal `selectedCategory`, computed gefilterte Liste, `kategorieOf(...)` aus `kategorie.model`, `router.navigate` beim Klick). `.html`: Filter-Chips (`'antrag.katalog.filterAll'` + je vorkommende Kategorie via `'antragstyp.category.'+key | translate`), Kachel-Grid `.hr-kachel` (Titel via Locale-Pipe/`title[lang]`, Beschreibung, Kategorie-Badge), Leerzustand `'antrag.katalog.empty'`. `.scss`: Karten-Look gespiegelt von `home.component.scss`/Modul-Katalog (Memory: keine Duplizierung — gleiche Token-/Karten-Metriken).

- [ ] **Step 4: Route registrieren** — in `app.routes.ts` VOR `antraege/:id` einfügen:

```typescript
  {
    path: 'antraege/neu',
    loadComponent: () =>
      import('./features/antrag/antrag-katalog.component').then(m => m.AntragKatalogComponent),
  },
```

- [ ] **Step 5: Run — passes; Build grün.**

Run: FE-Test (Katalog-Spec) + `docker exec hrsuite-fe-dev sh -c 'cd /app && npx ng build'`
Expected: PASS, Build EXIT 0.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/antrag/antrag-katalog.component.* frontend/src/app/app.routes.ts
git commit -m "feat(fe): Antrags-Katalog /antraege/neu (Kacheln + Kategorie-Filter, ADR-021)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: FE — Anbindung an /antraege (Query-Param + Button)

**Files:**
- Modify: `frontend/src/app/features/antrag/antrag-list.component.ts` (+ ggf. `.html`)
- Test: `frontend/src/app/features/antrag/antrag-list.component.spec.ts` (falls vorhanden; sonst Verhalten im Logic-Spec)

- [ ] **Step 1: Failing test** — bei `?neu=<id>` öffnet die Liste das Inline-Formular vorausgewählt: `ActivatedRoute`-Stub mit `snapshot.queryParamMap.get('neu') => 'x'`; nach `ngOnInit` ist `creating === true` und `selectedTypId === 'x'` (und `onTypChange` wurde aufgerufen → `antragstypService.listVersions` mit 'x'). Router/Service mocken.

- [ ] **Step 2: Run — fails.**

- [ ] **Step 3: Implementieren** — in `antrag-list.component.ts`: `ActivatedRoute` injizieren; in `ngOnInit` nach dem Laden der eigenen Anträge:

```typescript
const neu = this.route.snapshot.queryParamMap.get('neu');
if (neu) { this.openCreate(); this.onTypChange(neu); }
```

`.html`: den „Neuer Antrag"-Button von `(click)="openCreate()"` auf `routerLink="/antraege/neu"` umstellen (RouterLink importieren). Das Inline-Panel bleibt für den `?neu=`-Pfad bestehen.

- [ ] **Step 4: Run — passes.**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/antrag/antrag-list.component.*
git commit -m "feat(fe): /antraege öffnet bei ?neu= das vorausgewählte Formular; Button → Katalog (ADR-021)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: FE — Kategorie-Dropdown in der Create-Seite

**Files:**
- Modify: `frontend/src/app/features/antragstyp/antragstyp-create.component.ts` / `.html`
- Modify: `frontend/src/app/features/antragstyp/antragstyp.service.ts` (`createAntragstyp` um optional category)
- Test: `frontend/src/app/features/antragstyp/antragstyp-create.component.spec.ts`

- [ ] **Step 1: Failing test** — bei gesetztem Kategorie-Control sendet `submit()` die Kategorie mit (Service-Spy bekommt `category` im Aufruf).

- [ ] **Step 2: Run — fails.**

- [ ] **Step 3: Implementieren** — `antragstyp-create.component.ts`: Form-Control `category` (nonNullable, default `''`); im `submit()` an den Service durchreichen. `antragstyp.service.ts`: `createAntragstyp(key, title, category?: string)` → Body `{ key, title, ...(category ? { category } : {}) }`. `.html`: ein `select` mit `@for (k of kategorien; ...)` (`ANTRAGS_KATEGORIEN`) + Option „—" für leer; Label `'antragstyp.category.label'`, Optionen `'antragstyp.category.'+k | translate`.

- [ ] **Step 4: Run — passes; i18n grün.**

- [ ] **Step 5: Commit** (`feat(fe): Kategorie-Dropdown beim Antragstyp-Anlegen (ADR-021)`).

---

## Task 10: FE — Recategorize-Select in der Antragstyp-Liste

**Files:**
- Modify: `frontend/src/app/features/antragstyp/antragstyp-list.component.ts` / `.html`
- Test: `frontend/src/app/features/antragstyp/antragstyp-list.component.spec.ts` (falls vorhanden)

- [ ] **Step 1: Failing test** — Änderung des Kategorie-`select` einer Zeile ruft `service.setCategory(id, value)`.

- [ ] **Step 2: Run — fails.**

- [ ] **Step 3: Implementieren** — pro Zeile ein kleines `select` (Werte `ANTRAGS_KATEGORIEN`, Vorauswahl `kategorieOf(row.category)`); `(change)` → `service.setCategory(row.id, value).subscribe({ next: … markForCheck })`. Nur in der HR-Designer-Sicht anzeigen (die Liste ist bereits rollengated). i18n-Label wiederverwenden.

- [ ] **Step 4: Run — passes.**

- [ ] **Step 5: Commit** (`feat(fe): Kategorie pro Antragstyp in der Liste nachpflegen (ADR-021)`).

---

## Task 11: FE — Dashboard-Eintrag „Neuen Antrag stellen"

**Files:**
- Modify: `frontend/src/app/features/home/module-catalog.ts`
- Test: `frontend/src/app/features/home/module-catalog.spec.ts`

- [ ] **Step 1: Failing test** — der Katalog enthält einen Eintrag mit Route `/antraege/neu`.

- [ ] **Step 2: Run — fails.**

- [ ] **Step 3: Implementieren** — Eintrag „Neuen Antrag stellen" (route `/antraege/neu`, i18n-Titel/Beschreibung, passendes Icon) im deklarativen Modul-Katalog ergänzen; i18n ×4 nachziehen.

- [ ] **Step 4: Run — passes; i18n grün.**

- [ ] **Step 5: Commit** (`feat(fe): Dashboard-CTA „Neuen Antrag stellen" → /antraege/neu (ADR-021)`).

---

## Task 12: Voll-Verifikation + Screenshot

- [ ] **Step 1: FE volle Suite** — `docker exec hrsuite-fe-dev sh -c 'cd /app && npx ng test --watch=false' >/tmp/fe.log 2>&1; echo EXIT=$?` → `grep -iE "unhandled|Errors |error TS|✗" /tmp/fe.log`. Expected: EXIT 0, keine Red Flags.
- [ ] **Step 2: FE-Build** grün.
- [ ] **Step 3: i18n-Coverage** grün.
- [ ] **Step 4: Backend `verify`** grün (falls seit Task 4 Backend berührt).
- [ ] **Step 5: Stack neu bauen + re-seeden** — `docker compose build backend app && docker compose up -d backend app`; warten auf Health; `bash scripts/dev-seed.sh && bash scripts/seed-prototyp-antragstypen.sh`.
- [ ] **Step 6: Screenshot-Verifikation** (Memory: UI-Qualitätsmaßstab) — Browser auf `/antraege/neu`: Kacheln gerendert, Kategorie-Filter funktioniert, Klick → `/antraege` mit vorausgewähltem Formular. Vergleich gegen Dashboard-Kachel-Look. **Kein Token-Switch nötig** (Antragsteller = `dev-hr-designer` hat keinen applicant-Scope — zum Test des Antragsteller-Pfads via Dev-Rollen-Switcher Rolle `applicant` wählen, kein runtime.json-Edit, siehe Memory).
- [ ] **Step 7: PR** `feat/antrags-katalog` → `main`; Vault (DRAFT-ADR-021 promoten → ADR-021, Register-Eintrag, _NOW, Architekturdiagramm FE-Route) als geflaggte Vault-Änderung vorbereiten.

---

## Self-Review (vom Autor)

- **Spec-Abdeckung:** Datenmodell (T1), API create/response/PUT (T2/T3), Katalog-Seite (T7), `?neu=`-Anbindung + Button (T8), Kategorie setzen Create (T9) + Liste (T10), Dashboard (T11), Seeds (T4), i18n (T5/T9/T11), Tests je Task, Verifikation (T12). Alle Spec-Abschnitte abgedeckt.
- **Platzhalter:** keine TODO/TBD; FE-Modifikationen nennen exakte Anker + Code; reine FE-Listenmodifikationen verweisen auf bestehende Specs als Muster (zulässig, da Datei im Task genannt).
- **Typkonsistenz:** `AntragsKategorie` (Backend-Enum) ↔ `ANTRAGS_KATEGORIEN`/`AntragsKategorie` (FE-Konstante) ↔ `category`-Feld/Spalte/DTO durchgängig; `setCategory`/`recategorize`/`PUT …/category` konsistent benannt.
- **Risiko:** ungültiger Enum-Wert → 400 hängt am `ApiExceptionHandler` (Task 3 Step 5 deckt den Fallback ab). Seed-Keys in T4 ggf. an reale Prototyp-Keys anpassen.
