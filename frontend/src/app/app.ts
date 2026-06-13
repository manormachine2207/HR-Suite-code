import { Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DOCUMENT } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { ObMasterLayoutConfig, ObMasterLayoutModule } from '@oblique/oblique';
import { RuntimeConfigService } from './core/runtime-config/runtime-config.service';
import { DevRoleSwitcherComponent } from './core/auth/dev-role-switcher.component';

// ObMasterLayoutModule is imported here because ob-master-layout has standalone:false
// (Oblique 15.3 master-layout is not yet standalone-API-friendly — same root cause
// as schematic-rejection in FT3). Angular's standalone components support NgModule
// imports directly in the @Component imports array. Refs: BDR-008, ADR-007.
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, MatIconModule, ObMasterLayoutModule, TranslateModule, DevRoleSwitcherComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  private readonly translate = inject(TranslateService);
  private readonly config = inject(RuntimeConfigService);
  private readonly document = inject(DOCUMENT);

  // No global top-tab bar: it duplicated the dashboard tiles and looked unfinished
  // (ADR-014 navigation precision, Owner 2026-06-12). The Dashboard ('/') is the
  // sole entry hub, the tiles ARE the navigation; tabs only ever appear as
  // sub-navigation INSIDE a module. Back to the hub is via the federal-logo brand
  // link + the per-page <app-breadcrumb> "Dashboard › Modul".
  //
  // Point the Oblique master-layout brand link at the dashboard. The default is
  // '/home'; that route still exists and redirects to '/', but setting it here makes
  // the logo target the hub directly (no redirect hop). ObMasterLayoutConfig is a
  // root singleton with a mutable homePageRoute; set it before the layout renders.
  constructor() {
    inject(ObMasterLayoutConfig).homePageRoute = '/';
  }

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
