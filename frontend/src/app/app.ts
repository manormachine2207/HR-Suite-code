import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { DOCUMENT } from '@angular/common';
import { RuntimeConfigService } from './core/runtime-config/runtime-config.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  protected readonly title = signal('hr-suite-frontend');
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
