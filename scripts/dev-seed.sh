#!/usr/bin/env bash
# dev-seed.sh — seed the local dev stack with a demo tenant + sample Antragstypen,
# so the frontend Antragstyp list (Phase 3) has something to show.
#
# Prereqs: compose stack running (backend reachable; default http://localhost:8081/api/v1),
# `curl` + `jq` available. Dev auth only: the backend dev JwtDecoder accepts
# `dev-platform-admin` and tenant-scoped `dev-<role>~<tenant-uuid>` (NOT for prod).
#
# Tenant `code`/`subdomain` must be unique, so a short random suffix is used to keep
# the script rerunnable against the same database. The printed DEV TOKEN is what the
# frontend uses (runtime.json -> devAuth.token) to read this tenant's data.
set -euo pipefail

BASE="${1:-http://localhost:8081/api/v1}"
ADMIN="dev-platform-admin"
SUFFIX="${RANDOM}"
CODE="DEMO${SUFFIX}"
SUB="demo${SUFFIX}"

j() { curl -fsS -H "Content-Type: application/json" "$@"; }

echo "== Seeding against ${BASE} =="

TENANT_ID=$(j -X POST "$BASE/tenant" -H "Authorization: Bearer $ADMIN" \
  -d "{\"code\":\"$CODE\",\"subdomain\":\"$SUB\",\"displayName\":{\"de\":\"Demo-Amt\",\"fr\":\"Office démo\",\"it\":\"Ufficio demo\",\"en\":\"Demo office\"}}" \
  | jq -r .id)
echo "tenant: $TENANT_ID ($CODE)"

DESIGNER="dev-hr-designer~$TENANT_ID"

# Create several draft Antragstypen (OSS-generic HR processes, see 01-Vision-und-Scope).
declare -a KEYS=(sonderurlaub bildungsurlaub teilzeit-antrag sabbatical)
declare -a DE=("Sonderurlaub" "Bildungsurlaub" "Teilzeit-Antrag" "Sabbatical")
declare -a FR=("Congé spécial" "Congé de formation" "Demande de temps partiel" "Congé sabbatique")
declare -a IT=("Congedo speciale" "Congedo di formazione" "Richiesta tempo parziale" "Congedo sabbatico")
declare -a EN=("Special leave" "Educational leave" "Part-time request" "Sabbatical")

FIRST_ID=""
for i in "${!KEYS[@]}"; do
  AID=$(j -X POST "$BASE/antragstyp" -H "Authorization: Bearer $DESIGNER" \
    -d "{\"key\":\"${KEYS[$i]}\",\"title\":{\"de\":\"${DE[$i]}\",\"fr\":\"${FR[$i]}\",\"it\":\"${IT[$i]}\",\"en\":\"${EN[$i]}\"}}" \
    | jq -r .id)
  echo "antragstyp: ${KEYS[$i]} -> $AID"
  [ -z "$FIRST_ID" ] && FIRST_ID="$AID"
done

# Publish the first one so the list shows a LIVE row next to the DRAFTs.
VID=$(j -X POST "$BASE/antragstyp/$FIRST_ID/versions" -H "Authorization: Bearer $DESIGNER" \
  -d '{"formDefinition":{"fields":[{"key":"grund","type":"TEXT","required":true,"label":{"de":"Grund","fr":"Motif","it":"Motivo","en":"Reason"}}]},"workflowBpmn":"<bpmn/>","sfActionBindings":{}}' \
  | jq -r .id)
j -X POST "$BASE/antragstyp/versions/$VID/publish" -H "Authorization: Bearer $DESIGNER" >/dev/null
echo "published: $FIRST_ID (version $VID) -> LIVE"

echo
echo "DEV TOKEN (put into frontend runtime.json -> devAuth.token):"
echo "  $DESIGNER"
