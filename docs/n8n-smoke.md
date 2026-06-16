# n8n smoke (Cut A)

1. `docker compose up -d postgres backend n8n`
2. Open http://localhost:5678 (admin / dev), Import `docker/n8n/echo-workflow.json`, Activate it.
3. `bash scripts/dev-seed.sh` (creates demo tenant + tenant_n8n_config -> http://n8n:5678).
4. Trigger an ACTION end-to-end: until the flow editor exists (Cut C), deploy
   `application/src/test/resources/bpmn/action-test-process.bpmn20.xml` via the backend's
   Flowable RepositoryService (or reuse the N8nActionConnectorIT path), then check:
   - n8n execution log shows one call to `/webhook/provision-ad-account`.
   - `select status, attempts, ref from action_execution;` shows SUCCEEDED, attempts=1.

# SF-LMS-Übergabe (Cut 3, Prototyp-Inventur Abschnitt C)

Mock des SF-Learning-Calls `recordLearningEvents`; der Weiterbildungs-Flow ruft ihn
als ACTION `sf-lms-record-learning-events` mit `${var}`-Mapping (geschlossene
Prozessvariablen-Lookups, kein JUEL — siehe `N8nActionDelegate`).

1. Workflow importieren + aktivieren (CLI-Variante, UI geht auch):
   ```bash
   docker cp docker/n8n/sf-lms-workflow.json hrsuite-n8n:/tmp/sf-lms-workflow.json
   docker exec hrsuite-n8n n8n import:workflow --input=/tmp/sf-lms-workflow.json
   docker exec hrsuite-n8n n8n update:workflow --id=sfLmsRecordLearnEv01 --active=true
   docker restart hrsuite-n8n
   ```
2. `bash scripts/seed-prototyp-antragstypen.sh` — legt die 9 Prototyp-Antragstypen
   im fixen Dev-Tenant an (publiziert) und nimmt den SF-LMS-Ref in `allowed_refs` auf.
3. e2e: Weiterbildungs-Antrag mit `kosten > 5000` einreichen, Kette über `/aufgaben`
   genehmigen (Erfassung → VG → HR-BP → HAL) → `action_execution` zeigt
   `sf_lms_uebergabe | sf-lms-record-learning-events | SUCCEEDED`.

# HMAC-Signatur-Erzwingung (ADR-010 Trust-Boundary, Spec ADR-010-spec-n8n-signature-enforcement)

Beide Workflows validieren den vom Backend gesendeten `X-HRSuite-Signature`
(HMAC-SHA256 über den Roh-Body). Voraussetzung: die n8n-ENV
`HRSUITE_N8N_HMAC_SECRET` / `NODE_FUNCTION_ALLOW_BUILTIN=crypto` /
`N8N_BLOCK_ENV_ACCESS_IN_NODE=false` sind gesetzt (docker-compose) und der Secret
stimmt mit dem des Backends überein (`${N8N_HMAC_SECRET:-dev-n8n-secret}`).

1. Aktualisierte Workflows importieren (idempotent, mit fixer id):
   ```bash
   for w in echo sf-lms; do
     id=$([ $w = echo ] && echo provisiondemo001 || echo sfLmsRecordLearnEv01)
     docker cp docker/n8n/$w-workflow.json hrsuite-n8n:/tmp/$w.json
     docker exec hrsuite-n8n n8n import:workflow --input=/tmp/$w.json
     docker exec hrsuite-n8n n8n update:workflow --id=$id --active=true
   done
   docker restart hrsuite-n8n
   ```
   (Webhook-Registrierung hinkt dem `healthz` nach — den Endpoint pollen, bis er
   nicht mehr 404 liefert.)
2. Tamper-Check (Dev):
   ```bash
   S=dev-n8n-secret; B='{"idempotencyKey":"t:1","ref":"provision-ad-account","input":{}}'
   SIG=$(printf '%s' "$B" | openssl dgst -sha256 -hmac "$S" -r | cut -d' ' -f1)
   curl -s -o /dev/null -w "korrekt: %{http_code}\n"  -X POST localhost:5678/webhook/provision-ad-account -H "X-HRSuite-Signature: $SIG" -H "Content-Type: application/json" -d "$B"   # 200
   curl -s -o /dev/null -w "falsch:  %{http_code}\n"  -X POST localhost:5678/webhook/provision-ad-account -H "X-HRSuite-Signature: bad" -H "Content-Type: application/json" -d "$B"     # 401
   curl -s -o /dev/null -w "ohne:    %{http_code}\n"  -X POST localhost:5678/webhook/provision-ad-account -H "Content-Type: application/json" -d "$B"                                    # 401
   ```
   Erwartet: 200 / 401 / 401. Ungültige Signatur → 4xx → Connector terminal (FAILED).
