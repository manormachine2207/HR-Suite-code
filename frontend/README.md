# HR-Suite Frontend

Angular 21 + [Oblique 15.3](https://oblique.bit.admin.ch) (Swiss Confederation CI/CD, maintained by FOITT/BIT) frontend workspace for the HR-Suite request platform.

**For the OSS overview, repository layout, and quick-start (`docker compose up`), see the [repository root README](../README.md).**

**For developer conventions and AI-coding rules, see [../CLAUDE.md](../CLAUDE.md) and [../AI_CONTEXT.md](../AI_CONTEXT.md).**

## Architectural anchors

- [BDR-008 — UI follows CI Bund + Oblique](https://github.com/manormachine2207/HR-Suite-notes/blob/main/Entscheidungen/BDR/BDR-008-UI-folgt-CI-Bund-und-Oblique.md)
- [ADR-007 — Frontend stack: Angular 21 + Oblique 15.3](https://github.com/manormachine2207/HR-Suite-notes/blob/main/Entscheidungen/ADR-007-Frontend-Stack-Switch-Angular-Oblique.md)
- [SDR-003 — Accessibility: WCAG 2.1 AA + eCH-0059](https://github.com/manormachine2207/HR-Suite-notes/blob/main/Entscheidungen/SDR/SDR-003-Accessibility-WCAG-eCH-0059.md)
- [BDR-007 — Container-First / Compose-Helm-Symmetrie](https://github.com/manormachine2207/HR-Suite-notes/blob/main/Entscheidungen/BDR/BDR-007-Container-First-Compose-Helm-Symmetrie.md)

## Common commands

These commands work with a local Node 22 install. Per [BDR-007](https://github.com/manormachine2207/HR-Suite-notes/blob/main/Entscheidungen/BDR/BDR-007-Container-First-Compose-Helm-Symmetrie.md) (Container-First), they also run inside Docker — useful if you don't have Node installed locally:

```bash
docker run --rm -v "$PWD":/work -w /work --user "$(id -u):$(id -g)" -e HOME=/tmp node:22-alpine <cmd>
```

| Action | Command |
|---|---|
| Install deps | `npm ci` |
| Dev server (port 4200) | `npx ng serve` |
| Unit tests (Vitest) | `npx ng test --watch=false` |
| Production build | `npx ng build --configuration=production` |
| i18n key-coverage lint | `../scripts/check_i18n_coverage.sh` |

For a containerised production preview (nginx on port 8080), use the repository-level `docker compose up -d` — see [../README.md](../README.md).

## Locale files

The HR-Suite frontend uses **two locale-file sources** per language, merged at runtime by a custom `TranslateLoader`:

| Path | Source | Key style | Examples |
|---|---|---|---|
| `src/assets/i18n/<lang>.json` | This repo | nested (`app.title`, `nav.home`) | application strings |
| `assets/i18n/oblique-<lang>.json` (deployed from `node_modules/@oblique/oblique/assets/`) | Oblique 15.3 | flat (`i18n.oblique.accessibility-statement.link`) | Oblique component strings (master-layout, footer, dialogs, notifications, form-validation messages) |

Merge happens in `src/app/core/i18n/multi-translate-http-loader.ts`. Both styles coexist in the merged object — ngx-translate handles both lookup patterns.

When you add a new string to `<lang>.json`, run `../scripts/check_i18n_coverage.sh` from the repo root to ensure all four locales (de/fr/it/en) have identical key trees per [BDR-005](https://github.com/manormachine2207/HR-Suite-notes/blob/main/Entscheidungen/BDR/BDR-005-Mehrsprachigkeit-DE-FR-IT-EN.md).

## Out of scope (tracked in HR-Suite-notes roadmap)

- ESLint setup (`ng add @angular-eslint/schematics`)
- Bundle-size budget re-evaluation
- `runtime.json` override via Compose volume / K8s ConfigMap
- Real OIDC login flow + AuthGuard (kommt mit `identity-sp`-Cut)
- Backend API integration (kommt mit `core/tenant`-Cut)
