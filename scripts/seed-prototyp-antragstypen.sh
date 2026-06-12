#!/usr/bin/env bash
# seed-prototyp-antragstypen.sh — legt die 9 Antragstypen aus der Owner-Prototyp-
# Inventur (Vault: Prototyp-Inventur-2026-06-12.md, Abschnitt B) im fixen Dev-Tenant
# an und publiziert sie: 4-sprachig, mit Genehmigungsketten als Graph
# (Erfassung → VG → HR-BP → ggf. HAL), Kosten-Eskalation `kosten > 5000` ⇒ HAL und
# SF-LMS-Übergabe (ACTION sf-lms-record-learning-events) im Weiterbildungs-Flow.
#
# Rollen (ADR-016): die APPROVAL-Stufen laufen auf den fachlichen Genehmiger-
# Gruppen approver-vg / approver-hr-bp / approver-hal (Dev-Tokens:
# dev-approver-vg~<tenant> usw.); Org-Auflösung je Antragsteller bleibt Phase 2.
#
# Rerunnable: existierende Keys werden übersprungen (409 → skip);
# REPUBLISH=1 publiziert für existierende Typen eine NEUE Version (Minor-Bump).
# Prereqs: Compose-Stack läuft, `curl` + `jq` + `python3`; dev-seed.sh lief einmal
# (fixer Dev-Tenant + tenant_n8n_config existieren). n8n: docker/n8n/sf-lms-workflow.json
# importieren + aktivieren (siehe docs/n8n-smoke.md).
set -euo pipefail

BASE="${1:-http://localhost:8081/api/v1}"
TENANT_ID="019e754d-371c-70e0-b199-88ab785bef6e"   # fixer Dev-Tenant (runtime.json)
DESIGNER="dev-hr-designer~${TENANT_ID}"

echo "== Prototyp-Antragstypen gegen ${BASE} (Tenant ${TENANT_ID}) =="

# 1) SF-LMS-Ref in die n8n-Allowlist des Dev-Tenants aufnehmen (idempotent).
docker exec -i -e PGPASSWORD="${POSTGRES_PASSWORD:-dev}" hrsuite-postgres \
  psql -U hrsuite -d hrsuite -v ON_ERROR_STOP=1 <<SQL
UPDATE tenant_n8n_config
   SET allowed_refs = '["provision-ad-account","sf-lms-record-learning-events"]'::jsonb,
       updated_at = now()
 WHERE tenant_id = '${TENANT_ID}';
SQL
echo "allowed_refs: + sf-lms-record-learning-events"

# 2) Typen anlegen + publizieren. Die JSON-Bodies (Form + Graph) baut Python,
#    damit die Ketten programmatisch und lesbar bleiben.
python3 - "$BASE" "$DESIGNER" <<'PY'
import json, sys, urllib.request, urllib.error

BASE, DESIGNER = sys.argv[1], sys.argv[2]

def call(method, path, body=None):
    req = urllib.request.Request(BASE + path, method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + DESIGNER})
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return r.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:300]

L = lambda de, fr, it, en: {"de": de, "fr": fr, "it": it, "en": en}

def field(key, ftype, required, label):
    return {"key": key, "type": ftype, "required": required, "label": label}

BEGRUENDUNG = field("begruendung", "TEXT", True,
                    L("Begründung", "Justification", "Motivazione", "Reason"))

# Stufen-Vokabular mit den fachlichen Genehmiger-Gruppen (ADR-016).
STUFEN = {
    "vg":    ("vg_freigabe",   L("Freigabe Vorgesetzte:r", "Approbation supérieur·e", "Approvazione superiore", "Supervisor approval")),
    "hr_bp": ("hr_bp_pruefung", L("Prüfung HR-Businesspartner", "Examen HR Business Partner", "Verifica HR Business Partner", "HR business partner review")),
    "hal":   ("hal_freigabe",  L("Freigabe HAL", "Approbation chef·fe de division", "Approvazione capo divisione", "Head-of-division approval")),
}
ROLES = {"vg": "approver-vg", "hr_bp": "approver-hr-bp", "hal": "approver-hal"}

def chain_graph(stufen, cost_xor_var=None, action=None):
    """Erfassung -> je Stufe APPROVAL + Entscheid-XOR (approve -> weiter,
    reject/Default -> Ende abgelehnt); optional Kosten-XOR (> 5000 -> HAL);
    optional ACTION vor dem Genehmigt-Ende."""
    nodes = [
        {"id": "n_start", "type": "START", "position": {"x": 0, "y": 0}, "data": {}},
        {"id": "n_erf", "type": "FORM", "position": {"x": 140, "y": 0},
         "data": {"key": "erfassung", "title": L("Erfassung", "Saisie", "Registrazione", "Entry")}},
        {"id": "n_end_ok", "type": "END", "position": {"x": 1500, "y": 0}, "data": {}},
        {"id": "n_end_nok", "type": "END", "position": {"x": 800, "y": 220}, "data": {}},
    ]
    edges = [{"id": "e_start", "source": "n_start", "target": "n_erf"}]
    prev, x = "n_erf", 280

    def approval(stufe_id, x, first):
        key, title = STUFEN[stufe_id]
        nodes.append({"id": f"n_{key}", "type": "APPROVAL", "position": {"x": x, "y": 0},
                      "data": {"key": key, "title": title, "assigneeRole": ROLES[stufe_id]}})
        nodes.append({"id": f"n_{key}_x", "type": "XOR", "position": {"x": x + 140, "y": 0},
                      "data": {"key": f"{key}_entscheid",
                               "title": L("Entscheid", "Décision", "Decisione", "Decision")}})
        # Eingang nur für die erste Stufe (vom FORM-Knoten); alle weiteren Stufen
        # erreicht man ausschliesslich über die konditionierte approve-Kante des
        # Vorgänger-XOR — sonst entstünden zwei Default-Branches am XOR.
        if first:
            edges.append({"id": f"e_{key}_in", "source": prev_holder[0], "target": f"n_{key}"})
        edges.append({"id": f"e_{key}_x", "source": f"n_{key}", "target": f"n_{key}_x"})
        edges.append({"id": f"e_{key}_rej", "source": f"n_{key}_x", "target": "n_end_nok",
                      "label": "abgelehnt", "condition": f"{key}_outcome == 'reject'"})
        edges.append({"id": f"e_{key}_def", "source": f"n_{key}_x", "target": "n_end_nok",
                      "label": "kein Entscheid"})
        prev_holder[0] = f"n_{key}_x"
        return f"n_{key}_x", key

    prev_holder = [prev]
    approve_sources = []   # (xor_node, step_key) – approve-Kante wird nachgezogen
    for i, s in enumerate(stufen):
        xnode, key = approval(s, x, first=(i == 0))
        approve_sources.append((xnode, key))
        x += 300

    # approve-Kanten der Reihe nach verbinden: XOR_i -> APPROVAL_{i+1} bzw. Endstück
    targets = []
    for i in range(1, len(approve_sources)):
        targets.append(f"n_{STUFEN[stufen[i]][0]}")
    # Endstück nach der letzten Stufe:
    tail = "n_end_ok"
    if cost_xor_var:
        nodes.append({"id": "n_kost", "type": "XOR", "position": {"x": x, "y": 0},
                      "data": {"key": "kostenpruefung",
                               "title": L("Kostenprüfung", "Contrôle des coûts", "Controllo costi", "Cost check")}})
        hal_key, hal_title = STUFEN["hal"]
        nodes.append({"id": "n_hal_esc", "type": "APPROVAL", "position": {"x": x + 140, "y": -160},
                      "data": {"key": hal_key, "title": hal_title, "assigneeRole": ROLES["hal"]}})
        nodes.append({"id": "n_hal_esc_x", "type": "XOR", "position": {"x": x + 300, "y": -160},
                      "data": {"key": f"{hal_key}_entscheid",
                               "title": L("Entscheid", "Décision", "Decisione", "Decision")}})
        edges.append({"id": "e_kost_hal", "source": "n_kost", "target": "n_hal_esc",
                      "label": "> 5000", "condition": f"{cost_xor_var} > 5000"})
        edges.append({"id": "e_hal_esc_x", "source": "n_hal_esc", "target": "n_hal_esc_x"})
        edges.append({"id": "e_hal_esc_rej", "source": "n_hal_esc_x", "target": "n_end_nok",
                      "label": "abgelehnt", "condition": f"{hal_key}_outcome == 'reject'"})
        edges.append({"id": "e_hal_esc_def", "source": "n_hal_esc_x", "target": "n_end_nok",
                      "label": "kein Entscheid"})
        x += 460
        tail = "n_kost"

    after_tail = "n_end_ok"
    if action:
        nodes.append({"id": "n_act", "type": "ACTION", "position": {"x": x, "y": 0},
                      "data": {"key": action["key"], "title": action["title"],
                               "ref": action["ref"], "inputMapping": action["inputMapping"]}})
        edges.append({"id": "e_act_end", "source": "n_act", "target": "n_end_ok"})
        after_tail = "n_act"

    # approve-Kanten setzen
    for i, (xnode, key) in enumerate(approve_sources):
        if i + 1 < len(approve_sources):
            target = f"n_{STUFEN[stufen[i + 1]][0]}"
        elif tail == "n_kost":
            target = "n_kost"
        else:
            target = after_tail
        edges.append({"id": f"e_{key}_ok", "source": xnode, "target": target,
                      "label": "genehmigt", "condition": f"{key}_outcome == 'approve'"})
    if cost_xor_var:
        edges.append({"id": "e_kost_def", "source": "n_kost", "target": after_tail,
                      "label": "≤ 5000"})
        edges.append({"id": "e_hal_esc_ok", "source": "n_hal_esc_x", "target": after_tail,
                      "label": "genehmigt",
                      "condition": f"{STUFEN['hal'][0]}_outcome == 'approve'"})

    return {"nodes": nodes, "edges": edges}

SF_LMS_ACTION = {
    "key": "sf_lms_uebergabe",
    "title": L("SF-LMS-Übergabe", "Transfert SF LMS", "Trasferimento SF LMS", "SF LMS hand-over"),
    "ref": "sf-lms-record-learning-events",
    # ${var}-Werte = geschlossene Prozessvariablen-Lookups (N8nActionDelegate, Cut 3);
    # Ziel: SF Learning OData v4 recordLearningEvents (Inventur Abschnitt C).
    "inputMapping": {
        "studentID": "${personalnummer}",
        "description": "${massnahme}",
        "creditHours": "${umfang_stunden}",
        "instructorName": "${anbieter}",
        "comments": "HR-Suite Aus-/Weiterbildung",
        "dataPrivacyLabel": "INTERN",
        "sourceTraceId": "${antragId}",
    },
}

TYPES = [
    {"key": "stellenantrag", "sla": 7,
     "title": L("Stellenantrag", "Demande de poste", "Richiesta di posto", "Position request"),
     "fields": [
         field("bezeichnung", "TEXT", True, L("Stellenbezeichnung", "Intitulé du poste", "Denominazione del posto", "Position title")),
         field("pensum_prozent", "NUMBER", True, L("Pensum (%)", "Taux d'occupation (%)", "Grado di occupazione (%)", "Workload (%)")),
         field("kosten", "NUMBER", False, L("Kosten (CHF)", "Coûts (CHF)", "Costi (CHF)", "Costs (CHF)")),
         BEGRUENDUNG],
     "stufen": ["vg", "hr_bp", "hal"]},
    {"key": "weiterbildung", "sla": 7,
     "title": L("Aus- und Weiterbildung", "Formation continue", "Formazione continua", "Training & development"),
     "fields": [
         field("massnahme", "TEXT", True, L("Bildungsmassnahme", "Mesure de formation", "Misura formativa", "Training measure")),
         field("anbieter", "TEXT", True, L("Anbieter", "Prestataire", "Fornitore", "Provider")),
         field("beginn", "DATE", False, L("Beginn", "Début", "Inizio", "Start")),
         field("umfang_stunden", "NUMBER", True, L("Umfang (Stunden)", "Volume (heures)", "Volume (ore)", "Scope (hours)")),
         field("kosten", "NUMBER", True, L("Kosten (CHF)", "Coûts (CHF)", "Costi (CHF)", "Costs (CHF)")),
         field("personalnummer", "TEXT", True, L("Personalnummer", "Numéro personnel", "Numero personale", "Employee ID")),
         BEGRUENDUNG],
     "stufen": ["vg", "hr_bp"], "cost_var": "kosten", "action": SF_LMS_ACTION},
    {"key": "nebenbeschaeftigung", "sla": 10,
     "title": L("Nebenbeschäftigung", "Activité accessoire", "Attività accessoria", "Secondary employment"),
     "fields": [
         field("taetigkeit", "TEXT", True, L("Tätigkeit", "Activité", "Attività", "Activity")),
         field("arbeitgeber", "TEXT", True, L("Arbeitgeber/Auftraggeber", "Employeur/mandant", "Datore di lavoro/committente", "Employer/client")),
         field("stunden_pro_woche", "NUMBER", True, L("Stunden pro Woche", "Heures par semaine", "Ore alla settimana", "Hours per week")),
         BEGRUENDUNG],
     "stufen": ["vg", "hr_bp", "hal"]},
    {"key": "bg_wechsel", "sla": 14,
     "title": L("BG-Wechsel", "Changement du taux d'occupation", "Modifica del grado di occupazione", "Change of employment level"),
     "fields": [
         field("neuer_beschaeftigungsgrad", "NUMBER", True, L("Neuer Beschäftigungsgrad (%)", "Nouveau taux (%)", "Nuovo grado (%)", "New level (%)")),
         field("gueltig_ab", "DATE", True, L("Gültig ab", "Valable dès", "Valido dal", "Valid from")),
         BEGRUENDUNG],
     "stufen": ["vg", "hr_bp"]},
    {"key": "eintritt_externe", "sla": 7,
     "title": L("Eintritt Externe (HR)", "Entrée externes (RH)", "Entrata esterni (HR)", "External onboarding (HR)"),
     "fields": [
         field("name", "TEXT", True, L("Name, Vorname", "Nom, prénom", "Cognome, nome", "Last name, first name")),
         field("organisation", "TEXT", True, L("Organisation/Firma", "Organisation/société", "Organizzazione/ditta", "Organisation/company")),
         field("funktion", "TEXT", True, L("Funktion", "Fonction", "Funzione", "Function")),
         field("eintritt_am", "DATE", True, L("Eintritt am", "Entrée le", "Entrata il", "Start date"))],
     "stufen": ["vg", "hr_bp"]},
    {"key": "spontanpraemie", "sla": 5,
     "title": L("Spontanprämie", "Prime spontanée", "Premio spontaneo", "Spot bonus"),
     "fields": [
         field("empfaenger_personalnummer", "TEXT", True, L("Empfänger:in (Personalnummer)", "Bénéficiaire (n° personnel)", "Beneficiario (n. personale)", "Recipient (employee ID)")),
         field("kosten", "NUMBER", True, L("Betrag (CHF)", "Montant (CHF)", "Importo (CHF)", "Amount (CHF)")),
         BEGRUENDUNG],
     "stufen": ["vg", "hr_bp"], "cost_var": "kosten"},
    {"key": "treuepraemie_urlaub", "sla": 5,
     "title": L("Treueprämie als Urlaub", "Prime de fidélité en congé", "Premio fedeltà come congedo", "Loyalty bonus as leave"),
     "fields": [
         field("dienstjahre", "NUMBER", True, L("Dienstjahre", "Années de service", "Anni di servizio", "Years of service")),
         field("bezug_tage", "NUMBER", True, L("Bezug (Tage)", "Prise (jours)", "Fruizione (giorni)", "Days taken")),
         field("ab_datum", "DATE", False, L("Ab Datum", "À partir du", "Dal", "From date"))],
     "stufen": ["vg", "hr_bp"]},
    {"key": "pikett_vaz", "sla": 5,
     "title": L("Pikett mit VAZ", "Piquet avec compensation", "Picchetto con compensazione", "On-call with comp time"),
     "fields": [
         field("zeitraum_von", "DATE", True, L("Zeitraum von", "Période du", "Periodo dal", "Period from")),
         field("zeitraum_bis", "DATE", True, L("Zeitraum bis", "Période au", "Periodo al", "Period to")),
         field("stunden", "NUMBER", True, L("Stunden", "Heures", "Ore", "Hours"))],
     "stufen": ["vg"]},
    {"key": "tagung", "sla": 5,
     "title": L("Tagung", "Conférence", "Convegno", "Conference"),
     "fields": [
         field("veranstaltung", "TEXT", True, L("Veranstaltung", "Manifestation", "Evento", "Event")),
         field("datum", "DATE", True, L("Datum", "Date", "Data", "Date")),
         field("kosten", "NUMBER", True, L("Kosten (CHF)", "Coûts (CHF)", "Costi (CHF)", "Costs (CHF)")),
         BEGRUENDUNG],
     "stufen": ["vg", "hr_bp"], "cost_var": "kosten"},
]

import os
REPUBLISH = os.environ.get("REPUBLISH") == "1"

ok = skipped = failed = 0
existing = {}
status, listing = call("GET", "/antragstyp")
if status == 200:
    existing = {a["key"]: a["id"] for a in listing}

for t in TYPES:
    status, created = call("POST", "/antragstyp", {"key": t["key"], "title": t["title"]})
    if status == 409 and not REPUBLISH:
        print(f"  {t['key']}: existiert bereits -> übersprungen (REPUBLISH=1 für neue Version)")
        skipped += 1
        continue
    if status == 409:
        at_id = existing.get(t["key"])
        if not at_id:
            print(f"  {t['key']}: existiert, aber nicht auffindbar -> übersprungen")
            failed += 1
            continue
    elif status not in (200, 201):
        print(f"  {t['key']}: FEHLER beim Anlegen ({status}): {created}")
        failed += 1
        continue
    else:
        at_id = created["id"]
    graph = chain_graph(t["stufen"], t.get("cost_var"), t.get("action"))
    status, version = call("POST", f"/antragstyp/{at_id}/versions", {
        "formDefinition": {"fields": t["fields"]},
        "sfActionBindings": {},
        "graphDefinition": graph,
    })
    if status not in (200, 201):
        print(f"  {t['key']}: FEHLER bei Version ({status}): {version}")
        failed += 1
        continue
    status, _ = call("POST", f"/antragstyp/versions/{version['id']}/publish")
    if status != 200:
        print(f"  {t['key']}: FEHLER beim Publish ({status})")
        failed += 1
        continue
    print(f"  {t['key']}: angelegt + publiziert (SLA {t['sla']} T, Kette {'->'.join(t['stufen'])}"
          + (", Kosten-XOR" if t.get("cost_var") else "")
          + (", SF-LMS-ACTION" if t.get("action") else "") + ")")
    ok += 1

print(f"== Fertig: {ok} publiziert, {skipped} übersprungen, {failed} Fehler ==")
sys.exit(1 if failed else 0)
PY
