import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { DOCUMENT } from '@angular/common';
import { ObMasterLayoutModule } from '@oblique/oblique';
import { RuntimeConfigService } from './core/runtime-config/runtime-config.service';

// ObMasterLayoutModule is imported here because ob-master-layout has standalone:false
// (Oblique 15.3 master-layout is not yet standalone-API-friendly — same root cause
// as schematic-rejection in FT3). Angular's standalone components support NgModule
// imports directly in the @Component imports array. Refs: BDR-008, ADR-007.
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ObMasterLayoutModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  private readonly translate = inject(TranslateService);
  private readonly config = inject(RuntimeConfigService);
  private readonly document = inject(DOCUMENT);

  ngOnInit(): void {
    const supported = this.config.get().i18n.supportedLocales;
    const fallback = this.config.get().i18n.defaultLocale;
    this.translate.addLangs([...supported]);
    this.translate.setDefaultLang(fallback);
    const browser = (this.translate.getBrowserLang() ?? fallback) as 'de'|'fr'|'it'|'en';
    const chosen = supported.includes(browser) ? browser : fallback;
    this.translate.use(chosen);
    this.document.documentElement.setAttribute('lang', chosen);
  }
}
