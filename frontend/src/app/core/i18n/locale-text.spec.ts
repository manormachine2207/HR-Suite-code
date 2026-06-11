import { describe, it, expect } from 'vitest';
import { resolveLocaleText, dateLocaleFor } from './locale-text';

describe('resolveLocaleText', () => {
  const map = { de: 'Urlaub', fr: 'Vacances' };

  it('returns the active-language entry when present', () => {
    expect(resolveLocaleText(map, 'fr', 'urlaubsantrag')).toBe('Vacances');
  });

  it('falls back to de when the active language is missing', () => {
    expect(resolveLocaleText(map, 'it', 'urlaubsantrag')).toBe('Urlaub');
  });

  it('falls back to the first available language when de is missing too', () => {
    expect(resolveLocaleText({ en: 'Leave' }, 'fr', 'urlaubsantrag')).toBe('Leave');
  });

  it('falls back to the key when the map is empty or undefined', () => {
    expect(resolveLocaleText({}, 'de', 'urlaubsantrag')).toBe('urlaubsantrag');
    expect(resolveLocaleText(undefined, 'de', 'urlaubsantrag')).toBe('urlaubsantrag');
  });

  it('skips empty-string entries in the fallback chain', () => {
    expect(resolveLocaleText({ de: '', it: 'Vacanze' }, 'de', 'k')).toBe('Vacanze');
  });
});

describe('dateLocaleFor', () => {
  it('maps the UI languages to Swiss Angular locale ids', () => {
    expect(dateLocaleFor('de')).toBe('de-CH');
    expect(dateLocaleFor('fr')).toBe('fr-CH');
    expect(dateLocaleFor('it')).toBe('it-CH');
    expect(dateLocaleFor('en')).toBe('en-CH');
  });

  it('falls back to de-CH for unknown languages', () => {
    expect(dateLocaleFor('rm')).toBe('de-CH');
    expect(dateLocaleFor('')).toBe('de-CH');
  });
});
