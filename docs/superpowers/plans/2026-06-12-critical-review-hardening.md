# Plan — Critical-Review-Härtung (2026-06-12)

> Quelle: Kritische Review-Session 2026-06-12 (Funktion/UI/Logik). Branch
> `fix/critical-review-hardening`. Jeder Punkt TDD (Red→Green), kleine Commits
> mit Decision-Referenz. SP1 folgt separat (`feat/sp1-graph-compiler`),
> Review-Pfad nur als Decision-Draft im Vault (Tenet 15).

## Backend

1. ✅ `RuntimeDbRoleCheck` + IT committen (war untracked; ADR-008).
2. **Raw-BPMN-Deploy schließen (CRITICAL):** `CreateVersionRequest.workflowBpmn`
   entfällt — BPMN ist ausschließlich Compiler-Output (ADR-010 „HR sieht kein
   BPMN“). `publish()` deployt nur noch kompiliertes BPMN oder den
   Default-Platzhalter; client-gelieferte XML wird nie deployt.
   Tests: Controller-Slice (Feld wird ignoriert), Service-Test (publish ohne
   flow/graph → Platzhalter; gespeichertes rohes BPMN wird NICHT deployt).
3. **Outcome-Injection schließen (Cut-B-Follow-up a):** `ApprovalStep.outcomes`
   gegen `KEY_PATTERN` validieren (Outcomes sind technische Werte; Anzeige-Labels
   sind i18n-Sache des Editors) + `esc()` an allen Interpolationsstellen.
   `ActionStep.ref` → `requireNonNull` (Follow-up c).
4. **Gateway-Default-Flow (Cut-B-Follow-up b):** Continue-Pfad wird
   `default`-Flow des exclusiveGateway → unbekanntes Outcome bleibt nicht stecken,
   Flowable-Warnung entfällt.
5. **Action-Idempotenz (n8n-Follow-up b):** Idempotenz-Schlüssel von
   `processInstanceId:stepKey` auf `antragId (Business-Key) + stepKey` (Migration 010,
   neue Spalte `business_key` + Unique) — Resubmit nach Rollback dedupliziert wieder.
   FAILED/DEAD re-attempten bei Re-Entry nicht mehr (BpmnError statt Re-Fire);
   RUNNING (Crash-Recovery) darf weiter re-attempten.
6. **Applicant-Sichtbarkeit:** `GET /api/v1/antragstyp` liefert für Rolle
   `applicant` nur LIVE-Typen (HR-Drafts sind Arbeitsstände).
7. **Publish-Quality-Gate (BDR-005):** `publish()` → 422 wenn Antragstyp-Titel
   oder ein Feld-Label/Options-Label nicht in allen vier Sprachen (de/fr/it/en,
   non-blank) vorliegt. Fehlerliste benennt die Lücken.
8. **Payload-Validierung (ADR-009 / „validieren an Systemgrenzen“):**
   `submit()` validiert Payload gegen die gepinnte FormDefinition: unbekannte Keys,
   required, maxLength, min/max, SELECT-Werte ∈ Optionen. 422 mit Feldliste.

## Frontend

9. **Navigation + /home:** Logo-/NotFound-Link reparieren (Route `home` →
   Redirect auf `/`), echte Top-Navigation (Antragstypen / Anträge) im
   Oblique-Layout.
10. **Zoneless-Restfehler:** `markForCheck()` im Error-Pfad von
    `antragstyp-create`; verschachtelte Subscribes in `antrag-list`; NG0100-Ursache
    beheben.
11. **i18n-Hygiene:** roher Key `antragstyp.list.title` auf der Create-Seite,
    Doppel-Überschrift, hartkodierte deutsche Strings (httpMessage, Canvas-
    Warnungen) → Übersetzungskeys in de/fr/it/en.
12. **Sprachwechsel-Reaktivität:** Typ-Titel reaktiv auf `TranslateService`
    (statt einmalig `document.lang`), Status-Badges übersetzen, Datumsformat
    folgt aktiver Locale.
13. **Designer-UX:** Publish speichert vorher (kein Stale-Publish), Publish nach
    Erfolg deaktiviert, Disabled-Grund sichtbar; a11y: Sprachfelder programmatisch
    gelabelt.
14. **Antrag-UX:** maxLength/min/max-Validators aus FormDefinition; Fehlanzeige
    pro Feld; gescheitertes Submit → Draft mit Aktionen „erneut einreichen“ /
    „stornieren“ statt toter Zeile.

## Bewusst NICHT hier

- SP1 (eigener Branch/Plan), Review-Pfad (Decision-Draft), Backoff/async-Actions
  (Follow-up bleibt), act_*-RLS-Konzept (braucht eigene Betrachtung), Pagination,
  OpenAPI-Living-Spec (offenes Follow-up).
