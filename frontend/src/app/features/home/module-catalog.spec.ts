import { describe, it, expect } from 'vitest';

import { MODULE_CATALOG, ModuleCardDef, filterModules } from './module-catalog';

/**
 * Pure-function spec for the module-dashboard catalog (ADR-014). The filter is fed a
 * translateFn so the search semantics ("matches translated title + description,
 * case-insensitive") are testable without TranslateService/zone — per Tenet 14 the
 * tests defend the decision, not the wiring.
 */
const T: Record<string, string> = {
  'home.modules.antragstypen.title': 'Antragstypen',
  'home.modules.antragstypen.description':
    'Antragstypen modellieren: Formular, Flow-Canvas und Veröffentlichung.',
  'home.modules.antraege.title': 'Meine Anträge',
  'home.modules.antraege.description': 'Eigene Anträge stellen und ihren Status verfolgen.',
  'home.modules.aufgaben.title': 'Aufgaben',
  'home.modules.aufgaben.description': 'Offene Aufgaben prüfen und Anträge entscheiden.',
  'home.modules.hilfe.title': 'Hilfe',
  'home.modules.hilfe.description': 'Anleitungen und geführte Touren.',
  'home.modules.lohnrechner.title': 'Lohnrechner',
  'home.modules.lohnrechner.description':
    'Brutto-Netto-Simulation mit Richtwerten 2025 — rein im Browser, ohne Speicherung.',
};

const translateFn = (key: string): string => T[key] ?? key;

const ids = (defs: readonly ModuleCardDef[]): string[] => defs.map(d => d.id);

describe('MODULE_CATALOG', () => {
  it('declares the seven dashboard modules with route + roles', () => {
    expect(ids(MODULE_CATALOG)).toEqual([
      'antragstypen', 'antraege', 'antragKatalog', 'aufgaben', 'lohnrechner', 'hilfe', 'plattform',
    ]);
    const all = new Map(MODULE_CATALOG.map(d => [d.id, d]));
    expect(all.get('plattform')!.route).toBe('/plattform');
    expect(all.get('plattform')!.roles).toEqual(['platform-admin']);
    const byId = new Map(MODULE_CATALOG.map(d => [d.id, d]));
    expect(byId.get('antragstypen')!.route).toBe('/antragstypen');
    expect(byId.get('antragstypen')!.roles).toEqual(['hr-designer', 'tenant-admin']);
    expect(byId.get('antraege')!.route).toBe('/antraege');
    expect(byId.get('antraege')!.roles).toEqual([]);
    expect(byId.get('aufgaben')!.route).toBe('/aufgaben');
    expect(byId.get('aufgaben')!.roles).toEqual(['hr-reviewer', 'tenant-admin']);
    // ADR-015: help center is for everyone (roles []) behind the ?-circle icon.
    expect(byId.get('hilfe')!.route).toBe('/hilfe');
    expect(byId.get('hilfe')!.roles).toEqual([]);
    expect(byId.get('hilfe')!.icon).toBe('question_circle');
  });

  it('Lohnrechner card (ADR-018): for everyone, calculator icon, "Simulation" maturity badge', () => {
    const lohnrechner = MODULE_CATALOG.find(d => d.id === 'lohnrechner')!;
    expect(lohnrechner.route).toBe('/lohnrechner');
    expect(lohnrechner.roles).toEqual([]);
    expect(lohnrechner.icon).toBe('calculator');
    expect(lohnrechner.badgeKey).toBe('home.modules.lohnrechner.badge');
    // All other cards keep the default product tag (no badge).
    for (const def of MODULE_CATALOG.filter(d => d.id !== 'lohnrechner')) {
      expect(def.badgeKey, def.id).toBeUndefined();
    }
  });

  it('every module carries icon + i18n keys in the home.modules.* block', () => {
    for (const def of MODULE_CATALOG) {
      expect(def.icon.length).toBeGreaterThan(0);
      expect(def.titleKey).toBe(`home.modules.${def.id}.title`);
      expect(def.descriptionKey).toBe(`home.modules.${def.id}.description`);
    }
  });
});

describe('filterModules', () => {
  it('returns all modules for an empty search without favorites-only', () => {
    expect(filterModules(MODULE_CATALOG, '', false, [], translateFn)).toHaveLength(7);
  });

  it('treats whitespace-only search as empty', () => {
    expect(filterModules(MODULE_CATALOG, '   ', false, [], translateFn)).toHaveLength(7);
  });

  it('matches the translated title case-insensitively', () => {
    // "Aufgaben" appears in the aufgaben title (and its own description) only.
    expect(ids(filterModules(MODULE_CATALOG, 'aufGABEN', false, [], translateFn)))
      .toEqual(['aufgaben']);
  });

  it('matches the translated description too', () => {
    expect(ids(filterModules(MODULE_CATALOG, 'flow-canvas', false, [], translateFn)))
      .toEqual(['antragstypen']);
  });

  it('finds nothing for a term absent from titles and descriptions', () => {
    expect(filterModules(MODULE_CATALOG, 'lohnfindung', false, [], translateFn)).toEqual([]);
  });

  it('keeps only favorites when the toggle is on', () => {
    expect(ids(filterModules(MODULE_CATALOG, '', true, ['antraege'], translateFn)))
      .toEqual(['antraege']);
  });

  it('favorites toggle with no favorites yields an empty list', () => {
    expect(filterModules(MODULE_CATALOG, '', true, [], translateFn)).toEqual([]);
  });

  it('combines search and favorites (intersection)', () => {
    expect(ids(filterModules(MODULE_CATALOG, 'anträge', true, ['antraege', 'aufgaben'], translateFn)))
      .toEqual(['antraege', 'aufgaben']); // "Anträge" in antraege-title and aufgaben-description
  });

  it('search runs over the active language translation (translateFn is injected)', () => {
    const en = (key: string): string =>
      key === 'home.modules.antraege.title' ? 'My Requests' : key;
    expect(ids(filterModules(MODULE_CATALOG, 'requests', false, [], en))).toEqual(['antraege']);
    expect(ids(filterModules(MODULE_CATALOG, 'anträge', false, [], en))).toEqual([]);
  });
});

describe('MODULE_CATALOG — antragKatalog entry (ADR-021)', () => {
  it('includes a "new request" entry routing to /antraege/neu', () => {
    expect(MODULE_CATALOG.some(m => m.route === '/antraege/neu')).toBe(true);
  });

  it('antragKatalog card: for everyone (roles []), correct i18n keys, placed near antraege', () => {
    const entry = MODULE_CATALOG.find(d => d.id === 'antragKatalog');
    expect(entry).toBeDefined();
    expect(entry!.route).toBe('/antraege/neu');
    expect(entry!.roles).toEqual([]);
    expect(entry!.titleKey).toBe('home.modules.antragKatalog.title');
    expect(entry!.descriptionKey).toBe('home.modules.antragKatalog.description');
    expect(entry!.badgeKey).toBeUndefined();
  });
});
