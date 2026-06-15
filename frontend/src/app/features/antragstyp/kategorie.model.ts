/** Feste Kategorie-Keys (spiegelt das Backend-Enum AntragsKategorie, ADR-021).
 *  Labels via i18n-Key `antragstyp.category.<KEY>`. */
export const ANTRAGS_KATEGORIEN = ['ABSENCE', 'FINANCE', 'EMPLOYMENT', 'DEVELOPMENT', 'OTHER'] as const;
export type AntragsKategorie = typeof ANTRAGS_KATEGORIEN[number];

/** Backend liefert null für unkategorisierte Typen → im UI als OTHER behandeln. */
export function kategorieOf(value: string | null | undefined): AntragsKategorie {
  return (value && (ANTRAGS_KATEGORIEN as readonly string[]).includes(value))
    ? value as AntragsKategorie : 'OTHER';
}
