import { HttpClient } from '@angular/common/http';
import { TranslateLoader } from '@ngx-translate/core';

import { MultiTranslateHttpLoader } from './multi-translate-http-loader';

/**
 * ngx-translate loader provider that pulls BOTH our own and Oblique's locale
 * files for the current language and merges them. Without this, Oblique
 * keys like `i18n.oblique.accessibility-statement.link` show up as raw
 * strings in the UI because the default single-file loader only loads
 * `assets/i18n/<lang>.json` and ignores the sibling
 * `assets/i18n/oblique-<lang>.json`.
 */
export const translateHttpLoaderFactory = () => [
  {
    provide: TranslateLoader,
    useFactory: (http: HttpClient) =>
      new MultiTranslateHttpLoader(http, [
        { prefix: 'assets/i18n/', suffix: '.json' },
        { prefix: 'assets/i18n/oblique-', suffix: '.json' }
      ]),
    deps: [HttpClient]
  }
];
