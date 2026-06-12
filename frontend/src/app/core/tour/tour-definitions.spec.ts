import { describe, it, expect } from 'vitest';

import {
  DASHBOARD_TOUR_SEEN_KEY,
  KeyValueStorage,
  TourStepDef,
  builderTourSteps,
  dashboardTourSteps,
  markDashboardTourSeen,
  shouldAutoStartDashboardTour,
} from './tour-definitions';

/**
 * Pure-function spec for the guided-tour step data + first-visit flag (ADR-015
 * Ebene 3). The step definitions are plain data so the contract — stable
 * data-tour selectors, i18n keys, builder section choreography — is testable
 * without driver.js, TranslateService or the DOM (Tenet 14).
 */

/** Minimal in-memory KeyValueStorage test double. */
function memoryStorage(initial: Record<string, string> = {}): KeyValueStorage {
  const map = new Map(Object.entries(initial));
  return {
    getItem: (k: string) => map.get(k) ?? null,
    setItem: (k: string, v: string) => void map.set(k, v),
  };
}

const selectorsOf = (steps: TourStepDef[]): string[] =>
  steps.filter(s => s.selector !== undefined).map(s => s.selector!);

describe('dashboardTourSteps', () => {
  it('has 5 steps: welcome (no element) → search → favorite → card → header help', () => {
    const steps = dashboardTourSteps();
    expect(steps).toHaveLength(5);
    // Welcome step is a centered modal — no target element.
    expect(steps[0].selector).toBeUndefined();
    expect(selectorsOf(steps)).toEqual([
      '[data-tour="dashboard-search"]',
      '[data-tour="dashboard-favorite"]',
      '[data-tour="dashboard-card"]',
      '[data-tour="header-help"]',
    ]);
  });

  it('only uses data-tour selectors (never fragile CSS classes) and unique i18n keys', () => {
    const steps = dashboardTourSteps();
    for (const sel of selectorsOf(steps)) {
      expect(sel).toMatch(/^\[data-tour="[a-z-]+"\]$/);
    }
    const keys = steps.flatMap(s => [s.titleKey, s.descriptionKey]);
    expect(new Set(keys).size).toBe(keys.length);
    for (const k of keys) {
      expect(k).toMatch(/^tour\.dashboard\./);
    }
  });

  it('declares no builder sections (dashboard needs no section switching)', () => {
    expect(dashboardTourSteps().every(s => s.section === undefined)).toBe(true);
  });
});

describe('builderTourSteps', () => {
  it('walks stepper → toolbar → field card → flow → palette → canvas → publish summary', () => {
    const steps = builderTourSteps();
    expect(steps).toHaveLength(7);
    expect(selectorsOf(steps)).toEqual([
      '[data-tour="builder-stepper"]',
      '[data-tour="builder-toolbar"]',
      '[data-tour="builder-fieldcard"]',
      '[data-tour="builder-flow"]',
      '[data-tour="flow-palette"]',
      '[data-tour="flow-canvas"]',
      '[data-tour="publish-summary"]',
    ]);
  });

  it('declares the section each step needs (form → flow → publish, monotonic)', () => {
    const sections = builderTourSteps().map(s => s.section);
    expect(sections).toEqual(['form', 'form', 'form', 'flow', 'flow', 'flow', 'publish']);
  });

  it('uses tour.builder.* i18n keys throughout', () => {
    for (const s of builderTourSteps()) {
      expect(s.titleKey).toMatch(/^tour\.builder\./);
      expect(s.descriptionKey).toMatch(/^tour\.builder\./);
    }
  });
});

describe('dashboard first-visit flag', () => {
  it('auto-starts only while the seen flag is absent', () => {
    const storage = memoryStorage();
    expect(shouldAutoStartDashboardTour(storage)).toBe(true);

    markDashboardTourSeen(storage);

    expect(storage.getItem(DASHBOARD_TOUR_SEEN_KEY)).not.toBeNull();
    expect(shouldAutoStartDashboardTour(storage)).toBe(false);
  });

  it('never auto-starts when storage throws (private mode) — and marking stays silent', () => {
    const broken: KeyValueStorage = {
      getItem: () => { throw new Error('denied'); },
      setItem: () => { throw new Error('denied'); },
    };
    expect(shouldAutoStartDashboardTour(broken)).toBe(false);
    expect(() => markDashboardTourSeen(broken)).not.toThrow();
  });

  it('uses the agreed localStorage key', () => {
    expect(DASHBOARD_TOUR_SEEN_KEY).toBe('hrsuite.tour.dashboard.seen');
  });
});
