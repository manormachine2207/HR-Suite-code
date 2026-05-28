#!/usr/bin/env bash
# check_i18n_coverage.sh — verify all locale files share the same key tree.
#
# Locale files live under frontend/src/assets/i18n/. We use de.json as the
# reference (because the project default locale is 'de' per BDR-005) and
# compare the recursive key set of every other locale against it.
#
# Exit codes:
#   0 — all locales have identical key trees
#   1 — at least one locale has extra or missing keys
#   2 — environment problem (python3 missing, locales dir missing)
#
# Per BDR-005 (Mehrsprachigkeit DE/FR/IT/EN) + CLAUDE.md i18n-Sync-Disziplin.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCALE_DIR="${ROOT}/frontend/src/assets/i18n"
REFERENCE_LOCALE="de"

if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: python3 not on PATH" >&2
  exit 2
fi

if [ ! -d "${LOCALE_DIR}" ]; then
  echo "ERROR: locale dir not found: ${LOCALE_DIR}" >&2
  exit 2
fi

export LOCALE_DIR
export REFERENCE_LOCALE

python3 - <<'PYEOF'
import json
import os
import sys

LOCALE_DIR = os.environ["LOCALE_DIR"]
REFERENCE = os.environ.get("REFERENCE_LOCALE", "de")

def flatten(prefix, obj, out):
    if isinstance(obj, dict):
        for k, v in obj.items():
            flatten(f"{prefix}.{k}" if prefix else k, v, out)
    else:
        out.add(prefix)

def load(lang):
    path = os.path.join(LOCALE_DIR, f"{lang}.json")
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)

ref_keys = set()
flatten("", load(REFERENCE), ref_keys)

failed = False
for lang in ("fr", "it", "en"):
    lang_keys = set()
    flatten("", load(lang), lang_keys)
    missing = ref_keys - lang_keys
    extra = lang_keys - ref_keys
    if missing or extra:
        failed = True
        print(f"FAIL: {lang}.json drift vs {REFERENCE}.json", file=sys.stderr)
        if missing:
            print(f"  missing in {lang}: {sorted(missing)[:10]}{' ...' if len(missing) > 10 else ''}",
                  file=sys.stderr)
        if extra:
            print(f"  extra in {lang}: {sorted(extra)[:10]}{' ...' if len(extra) > 10 else ''}",
                  file=sys.stderr)
    else:
        print(f"OK: {lang}.json keys match {REFERENCE}.json ({len(ref_keys)} keys)")

sys.exit(1 if failed else 0)
PYEOF
