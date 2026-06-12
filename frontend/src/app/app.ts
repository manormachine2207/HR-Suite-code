import { Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DOCUMENT } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { ObMasterLayoutModule, ObINavigationLink } from '@oblique/oblique';
import { RuntimeConfigService } from './core/runtime-config/runtime-config.service';

// ObMasterLayoutModule is imported here because ob-master-layout has standalone:false
// (Oblique 15.3 master-layout is not yet standalone-API-friendly — same root cause
// as schematic-rejection in FT3). Angular's standalone components support NgModule
// imports directly in the @Component imports array. Refs: BDR-008, ADR-007.
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, MatIconModule, ObMasterLayoutModule, TranslateModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  private readonly translate = inject(TranslateService);
  private readonly config = inject(RuntimeConfigService);
  private readonly document = inject(DOCUMENT);

  /** Top navigation (Oblique master layout translates the label keys itself). */
  readonly navigation: ObINavigationLink[] = [
    { label: 'nav.antragstypen', url: 'antragstypen' },
    { label: 'nav.antraege', url: 'antraege' },
    { label: 'nav.aufgaben', url: 'aufgaben' },
  ];

  /**
   * Release version for the header badge, from runtime.json (12-Factor; the
   * APP_INITIALIZER guarantees the config is loaded before this component exists).
   */
  protected readonly version = this.config.get().release.version;

  ngOnInit(): void {
    const supported = this.config.get().i18n.supportedLocales;
    const fallback = this.config.get().i18n.defaultLocale;
    this.translate.addLangs([...supported]);
    this.translate.setDefaultLang(fallback);
    const browser = (this.translate.getBrowserLang() ?? fallback) as 'de'|'fr'|'it'|'en';
    const chosen = supported.includes(browser) ? browser : fallback;
    this.translate.use(chosen);
    this.document.documentElement.setAttribute('lang', chosen);
    // Keep <html lang> in sync when the user switches the language (a11y + e.g. hyphenation).
    this.translate.onLangChange.subscribe(e =>
      this.document.documentElement.setAttribute('lang', e.lang));
  }
}
