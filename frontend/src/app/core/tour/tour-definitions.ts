/**
 * Guided-tour step data + first-visit flag (ADR-015 Ebene 3) — pure module.
 *
 * The steps are plain data so the contract is unit-testable without driver.js:
 * - Targets are addressed EXCLUSIVELY via `data-tour="…"` attributes that the
 *   feature templates carry for this purpose — never via styling classes, so a
 *   CSS refactor cannot silently break the tours (ADR-015 "Pflege-Artefakte").
 * - Texts are i18n keys (BDR-005); TourService resolves them via TranslateService.
 * - `section` declares which builder step must be active before the element is
 *   visible; TourService asks the host component to switch (zoneless-safe).
 *
 * MAINTENANCE CHECKLIST (ADR-015): when renaming/moving one of the highlighted
 * UI elements, update the data-tour attribute AND the matching step here AND
 * the tour.* i18n texts in assets/i18n/{de,fr,it,en}.json.
 */

export type BuilderSection = 'form' | 'flow' | 'publish';

export interface TourStepDef {
  /** `[data-tour="…"]` selector; undefined renders a centered (element-less) popover. */
  readonly selector?: string;
  /** i18n key of the popover title. */
  readonly titleKey: string;
  /** i18n key of the popover body. */
  readonly descriptionKey: string;
  /** Builder section that must be active for this step (builder tour only). */
  readonly section?: BuilderSection;
}

/** localStorage key marking the dashboard tour as seen (auto-start only once). */
export const DASHBOARD_TOUR_SEEN_KEY = 'hrsuite.tour.dashboard.seen';

/** Dashboard tour: welcome → search → favorite star → module card → header help. */
export function dashboardTourSteps(): TourStepDef[] {
  return [
    {
      titleKey: 'tour.dashboard.welcome.title',
      descriptionKey: 'tour.dashboard.welcome.desc',
    },
    {
      selector: '[data-tour="dashboard-search"]',
      titleKey: 'tour.dashboard.search.title',
      descriptionKey: 'tour.dashboard.search.desc',
    },
    {
      selector: '[data-tour="dashboard-favorite"]',
      titleKey: 'tour.dashboard.favorite.title',
      descriptionKey: 'tour.dashboard.favorite.desc',
    },
    {
      selector: '[data-tour="dashboard-card"]',
      titleKey: 'tour.dashboard.card.title',
      descriptionKey: 'tour.dashboard.card.desc',
    },
    {
      selector: '[data-tour="header-help"]',
      titleKey: 'tour.dashboard.help.title',
      descriptionKey: 'tour.dashboard.help.desc',
    },
  ];
}

/**
 * Builder tour: stepper → fields toolbar → field card → flow section (switches
 * via the host) → palette → canvas → publish summary (switches again).
 */
export function builderTourSteps(): TourStepDef[] {
  return [
    {
      selector: '[data-tour="builder-stepper"]',
      titleKey: 'tour.builder.stepper.title',
      descriptionKey: 'tour.builder.stepper.desc',
      section: 'form',
    },
    {
      selector: '[data-tour="builder-toolbar"]',
      titleKey: 'tour.builder.toolbar.title',
      descriptionKey: 'tour.builder.toolbar.desc',
      section: 'form',
    },
    {
      selector: '[data-tour="builder-fieldcard"]',
      titleKey: 'tour.builder.fieldcard.title',
      descriptionKey: 'tour.builder.fieldcard.desc',
      section: 'form',
    },
    {
      selector: '[data-tour="builder-flow"]',
      titleKey: 'tour.builder.flow.title',
      descriptionKey: 'tour.builder.flow.desc',
      section: 'flow',
    },
    {
      selector: '[data-tour="flow-palette"]',
      titleKey: 'tour.builder.palette.title',
      descriptionKey: 'tour.builder.palette.desc',
      section: 'flow',
    },
    {
      selector: '[data-tour="flow-canvas"]',
      titleKey: 'tour.builder.canvas.title',
      descriptionKey: 'tour.builder.canvas.desc',
      section: 'flow',
    },
    {
      selector: '[data-tour="publish-summary"]',
      titleKey: 'tour.builder.publish.title',
      descriptionKey: 'tour.builder.publish.desc',
      section: 'publish',
    },
  ];
}

/** Storage abstraction (localStorage-shaped) so the flag logic stays pure/testable. */
export interface KeyValueStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

/**
 * True only while the seen flag is absent. A throwing storage (private mode,
 * blocked cookies) deliberately yields false: better no auto-tour than an
 * auto-tour on every single visit.
 */
export function shouldAutoStartDashboardTour(storage: KeyValueStorage): boolean {
  try {
    return storage.getItem(DASHBOARD_TOUR_SEEN_KEY) === null;
  } catch {
    return false;
  }
}

/** Persists the seen flag (ISO timestamp for debuggability); silent on quota/denial. */
export function markDashboardTourSeen(storage: KeyValueStorage): void {
  try {
    storage.setItem(DASHBOARD_TOUR_SEEN_KEY, new Date().toISOString());
  } catch {
    // Storage unavailable — the tour will auto-offer again next visit, acceptable.
  }
}
