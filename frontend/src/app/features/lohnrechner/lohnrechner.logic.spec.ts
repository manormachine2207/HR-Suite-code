import { describe, it, expect } from 'vitest';

import {
  AbzugKey,
  KANTONE,
  LohnrechnerInput,
  SAETZE_2025,
  altersgutschriftProzent,
  berechneAbzuege,
  rundeAufRappen,
} from './lohnrechner.logic';

/**
 * Handgerechnete Fixtures für die ADR-018-Simulation (Richtwerte 2025) — die
 * Erwartungswerte sind unabhängig vom Code auf Papier nachgerechnet, damit der
 * Test die Entscheidung verteidigt und nicht die Implementierung abschreibt
 * (Tenet 14). Rundung: kaufmännisch auf Rappen je Position.
 */

function input(overrides: Partial<LohnrechnerInput>): LohnrechnerInput {
  return {
    alter: 40,
    kanton: 'BE',
    jahresbrutto: 120_000,
    pensumProzent: 100,
    mitQuellensteuer: false,
    ...overrides,
  };
}

function betragJahr(ergebnis: ReturnType<typeof berechneAbzuege>, key: AbzugKey): number | undefined {
  return ergebnis.abzuege.find(p => p.key === key)?.betragJahr;
}

describe('berechneAbzuege — handgerechnete Fixtures (SAETZE_2025)', () => {
  it('Standardfall: Alter 40, BE, 120 000, Pensum 100 %, mit QST', () => {
    // Hand: AHV/IV/EO 5.3% = 6360.00 · ALV 1.1% = 1320.00 · NBU 1.0% = 1200.00
    //       KTG 0.5% = 600.00
    //       BVG: koord = min(120000, 88200) − 25725 = 62475; 35–44 → 10%; AN 5% = 3123.75
    //       Sozial-Total = 12603.75 → Netto vor QST = 107396.25
    //       QST BE 10% = 10739.625 → 10739.63 (Rappenrundung)
    //       Total = 23343.38 → Netto 96656.62 / Monat 8054.72 (96656.62 ÷ 12 = 8054.7183…)
    const e = berechneAbzuege(input({ mitQuellensteuer: true }));

    expect(e.saetzeJahr).toBe(2025);
    expect(e.bruttoJahr).toBe(120_000);
    expect(e.bruttoMonat).toBe(10_000);
    expect(betragJahr(e, 'ahvIvEo')).toBe(6_360);
    expect(betragJahr(e, 'alv')).toBe(1_320);
    expect(betragJahr(e, 'nbu')).toBe(1_200);
    expect(betragJahr(e, 'ktg')).toBe(600);
    expect(e.koordinierterLohn).toBe(62_475);
    expect(betragJahr(e, 'bvg')).toBe(3_123.75);
    expect(e.nettoVorQstJahr).toBe(107_396.25);
    expect(betragJahr(e, 'qst')).toBe(10_739.63);
    expect(e.totalAbzuegeJahr).toBe(23_343.38);
    expect(e.nettoJahr).toBe(96_656.62);
    expect(e.nettoMonat).toBe(8_054.72);
  });

  it('Brutto über dem ALV-/NBU-Ceiling: Alter 50, ZH, 200 000, ohne QST', () => {
    // Hand: AHV 5.3% × 200000 = 10600.00 (kein Ceiling)
    //       ALV 1.1% × 148200 = 1630.20 · NBU 1.0% × 148200 = 1482.00 (Ceiling greift)
    //       KTG 0.5% × 200000 = 1000.00
    //       BVG: koord = 88200 − 25725 = 62475 (oberer Grenzbetrag); 45–54 → 15%;
    //            AN 7.5% = 4685.625 → 4685.63
    //       Total = 19397.83 → Netto 180602.17 / Monat 15050.18
    const e = berechneAbzuege(input({ alter: 50, kanton: 'ZH', jahresbrutto: 200_000 }));

    expect(betragJahr(e, 'ahvIvEo')).toBe(10_600);
    expect(betragJahr(e, 'alv')).toBe(1_630.2);
    expect(betragJahr(e, 'nbu')).toBe(1_482);
    expect(betragJahr(e, 'ktg')).toBe(1_000);
    expect(e.koordinierterLohn).toBe(62_475);
    expect(betragJahr(e, 'bvg')).toBe(4_685.63);
    expect(e.totalAbzuegeJahr).toBe(19_397.83);
    expect(e.nettoJahr).toBe(180_602.17);
    expect(e.nettoMonat).toBe(15_050.18);
  });

  it('Alter unter 25: kein BVG-Sparbeitrag (22, BE, 60 000, ohne QST)', () => {
    // Hand: AHV 3180.00 · ALV 660.00 · NBU 600.00 · KTG 300.00 · BVG 0
    //       Total = 4740.00 → Netto 55260.00 / Monat 4605.00
    const e = berechneAbzuege(input({ alter: 22, jahresbrutto: 60_000 }));

    expect(betragJahr(e, 'bvg')).toBe(0);
    expect(e.abzuege.find(p => p.key === 'bvg')?.satzProzent).toBe(0);
    expect(e.totalAbzuegeJahr).toBe(4_740);
    expect(e.nettoJahr).toBe(55_260);
    expect(e.nettoMonat).toBe(4_605);
  });

  it('Brutto unter der BVG-Koordination: koordinierter Lohn 0 → BVG 0 (30, ZH, 24 000)', () => {
    // Hand: 24000 − 25725 < 0 → koord = 0 → BVG 0
    //       AHV 1272.00 · ALV 264.00 · NBU 240.00 · KTG 120.00
    //       Total = 1896.00 → Netto 22104.00 / Monat 1842.00
    const e = berechneAbzuege(input({ alter: 30, kanton: 'ZH', jahresbrutto: 24_000 }));

    expect(e.koordinierterLohn).toBe(0);
    expect(betragJahr(e, 'bvg')).toBe(0);
    expect(e.totalAbzuegeJahr).toBe(1_896);
    expect(e.nettoJahr).toBe(22_104);
    expect(e.nettoMonat).toBe(1_842);
  });

  it('Quellensteuer je Kanton auf dem Netto NACH Sozialabzügen (40, 120 000, 100 %)', () => {
    // Hand: QST-Basis = 107396.25 (siehe Standardfall) ×
    //       BE 10% = 10739.63 · ZH 9.5% = 10202.64 (10202.64375) · GE 12% = 12887.55
    //       VD 11.5% = 12350.57 (12350.56875) · BS 11% = 11813.59 (11813.5875)
    const erwartet: Record<string, number> = {
      BE: 10_739.63,
      ZH: 10_202.64,
      GE: 12_887.55,
      VD: 12_350.57,
      BS: 11_813.59,
    };
    for (const kanton of KANTONE) {
      const e = berechneAbzuege(input({ kanton, mitQuellensteuer: true }));
      expect(e.nettoVorQstJahr).toBe(107_396.25);
      expect(betragJahr(e, 'qst'), `QST ${kanton}`).toBe(erwartet[kanton]);
    }
  });

  it('Pensum 50 %: effektives Brutto = Eingabe × 0.5, Ceilings/Koordination auf dem effektiven Brutto', () => {
    // Hand: eff. Brutto = 120000 × 50% = 60000
    //       AHV 3180.00 · ALV 660.00 · NBU 600.00 · KTG 300.00
    //       BVG: koord = 60000 − 25725 = 34275; 10% → AN 5% = 1713.75
    //       Total = 6453.75 → Netto 53546.25 / Monat 4462.19 (4462.1875)
    const e = berechneAbzuege(input({ pensumProzent: 50 }));

    expect(e.bruttoJahr).toBe(60_000);
    expect(e.koordinierterLohn).toBe(34_275);
    expect(betragJahr(e, 'bvg')).toBe(1_713.75);
    expect(e.totalAbzuegeJahr).toBe(6_453.75);
    expect(e.nettoJahr).toBe(53_546.25);
    expect(e.nettoMonat).toBe(4_462.19);
  });

  it('ohne QST erscheint keine qst-Position; mit QST ist sie die letzte Zeile', () => {
    const ohne = berechneAbzuege(input({}));
    expect(ohne.abzuege.map(p => p.key)).toEqual(['ahvIvEo', 'alv', 'nbu', 'ktg', 'bvg']);

    const mit = berechneAbzuege(input({ mitQuellensteuer: true }));
    expect(mit.abzuege.map(p => p.key)).toEqual(['ahvIvEo', 'alv', 'nbu', 'ktg', 'bvg', 'qst']);
  });

  it('Monatswerte sind die durch 12 geteilten, rappengerundeten Jahreswerte', () => {
    const e = berechneAbzuege(input({ mitQuellensteuer: true }));
    for (const p of e.abzuege) {
      expect(p.betragMonat, p.key).toBe(rundeAufRappen(p.betragJahr / 12));
    }
    expect(e.totalAbzuegeMonat).toBe(rundeAufRappen(e.totalAbzuegeJahr / 12));
    // Standardfall konkret: BVG 3123.75 ÷ 12 = 260.3125 → 260.31
    expect(e.abzuege.find(p => p.key === 'bvg')?.betragMonat).toBe(260.31);
  });

  it('defensive Eingaben: nicht-finite/negative Werte zählen als 0, Pensum max. 100 %', () => {
    const leer = berechneAbzuege(input({ jahresbrutto: Number.NaN }));
    expect(leer.bruttoJahr).toBe(0);
    expect(leer.nettoJahr).toBe(0);
    expect(leer.totalAbzuegeJahr).toBe(0);

    const negativ = berechneAbzuege(input({ jahresbrutto: -50_000 }));
    expect(negativ.bruttoJahr).toBe(0);

    const uebervoll = berechneAbzuege(input({ pensumProzent: 150 }));
    expect(uebervoll.bruttoJahr).toBe(120_000); // gekappt auf 100 %
  });
});

describe('altersgutschriftProzent — BVG-Stufen inkl. Grenzwerte', () => {
  it('bildet die Stufen 7/10/15/18 % exakt an den Altersgrenzen ab', () => {
    expect(altersgutschriftProzent(24)).toBe(0);
    expect(altersgutschriftProzent(25)).toBe(7);
    expect(altersgutschriftProzent(34)).toBe(7);
    expect(altersgutschriftProzent(35)).toBe(10);
    expect(altersgutschriftProzent(44)).toBe(10);
    expect(altersgutschriftProzent(45)).toBe(15);
    expect(altersgutschriftProzent(54)).toBe(15);
    expect(altersgutschriftProzent(55)).toBe(18);
    expect(altersgutschriftProzent(70)).toBe(18);
    expect(altersgutschriftProzent(71)).toBe(0);
    expect(altersgutschriftProzent(Number.NaN)).toBe(0);
  });
});

describe('rundeAufRappen', () => {
  it('rundet kaufmännisch auf 2 Nachkommastellen (inkl. x.xx5-Fälle)', () => {
    expect(rundeAufRappen(10_739.625)).toBe(10_739.63);
    expect(rundeAufRappen(4_685.625)).toBe(4_685.63);
    expect(rundeAufRappen(8_054.7183)).toBe(8_054.72);
    expect(rundeAufRappen(100)).toBe(100);
  });
});

describe('SAETZE_2025 — versionierte Richtwerte (ADR-018)', () => {
  it('trägt die Inventur-Sätze 2025', () => {
    expect(SAETZE_2025.jahr).toBe(2025);
    expect(SAETZE_2025.ahvIvEoProzent).toBe(5.3);
    expect(SAETZE_2025.alvProzent).toBe(1.1);
    expect(SAETZE_2025.alvCeilingJahr).toBe(148_200);
    expect(SAETZE_2025.nbuProzent).toBe(1.0);
    expect(SAETZE_2025.ktgProzent).toBe(0.5);
    expect(SAETZE_2025.bvg.obererGrenzbetragJahr).toBe(88_200);
    expect(SAETZE_2025.bvg.koordinationsabzugJahr).toBe(25_725);
    expect(SAETZE_2025.quellensteuerProzent).toEqual({ BE: 10, ZH: 9.5, GE: 12, VD: 11.5, BS: 11 });
  });
});
