import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

import { BreadcrumbComponent } from '../../shared/breadcrumb/breadcrumb.component';
import { MandantenComponent } from './mandanten/mandanten.component';

/** A module-internal tab. `enabled:false` = stage placeholder ("in Vorbereitung"). */
interface PlattformTab {
  readonly id: 'mandanten' | 'sso' | 'smtp' | 'logging';
  readonly labelKey: string;
  readonly enabled: boolean;
}

/**
 * Plattform module shell (ADR-019). First real use of MODULE-INTERNAL tabs
 * (ADR-014 navigation precision: tabs belong inside a module, never on the
 * dashboard). Stufe 1 ships the Mandanten tab; SSO/SMTP/Logging are visible but
 * disabled placeholders so the target structure is legible.
 */
@Component({
  selector: 'app-plattform',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslateModule, BreadcrumbComponent, MandantenComponent],
  templateUrl: './plattform.component.html',
  styleUrl: './plattform.component.scss',
})
export class PlattformComponent {
  readonly tabs: readonly PlattformTab[] = [
    { id: 'mandanten', labelKey: 'plattform.tab.mandanten', enabled: true },
    { id: 'sso', labelKey: 'plattform.tab.sso', enabled: false },
    { id: 'smtp', labelKey: 'plattform.tab.smtp', enabled: false },
    { id: 'logging', labelKey: 'plattform.tab.logging', enabled: false },
  ];

  readonly active = signal<PlattformTab['id']>('mandanten');

  select(tab: PlattformTab): void {
    if (tab.enabled) {
      this.active.set(tab.id);
    }
  }
}
