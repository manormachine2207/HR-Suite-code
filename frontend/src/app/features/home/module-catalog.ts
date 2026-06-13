/**
 * Declarative catalog for the module dashboard on '/' (ADR-014, FATIP pattern).
 *
 * Pure data + pure filter function — no Angular imports — so the dashboard semantics
 * (search over the *translated* title/description, favorites intersection) are unit-
 * testable without TranslateService. The component layers Signals on top.
 *
 * `roles` is informational only: cards are never hidden by role, because the dev
 * token carries a single role and the real role set only arrives with identity-sp.
 * Non-empty roles render as chips on the card (see home.component.html).
 */
export interface ModuleCardDef {
  /** Stable id — also the localStorage favorites key entry. */
  readonly id: string;
  /** Oblique icon name (registered app-wide by provideObliqueConfiguration). */
  readonly icon: string;
  /** i18n key of the card title (home.modules.<id>.title). */
  readonly titleKey: string;
  /** i18n key of the 1-2 sentence description (home.modules.<id>.description). */
  readonly descriptionKey: string;
  /** Router target of the whole-card link. */
  readonly route: string;
  /** Roles that use this module; [] = everyone. Shown as chips, never used to hide. */
  readonly roles: readonly string[];
  /**
   * Optional i18n key of a maturity badge that replaces the default footer tag
   * ('home.tag'). Used e.g. by the Lohnrechner to flag itself as "Simulation"
   * (ADR-018) — absent = regular module.
   */
  readonly badgeKey?: string;
}

export const MODULE_CATALOG: readonly ModuleCardDef[] = [
  {
    id: 'antragstypen',
    icon: 'pencil',
    titleKey: 'home.modules.antragstypen.title',
    descriptionKey: 'home.modules.antragstypen.description',
    route: '/antragstypen',
    roles: ['hr-designer', 'tenant-admin'],
  },
  {
    id: 'antraege',
    icon: 'file_text',
    titleKey: 'home.modules.antraege.title',
    descriptionKey: 'home.modules.antraege.description',
    route: '/antraege',
    roles: [],
  },
  {
    id: 'aufgaben',
    icon: 'inbox',
    titleKey: 'home.modules.aufgaben.title',
    descriptionKey: 'home.modules.aufgaben.description',
    route: '/aufgaben',
    roles: ['hr-reviewer', 'tenant-admin'],
  },
  {
    // Lohnrechner (ADR-018): client-side simulation for everyone — the badge marks
    // its maturity level "Simulation" (Richtwerte 2025, no legal validity).
    id: 'lohnrechner',
    icon: 'calculator',
    titleKey: 'home.modules.lohnrechner.title',
    descriptionKey: 'home.modules.lohnrechner.description',
    route: '/lohnrechner',
    roles: [],
    badgeKey: 'home.modules.lohnrechner.badge',
  },
  {
    // Help center (ADR-015 Ebene 2) — for everyone, hence roles: [].
    id: 'hilfe',
    icon: 'question_circle',
    titleKey: 'home.modules.hilfe.title',
    descriptionKey: 'home.modules.hilfe.description',
    route: '/hilfe',
    roles: [],
  },
  {
    // Plattform-Management (ADR-019): platform-admin only. The tile is not
    // role-hidden (see note above); the page enforces via the 403 → operators-only state.
    id: 'plattform',
    icon: 'settings',
    titleKey: 'home.modules.plattform.title',
    descriptionKey: 'home.modules.plattform.description',
    route: '/plattform',
    roles: ['platform-admin'],
  },
];

export type TranslateFn = (key: string) => string;

/**
 * Filters the catalog for the dashboard view.
 *
 * - `searchText` matches case-insensitively against the translated title AND
 *   description (trimmed; whitespace-only = no filter). Translation happens through
 *   the injected `translateFn`, so a language switch re-filters with the new texts.
 * - `onlyFavorites` keeps cards whose id is in `favoriteIds` (intersection with search).
 */
export function filterModules(
  defs: readonly ModuleCardDef[],
  searchText: string,
  onlyFavorites: boolean,
  favoriteIds: readonly string[],
  translateFn: TranslateFn
): ModuleCardDef[] {
  const needle = searchText.trim().toLowerCase();
  return defs.filter(def => {
    if (onlyFavorites && !favoriteIds.includes(def.id)) {
      return false;
    }
    if (needle.length === 0) {
      return true;
    }
    const haystack =
      `${translateFn(def.titleKey)} ${translateFn(def.descriptionKey)}`.toLowerCase();
    return haystack.includes(needle);
  });
}
