/**
 * Pure Brutto→Netto-Simulationslogik für CH-Sozialabzüge (ADR-018, accepted 2026-06-12).
 *
 * Rein clientseitig: keine Backend-Calls, keine Persistenz, keine Personendaten —
 * BDR-001-konform. Die Sätze sind **Richtwerte 2025** aus dem Owner-Prototyp
 * (Prototyp-Inventur 2026-06-12) und liegen als versionierte Konstante `SAETZE_2025`
 * vor; ein Folgejahr ergänzt eine neue Konstante statt diese zu editieren.
 *
 * Dokumentierte Annahmen (siehe ADR-018):
 * - Das eingegebene Jahresbrutto versteht sich als 100%-Pensum-Basis. Das Pensum
 *   skaliert es linear: **effektives Brutto = brutto × pensum/100**. Alle Ceilings
 *   (ALV/NBU) und die BVG-Koordination werden auf dem EFFEKTIVEN Brutto angewendet.
 * - BVG: koordinierter Lohn = clamp(Jahresbrutto, 0…oberer Grenzbetrag) −
 *   Koordinationsabzug (mind. 0). Der AN-Anteil ist die HÄLFTE der Altersgutschrift
 *   auf dem koordinierten Lohn. Unter 25 (und über 70) kein BVG-Sparbeitrag.
 * - Quellensteuer (optional): grobe Schätzung je Kanton auf dem Netto NACH
 *   Sozialabzügen — keine Tarif-Tabellen, keine Rechtsverbindlichkeit.
 * - Rundung: kaufmännisch auf Rappen (2 Nachkommastellen) je Position.
 * - Defensive Eingaben: nicht-finite oder negative Beträge zählen als 0; das
 *   Pensum wird auf 0…100 % begrenzt.
 */

export const KANTONE = ['BE', 'ZH', 'GE', 'VD', 'BS'] as const;
export type Kanton = (typeof KANTONE)[number];

/** Richtwerte 2025 (Owner-Prototyp) — versioniert, niemals in-place fortschreiben. */
export const SAETZE_2025 = {
  jahr: 2025,
  /** AN-Abzug AHV/IV/EO in % des Jahresbruttos (ohne Ceiling). */
  ahvIvEoProzent: 5.3,
  /** AN-Abzug ALV in % — nur bis zum Jahreslohn-Ceiling. */
  alvProzent: 1.1,
  alvCeilingJahr: 148_200,
  /** AN-Abzug NBU in % — bis zum gleichen Ceiling. */
  nbuProzent: 1.0,
  nbuCeilingJahr: 148_200,
  /** AN-Abzug KTG in % des Jahresbruttos (ohne Ceiling). */
  ktgProzent: 0.5,
  bvg: {
    /** Oberer Grenzbetrag des versicherten Lohns. */
    obererGrenzbetragJahr: 88_200,
    /** Koordinationsabzug. */
    koordinationsabzugJahr: 25_725,
    /** Altersgutschriften in % des koordinierten Lohns; AN-Anteil = Hälfte. */
    altersgutschriften: [
      { vonAlter: 25, bisAlter: 34, prozent: 7 },
      { vonAlter: 35, bisAlter: 44, prozent: 10 },
      { vonAlter: 45, bisAlter: 54, prozent: 15 },
      { vonAlter: 55, bisAlter: 70, prozent: 18 },
    ],
  },
  /** QST-Schätzsatz je Kanton in % — auf dem Netto NACH Sozialabzügen. */
  quellensteuerProzent: { BE: 10, ZH: 9.5, GE: 12, VD: 11.5, BS: 11 } as Record<Kanton, number>,
} as const;

export interface LohnrechnerInput {
  /** Alter in ganzen Jahren (bestimmt die BVG-Altersgutschrift). */
  readonly alter: number;
  /** Kanton für die optionale Quellensteuer-Schätzung. */
  readonly kanton: Kanton;
  /** Jahresbrutto in CHF, bezogen auf ein 100%-Pensum (siehe Annahme oben). */
  readonly jahresbrutto: number;
  /** Beschäftigungsgrad in % — skaliert das Jahresbrutto linear (0…100). */
  readonly pensumProzent: number;
  /** Quellensteuer-Schätzung einrechnen? */
  readonly mitQuellensteuer: boolean;
}

/** Stabiler Schlüssel je Abzugsposition — zugleich i18n-Suffix `lohnrechner.position.<key>`. */
export type AbzugKey = 'ahvIvEo' | 'alv' | 'nbu' | 'ktg' | 'bvg' | 'qst';

export interface AbzugsPosition {
  readonly key: AbzugKey;
  /** Angewendeter Satz in % (informativ; beim BVG: AN-Anteil auf dem koordinierten Lohn). */
  readonly satzProzent: number;
  /** CHF/Jahr, auf Rappen gerundet. */
  readonly betragJahr: number;
  /** CHF/Monat = Jahr ÷ 12, auf Rappen gerundet. */
  readonly betragMonat: number;
}

export interface LohnrechnerErgebnis {
  /** Jahr der angewendeten Richtwerte (aus der versionierten Konstante). */
  readonly saetzeJahr: number;
  /** Effektives (pensum-skaliertes) Jahresbrutto. */
  readonly bruttoJahr: number;
  readonly bruttoMonat: number;
  /** Abzugspositionen in Anzeige-Reihenfolge; QST nur wenn aktiviert. */
  readonly abzuege: readonly AbzugsPosition[];
  /** BVG-Bemessungsgrundlage (0, wenn das Brutto unter der Koordination liegt). */
  readonly koordinierterLohn: number;
  readonly totalAbzuegeJahr: number;
  readonly totalAbzuegeMonat: number;
  /** Netto nach Sozialabzügen = QST-Bemessungsbasis. */
  readonly nettoVorQstJahr: number;
  readonly nettoJahr: number;
  readonly nettoMonat: number;
}

/** Kaufmännische Rundung auf Rappen; das EPSILON neutralisiert IEEE-754-Artefakte (x.xx5). */
export function rundeAufRappen(betrag: number): number {
  return Math.round((betrag + Number.EPSILON) * 100) / 100;
}

/**
 * Altersgutschrift in % des koordinierten Lohns (volle Gutschrift, AN zahlt die Hälfte).
 * Ausserhalb 25–70 (oder bei nicht-finitem Alter): 0 — kein BVG-Sparbeitrag.
 */
export function altersgutschriftProzent(alter: number): number {
  if (!Number.isFinite(alter)) {
    return 0;
  }
  const stufe = SAETZE_2025.bvg.altersgutschriften.find(
    s => alter >= s.vonAlter && alter <= s.bisAlter
  );
  return stufe?.prozent ?? 0;
}

/** Nicht-finite oder negative Beträge zählen als 0 (defensive Live-Eingabe). */
function alsBetrag(wert: number): number {
  return Number.isFinite(wert) && wert > 0 ? wert : 0;
}

/**
 * Berechnet alle AN-Abzüge und das Netto aus den `SAETZE_2025`-Richtwerten.
 * Pure Funktion: gleiche Eingabe → gleiches Ergebnis, keine Seiteneffekte.
 */
export function berechneAbzuege(input: LohnrechnerInput): LohnrechnerErgebnis {
  const s = SAETZE_2025;
  const pensum = Math.min(100, alsBetrag(input.pensumProzent));
  // Annahme (ADR-018): Eingabe = 100%-Basis, Pensum skaliert linear.
  const brutto = rundeAufRappen(alsBetrag(input.jahresbrutto) * (pensum / 100));

  const ahvIvEo = rundeAufRappen(brutto * (s.ahvIvEoProzent / 100));
  const alv = rundeAufRappen(Math.min(brutto, s.alvCeilingJahr) * (s.alvProzent / 100));
  const nbu = rundeAufRappen(Math.min(brutto, s.nbuCeilingJahr) * (s.nbuProzent / 100));
  const ktg = rundeAufRappen(brutto * (s.ktgProzent / 100));

  const koordinierterLohn = Math.max(
    0,
    Math.min(brutto, s.bvg.obererGrenzbetragJahr) - s.bvg.koordinationsabzugJahr
  );
  const gutschriftProzent = altersgutschriftProzent(input.alter);
  /** AN-Anteil = Hälfte der Altersgutschrift auf dem koordinierten Lohn. */
  const bvgSatzAn = gutschriftProzent / 2;
  const bvg = rundeAufRappen(koordinierterLohn * (bvgSatzAn / 100));

  const position = (key: AbzugKey, satzProzent: number, betragJahr: number): AbzugsPosition => ({
    key,
    satzProzent,
    betragJahr,
    betragMonat: rundeAufRappen(betragJahr / 12),
  });

  const abzuege: AbzugsPosition[] = [
    position('ahvIvEo', s.ahvIvEoProzent, ahvIvEo),
    position('alv', s.alvProzent, alv),
    position('nbu', s.nbuProzent, nbu),
    position('ktg', s.ktgProzent, ktg),
    position('bvg', bvgSatzAn, bvg),
  ];

  const totalSozial = rundeAufRappen(ahvIvEo + alv + nbu + ktg + bvg);
  const nettoVorQst = rundeAufRappen(brutto - totalSozial);

  if (input.mitQuellensteuer) {
    const qstSatz = s.quellensteuerProzent[input.kanton];
    abzuege.push(position('qst', qstSatz, rundeAufRappen(nettoVorQst * (qstSatz / 100))));
  }

  const totalAbzuege = rundeAufRappen(abzuege.reduce((sum, p) => sum + p.betragJahr, 0));
  const netto = rundeAufRappen(brutto - totalAbzuege);

  return {
    saetzeJahr: s.jahr,
    bruttoJahr: brutto,
    bruttoMonat: rundeAufRappen(brutto / 12),
    abzuege,
    koordinierterLohn,
    totalAbzuegeJahr: totalAbzuege,
    totalAbzuegeMonat: rundeAufRappen(totalAbzuege / 12),
    nettoVorQstJahr: nettoVorQst,
    nettoJahr: netto,
    nettoMonat: rundeAufRappen(netto / 12),
  };
}
