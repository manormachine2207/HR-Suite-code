import { HttpClient } from '@angular/common/http';
import { TranslateLoader } from '@ngx-translate/core';
import { Observable, forkJoin, map } from 'rxjs';

/**
 * Loads multiple JSON locale files per language and merges them into a single
 * translation object. Used to combine our own `assets/i18n/<lang>.json` with
 * Oblique's `assets/i18n/oblique-<lang>.json` so keys like
 * `i18n.oblique.accessibility-statement.link` resolve.
 *
 * Oblique 15 ships locale files with FLAT, dotted keys
 * (`{ "i18n.common.yes": "Ja", ... }`) — `Object.assign`-style shallow merge
 * is the correct strategy.
 */
export class MultiTranslateHttpLoader implements TranslateLoader {
  constructor(
    private readonly http: HttpClient,
    private readonly resources: ReadonlyArray<{ prefix: string; suffix: string }>
  ) {}

  getTranslation(lang: string): Observable<Record<string, string>> {
    return forkJoin(
      this.resources.map(r =>
        this.http.get<Record<string, string>>(`${r.prefix}${lang}${r.suffix}`)
      )
    ).pipe(
      map(parts =>
        parts.reduce<Record<string, string>>(
          (merged, current) => ({ ...merged, ...current }),
          {}
        )
      )
    );
  }
}
