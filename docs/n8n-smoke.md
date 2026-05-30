# n8n smoke (Cut A)

1. `docker compose up -d postgres backend n8n`
2. Open http://localhost:5678 (admin / dev), Import `docker/n8n/echo-workflow.json`, Activate it.
3. `bash scripts/dev-seed.sh` (creates demo tenant + tenant_n8n_config -> http://n8n:5678).
4. Trigger an ACTION end-to-end: until the flow editor exists (Cut C), deploy
   `application/src/test/resources/bpmn/action-test-process.bpmn20.xml` via the backend's
   Flowable RepositoryService (or reuse the N8nActionConnectorIT path), then check:
   - n8n execution log shows one call to `/webhook/provision-ad-account`.
   - `select status, attempts, ref from action_execution;` shows SUCCEEDED, attempts=1.
