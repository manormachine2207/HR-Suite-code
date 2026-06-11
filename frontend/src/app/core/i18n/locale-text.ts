/**
 * Pure helpers for rendering backend i18n content (jsonb LocaleMaps) and dates in the
 * active UI language (BDR-005). Components resolve via TranslateService.currentLang and
 * re-resolve on onLangChange so a language switch updates rendered titles in place.
 */

/** Locale-id per UI language for Angular's DatePipe (registered in app.config.ts). */
const DATE_LOCALES: Record<string, string> = {
  de: 'de-CH',
  fr: 'fr-CH',
  it: 'it-CH',
  en: 'en-CH',
};

export function dateLocaleFor(lang: string): string {
  return DATE_LOCALES[lang] ?? DATE_LOCALES['de'];
}

/**
 * Resolves a backend LocaleMap with the fallback chain:
 * active language → de → first non-empty entry → technical fallback (usually the key).
 */
export function resolveLocaleText(
  map: Partial<Record<string, string>> | undefined | null,
  lang: string,
  fallback: string
): string {
  if (!map) {
    return fallback;
  }
  const direct = map[lang];
  if (direct) {
    return direct;
  }
  const de = map['de'];
  if (de) {
    return de;
  }
  const first = Object.values(map).find(v => !!v);
  return first ?? fallback;
}
